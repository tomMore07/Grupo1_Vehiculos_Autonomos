<div class="portada">
<div class="sub">UNIVERSIDAD SERGIO ARBOLEDA · ESCUELA DE CIENCIAS EXACTAS E INGENIERÍA · ICCIA</div>

# Informe Técnico Final de Ingeniería

<div class="sub">Proyecto Final Integrador · Paradigmas de Programación (SIST0176)</div>

**Grupo 1:** Detección de anomalías e inferencia en tiempo real para vehículos autónomos<br>
**Integrantes:** Sara Valentina Díaz Rojas · Tomás Felipe Morera Pérez · Cristhian Santiago Pinilla Vega<br>
**Fecha:** noviembre de 2026
</div>

## 1. Resumen
Se construyó una plataforma a bordo de un vehículo autónomo que filtra nubes de puntos LIDAR, detecta anomalías en la telemetría y decide un frenado de emergencia. El sistema integra tres módulos, cada uno en el paradigma que mejor explota su tarea: **C++17 con OpenMP** (imperativo), **Java 17** (orientado a objetos) y **Python 3.10** (funcional). Los módulos son procesos independientes que se comunican por JSON; Java los consume a través del patrón Adapter.

Este informe recoge la versión final tras la revisión del avance parcial: se simplificó el diseño, se paralelizó la etapa dominante del kernel, se eliminaron las estructuras mutables del módulo funcional y se corrigió el aliasing del historial en Java.

## 2. Ajustes realizados tras la retroalimentación
| Observación | Cambio realizado | Dónde verlo |
|---|---|---|
| Reducir las clases no esenciales | El módulo Java pasó de 17 a **13 clases**. Se eliminaron `EstadoVehiculo`, `ResultadoFrenado`, `JsonPlano` y `PercepcionException`. | `java/src/`, `docs/01_arquitectura_uml.pdf` |
| El diagrama de flujo de datos era muy básico | Se rehízo como **DFD formal (Yourdon–DeMarco)** con entidades externas, procesos numerados, almacenes de datos, flujos rotulados, niveles 0 y 1 y diccionario de datos. Se agregó un diagrama de secuencia. Todo en Mermaid. | `docs/02_arquitectura_uml.md` §5–6 |
| Python usaba `deque.append` (estructura mutable) | Las ventanas se definen como un *scan* puro con `itertools.accumulate`: cada ventana es una tupla nueva, sin mutación. Una prueba verifica que el núcleo no contenga estructuras mutables. | `python/pipeline.py`, `test_pipeline.py` |
| `std::sort` secuencial limitaba el speedup (Amdahl ≈ 1.2×) | Ordenamiento **por bloques en paralelo + fusión**, y compactación paralela con suma de prefijos. Resultado idéntico al secuencial con 1, 2, 3, 4 y 8 hilos. | `cpp/lidar_kernel.cpp` (`sort_keys`, etapa 2) |
| `unmodifiableList` entrega una vista "en vivo" (riesgo de `ConcurrentModificationException`) | `getHistorial()` y `getBitacora()` devuelven **`List.copyOf(...)`**, una instantánea inmutable. Pruebas del fallo original y de la corrección. | `MotorDecision.java`, `ActuadorFrenado.java`, `Pruebas.java` |

## 3. Arquitectura de dominio
El detalle (diagrama de clases UML, DFD, patrones y SOLID) está en `docs/01_arquitectura_uml.pdf`. En resumen:

- **Template Method:** `ActuadorFrenado.ejecutarSecuenciaFrenado()` es `final` y fija el orden verificar → aplicar → registrar; `FrenoHidraulico` y `FrenoRegenerativo` aportan los pasos variables.
- **Strategy:** `EstrategiaEvaluacionRiesgo` con `RiesgoPorDistancia` y `RiesgoPorTiempoColision`, intercambiables en ejecución.
- **Adapter / DIP:** `AdaptadorKernelCpp` implementa `ProveedorPercepcion`; `MotorDecision` nunca depende de C++ ni de Python.
- **Encapsulamiento:** estado privado, `record` inmutables con invariantes validados, sin fuga de referencias (instantáneas con `List.copyOf`) y métodos `synchronized` para uso concurrente.

## 4. Implementación por paradigma

### 4.1 Imperativo: kernel C++ (`cpp/`)
La nube de puntos se almacena como **Structure of Arrays** (`x[]`, `y[]`, `z[]`, `intensidad[]`) en memoria contigua: cada bucle recorre solo los campos que necesita y aprovecha la caché. El kernel tiene siete etapas:

| # | Etapa | Paralelización |
|---|---|---|
| 1 | Máscara de suelo y alcance | `#pragma omp parallel for` |
| 2 | Compactación de índices | Cada hilo cuenta en su tramo; un solo hilo calcula los desplazamientos (suma de prefijos, `omp single`); cada hilo escribe en su zona. Orden idéntico al secuencial. |
| 3 | Clave de vóxel de 63 bits (21 bits por eje) | `parallel for` |
| 4 | Ordenamiento por (clave, índice) | Bloques ordenados en paralelo con `std::sort` + fusión por rondas con `std::inplace_merge` |
| 5 | Segmentación en vóxeles y filtro de ruido | Secuencial (≈ 2 % del tiempo) |
| 6 | Centroides por vóxel | `parallel for` (cada hilo escribe celdas distintas) |
| 7 | Obstáculo más cercano en el corredor | `parallel for` con `reduction(min:best)` |

**Memoria sin inicializar:** los arreglos grandes (`mask`, `kept`, `keys`) se reservan con `unique_ptr<T[]>(new T[n])`, sin rellenarlos de ceros. Un `std::vector` los rellena de forma secuencial (solo `keys` cuesta ≈ 6.6 ms en un solo hilo, medido con una prueba aislada), tiempo que no se puede paralelizar; sin relleno, la primera escritura ocurre dentro del bucle paralelo y cada hilo "toca" sus propias páginas.

**Ausencia de condiciones de carrera:** cada iteración o cada hilo escribe en posiciones exclusivas (`mask[i]`, `keys[k]`, `filtered.x[j]`, `kept[pos++]` con `pos` privado por hilo y rangos disjuntos); el único valor compartido, el mínimo, usa `reduction`. En las fusiones, cada par de bloques es un rango disjunto.

**Determinismo:** como cada elemento ordenado es único (la clave se desempata por índice), cualquier algoritmo de ordenamiento produce la misma permutación. El programa compara bit a bit el resultado secuencial y el paralelo y devuelve error si difieren (`make test-cpp`).

### 4.2 Orientado a objetos: motor de decisión (`java/`)
13 clases en 5 paquetes (`dominio`, `riesgo`, `actuadores`, `percepcion`, `decision`) más la clase `Main` de demostración. Puntos clave:

- **Invariantes protegidos:** un `Obstaculo` con distancia negativa no puede existir; el constructor compacto del `record` lo rechaza.
- **Instantáneas en lugar de vistas:** `getHistorial()` devuelve `List.copyOf(historial)`. Con `Collections.unmodifiableList` el receptor vería los cambios posteriores del motor y podría recibir `ConcurrentModificationException` al recorrer la lista; con la copia, la lista recibida es estática. Cuesta O(E) por llamada, a cambio de seguridad.
- **Concurrencia:** los métodos públicos de `MotorDecision` y `ActuadorFrenado` son `synchronized`; los campos que escribe un hilo y lee otro (`presionLineaBar`, `torqueNm`) son `volatile`.

### 4.3 Funcional: pipeline Python (`python/pipeline.py`)
- **Sin estructuras mutables.** Las ventanas deslizantes se definen como un *scan*: `accumulate(stream, lambda w, x: (w + (x,))[-size:], initial=())`. Cada ventana es una tupla nueva calculada a partir de la anterior; nada se modifica.
- **Funciones de orden superior:** `map`, `filter` y `reduce` (media y varianza con dos `reduce`).
- **Evaluación perezosa:** todo opera sobre iteradores; funciona sobre el generador infinito `source_stream`.
- **Currificación:** el decorador `@curry` permite `detect_anomalies(4.0)(30)(flujo)`.
- **Functional Core, Imperative Shell:** el núcleo (`mean_var`, `sliding_windows`, `moving_average`, `detect_anomalies`) es puro; los efectos (azar en `source_stream`, impresión en `main`) quedan en la cáscara.

| Función | ¿Pura? | Efecto aislado |
|---|---|---|
| `mean_var`, `sliding_windows`, `moving_average`, `detect_anomalies`, `anomaly_to_json` | Sí | — |
| `source_stream` | No | Generador de números aleatorios (cáscara) |
| `main` | No | Lectura de argumentos e impresión (cáscara) |

## 5. Análisis de complejidad (Big O)

### 5.1 Definiciones y modelo de costo
**Definición.** f(N) ∈ O(g(N)) ⇔ ∃ c > 0, ∃ N₀ tales que ∀ N ≥ N₀: f(N) ≤ c·g(N). f ∈ Θ(g) ⇔ f ∈ O(g) y g ∈ O(f).

**Notación.** N = puntos de entrada; M ≤ N = puntos que sobreviven al filtro de suelo y alcance; V = vóxeles válidos (V ≤ M/3 porque `min_pts_voxel = 3`); p = número de hilos (constante). Operaciones aritméticas, comparaciones y accesos a `std::vector` cuestan Θ(1). Se cuenta el peor caso salvo que se indique lo contrario.

### 5.2 Tiempo secuencial
| Etapa | Costo | Justificación |
|---|---|---|
| 1 Máscara | k₁·N | Un bucle de cuerpo constante sobre N puntos. |
| 2 Compactación | k₂·N | Dos pasadas sobre N (contar y escribir). |
| 3 Claves | k₃·M | Un bucle sobre los M puntos conservados. |
| 4 Ordenamiento | ≤ k₄·M·log₂M | La norma C++11 exige O(M log M) comparaciones en el peor caso (introsort). |
| 5 Segmentación | ≤ k₅·M | El bucle interno avanza `e` y el externo asigna `s = e`: cada posición se visita una vez. |
| 6 Centroides | ≤ k₆·M + k₆′·V | Los rangos de vóxeles son disjuntos: Σ(fin − inicio) ≤ M. |
| 7 Mínimo | k₇·V | Un bucle sobre V centroides. |

**Teorema 1.** T(N) ∈ O(N log N).

*Demostración.* Sumando las etapas, T(N) ≤ (k₁+k₂)·N + (k₃+k₅+k₆)·M + (k₆′+k₇)·V + k₄·M·log₂M. Para N ≥ 2: (i) V ≤ M ≤ N; (ii) log₂N ≥ 1, luego N ≤ N·log₂N; (iii) h(x) = x·log₂x es creciente para x ≥ 1 y M ≤ N, luego M·log₂M ≤ N·log₂N. Aplicando (i)–(iii) a cada término, T(N) ≤ c·N·log₂N con c = k₁+k₂+k₃+k₄+k₅+k₆+k₆′+k₇ y N₀ = 2. ∎

**Proposición 2 (cota ajustada).** Si M ≥ α·N con α ∈ (0,1], entonces T(N) ∈ Θ(N log N).

*Demostración.* La cota superior es el Teorema 1. Para la inferior, ordenar M claves distintas requiere Ω(M log M) comparaciones en el peor caso, y M·log₂M ≥ αN·(log₂N + log₂α) ≥ (α/2)·N·log₂N siempre que log₂N ≥ 2·log₂(1/α), es decir N ≥ 1/α². ∎ (En los datos sintéticos M/N ≈ 0.55, luego N₀ ≈ 4.)

### 5.3 Espacio
| Estructura | Bytes | Cota |
|---|---|---|
| `mask` (`uint8_t`) | N | Θ(N) |
| `kept` (`uint32_t`) | 4·M | Θ(M) |
| `keys` (`pair<uint64_t,uint32_t>`, 16 B con relleno) | 16·M | Θ(M) |
| Búfer temporal de `inplace_merge` (modo paralelo) | ≤ 8·M | O(M) |
| `runs` (dos `int64_t`) | ≤ 16·V | O(V) |
| Salida (4 arreglos `float` de V) | 16·V | Θ(V) |
| Contadores por hilo | O(p) | O(1) |

**Teorema 3.** El espacio adicional es S(N) ∈ Θ(N).

*Demostración.* S(N) ≤ N + 4M + 16M + 8M + 32V + O(p). Con V ≤ M/3 ≤ N/3: S(N) ≤ N + 28N + 32N/3 + O(p) < 40·N + O(p), luego S ∈ O(N). Además S(N) ≥ N por `mask`, luego S ∈ Ω(N). ∎

### 5.4 Tiempo paralelo (trabajo y camino crítico)
El ordenamiento paralelo divide las M claves en p bloques, ordena cada uno con `std::sort` y los fusiona en log₂p rondas.

**Teorema 4.** Con p hilos, el trabajo total sigue siendo O(N log N) y el tiempo ideal es Tₚ(N) = O( (N log N)/p + N ).

*Demostración.* *Trabajo:* ordenar los p bloques cuesta p·(M/p)·log₂(M/p) = M·log₂(M/p); las log₂p rondas de fusión cuestan M cada una, es decir M·log₂p. La suma es M·log₂M: no hay trabajo asintótico extra. *Camino crítico:* las etapas 1, 2, 3, 6 y 7 se reparten entre p hilos (O(N/p)); los bloques se ordenan en paralelo en O((M/p)·log(M/p)); en la ronda r hay p/2ʳ fusiones independientes, cada una de tamaño 2ʳ·M/p y secuencial, así que la ronda cuesta O(2ʳ·M/p), y Σ(r = 1 … log₂p) 2ʳ·M/p = (2p − 2)·M/p < 2M = O(N). La segmentación (etapa 5) es O(M). Sumando se obtiene Tₚ = O(N/p + (N/p)·log N + N). ∎

**Corolario (speedup asintótico).** Para p fijo, Sₚ = T₁/Tₚ ≥ c₁·N·log N / (c₂·N·log N / p + c₃·N) = c₁ / (c₂/p + c₃/log N) → p·c₁/c₂ cuando N → ∞. La parte que sigue siendo secuencial en esencia (la última fusión, Θ(N)) pesa una fracción O(1/log N) del trabajo, por lo que **la eficiencia mejora al crecer N**. Para N = 2·10⁶ (log₂N ≈ 21) esa fracción aún es apreciable, y de ahí el techo práctico que se estima en §6.

**Comparación con el diseño anterior.** Con `std::sort` secuencial, la fracción secuencial era f ≈ 0.81 y la ley de Amdahl acotaba el speedup a 1/f ≈ 1.24× sin importar p. Con el ordenamiento por bloques, la parte secuencial ya no es el ordenamiento completo sino la fusión final (≈ 11 % del tiempo secuencial) más la segmentación (≈ 2 %).

### 5.5 Pipeline funcional y módulo Java
Sea W el tamaño de ventana y N el número de lecturas.

| Función | Tiempo | Espacio |
|---|---|---|
| `mean_var` | Θ(W) | O(1) |
| `sliding_windows`, `moving_average`, `detect_anomalies` | Θ(N·W) | O(W) |

Cada ventana nueva `(w + (x,))[-size:]` cuesta Θ(W) (dos tuplas de tamaño ≈ W). Por la evaluación perezosa el espacio no depende de N, lo que permite el flujo infinito. En Java: `evaluarYActuar` es O(1) amortizado; `getHistorial()` es **O(E)** (copia de E eventos), el costo de la instantánea inmutable; `AdaptadorKernelCpp.leerCampo` es O(L) en la longitud L de la línea.

## 6. Benchmarks de rendimiento

### 6.1 Metodología
Medición con `make benchmark` (`scripts/benchmark.sh`): N = 2·10⁶ puntos, nube sintética con semilla fija, hilos ∈ {1, 2, 4, 8}, compilación con `g++ -O3 -fopenmp`. Para que la comparación sea justa, cada ejecución del programa hace **(1) una corrida de calentamiento de cada modo, descartada, y (2) tres repeticiones alternadas secuencial/paralelo, tomando el mínimo de cada modo**; el script repite la ejecución 3 veces y conserva el mínimo. Cada corrida verifica que el resultado paralelo sea idéntico al secuencial. Speedup = tiempo secuencial / tiempo paralelo; eficiencia = speedup / hilos.

**Lección sobre el método (sesgo detectado).** Una primera versión medía una sola corrida de cada modo, la secuencial primero. Al probar con **1 hilo** el modo "paralelo" salió 1.32× más rápido que el secuencial, lo cual es imposible: la segunda corrida reutilizaba la memoria que la primera ya había reservado y se ahorraba los fallos de página. Con el calentamiento y las repeticiones, la prueba con 1 hilo da 1.00×, que es lo esperado. Toda medición debe pasar esa prueba de cordura (1 hilo ⇒ speedup ≈ 1) antes de reportarse.

### 6.2 Línea base (diseño anterior, `std::sort` secuencial) — medido por el grupo
N = 2·10⁶, 4 hilos, en el computador del grupo:

| Métrica | Valor |
|---|---|
| Speedup real | **1.06×** |
| Peso del ordenamiento | 70.3 % del tiempo |
| Fracción secuencial f | 0.807 |
| Cota de Amdahl (p = 4 / p → ∞) | 1.17× / 1.24× |

### 6.2b Primera medición con el ordenamiento paralelo (computador del grupo, 4 hilos)
Medición con la versión intermedia, **antes de corregir el método** (una sola corrida por modo, sin calentamiento). Se conserva porque muestra qué etapas aceleran y cuáles no:

| Etapa | Secuencial (ms) | Paralelo (ms) | Speedup |
|---|---:|---:|---:|
| 1 Máscara | 13.47 | 19.21 | 0.70× |
| 2 Compactación | 12.23 | 5.96 | 2.05× |
| 3 Claves de vóxel | 14.90 | 17.91 | 0.83× |
| 4 Ordenamiento (bloques 29.50 + fusión 16.27) | 110.94 | 45.78 | 2.42× |
| 5 Segmentación | 3.68 | 3.55 | 1.04× |
| 6 Centroides | 2.94 | 2.02 | 1.46× |
| 7 Mínimo | 0.02 | 0.01 | 1.37× |
| **Total** | **158.18** | **94.43** | **1.68×** |

Lectura: (1) el ordenamiento, que antes era el techo (1.06× en total), ahora se acelera 2.42× y su tiempo paralelo (45.78 ms) **coincide con la estimación del modelo** de §6.5 (≈ 45.7 ms: 25.6 ms de bloques + 20.1 ms de fusión crítica); la teoría del Teorema 4 se confirma. (2) El modelo predijo 63.9 ms en total y se midieron 94.4 ms: la diferencia está casi toda en las etapas 1 y 3, que **no aceleraron**. Dos causas identificadas: el relleno secuencial con ceros de los arreglos grandes y la creación de los hilos de OpenMP dentro de la primera región paralela; ambas se corrigieron (memoria sin inicializar y calentamiento). (3) Este 1.68× pudo estar inflado por el sesgo descrito en §6.1, por lo que **no debe citarse como resultado final**: la medición definitiva es la de §6.6.

### 6.3 Crecimiento N log N (versión secuencial)
Mínimo de 3 ejecuciones, 1 hilo. Si T ≈ c·N·log₂N el cociente T/(N·log₂N) debe ser casi constante.

| N | T (s) | T/(N·log₂N) (ns) | T/N (ns/punto) |
|---:|---:|---:|---:|
| 250 000 | 0.0175 | 3.90 | 70.0 |
| 500 000 | 0.0331 | 3.50 | 66.2 |
| 1 000 000 | 0.0734 | 3.68 | 73.4 |
| 2 000 000 | 0.1458 | 3.48 | 72.9 |
| 4 000 000 | 0.3202 | 3.65 | 80.0 |

El cociente se mantiene entre 3.5 y 3.9 ns (variación ≈ 12 %) mientras N crece 16 veces; el tiempo por punto pasa de 70 a 80 ns, coherente con N log N y no con N² (que lo habría multiplicado por 16). *Nota:* estas mediciones provienen de un entorno compartido de un solo núcleo, con variación de ±20 % entre ejecuciones; se usan para verificar la forma de la curva, no valores absolutos.

### 6.4 Tiempo por etapa tras la paralelización
Ejecución con `--threads 4 --stages` en un entorno de **un solo núcleo** (los hilos se ejecutan en serie, así que no puede haber speedup; la tabla sirve para ver la distribución del costo):

| Etapa | Secuencial (ms) | % del total | Paralelo, 4 hilos en 1 núcleo (ms) |
|---|---:|---:|---:|
| 1 Máscara | 16.59 | 9.0 | 13.85 |
| 2 Compactación | 15.55 | 8.4 | 12.04 |
| 3 Claves de vóxel | 21.83 | 11.8 | 17.18 |
| 4 Ordenamiento | 124.21 | 67.3 | 129.18 (bloques 102.44 + fusión 26.74) |
| 5 Segmentación | 4.10 | 2.2 | 3.03 |
| 6 Centroides | 2.35 | 1.3 | 3.40 |
| 7 Mínimo | 0.02 | 0.0 | 0.04 |
| **Total** | **184.64** | 100 | 178.73 |

El resultado paralelo coincidió con el secuencial en todas las ejecuciones (1, 2, 3, 4 y 8 hilos).

### 6.5 Estimación por modelo (no es una medición)
Con los tiempos de §6.4, suponiendo que las etapas paralelas escalan linealmente con p, que cada ronda de fusión cuesta lo mismo (≈ fusión total / nº de rondas) y que no hay límite de ancho de banda de memoria:

| p | Tiempo paralelo estimado (ms) | Speedup estimado | Parte secuencial (fusión crítica + segmentación) |
|---:|---:|---:|---|
| 4 | 63.9 | ≈ 2.9× | 20.1 + 4.1 ms |
| 8 | 40.3 | ≈ 4.6× | 18.1 + 4.1 ms |

Frente al techo de ≈ 1.24× del diseño anterior, el modelo predice una mejora de unas 2.3 a 3.7 veces. El speedup real será menor por el ancho de banda de memoria y por la sobrecarga de los hilos; **debe confirmarse con `make benchmark` en hardware multinúcleo**.

### 6.6 Resultados medidos en hardware multinúcleo (completar)
Ejecute `make benchmark` en un computador con ≥ 4 núcleos físicos (con `nproc` ≥ 4 dentro de WSL) y pegue aquí la tabla que imprime, **con la versión final del código**. Verifique que la fila de 1 hilo dé ≈ 1.00× (prueba de cordura). Indique modelo de CPU, número de núcleos y sistema operativo.

| Hilos | Secuencial (s) | Paralelo (s) | Speedup | Eficiencia |
|---:|---:|---:|---:|---:|
| 1 | ___ | ___ | ___ | ___ |
| 2 | ___ | ___ | ___ | ___ |
| 4 | ___ | ___ | ___ | ___ |
| 8 | ___ | ___ | ___ | ___ |

## 7. Control de side effects y thread safety
| Módulo | Mecanismo |
|---|---|
| Python | Functional Core / Imperative Shell: núcleo puro sin estructuras mutables; efectos solo en `source_stream` y `main`. |
| C++ | Escrituras en posiciones exclusivas por hilo; `reduction(min:best)`; `pos` privado en la compactación; resultado idéntico al secuencial verificado en cada ejecución. |
| Java | `record` inmutables; estado privado; instantáneas `List.copyOf`; `synchronized` en `MotorDecision` y `ActuadorFrenado`; `volatile` en campos leídos entre hilos. |

## 8. Pruebas
| Módulo | Pruebas | Qué verifican |
|---|---:|---|
| C++ | 5 | Resultado paralelo idéntico al secuencial con 1, 2, 3, 4 y 8 hilos (`make test-cpp`). |
| Python | 11 | Pureza, no mutación de entradas, ventanas como tuplas, flujo infinito perezoso, ausencia de estructuras mutables en el núcleo, contrato JSON. |
| Java | 26 | Inmutabilidad y aliasing (8): campos `final`, invariantes, historial inmutable, **instantánea estática**, recorrido sin `ConcurrentModificationException`, demostración del fallo de la vista, bitácora, dependencias nulas. Template Method (4). LSP (2). Strategy y motor (5). **Thread safety (2):** 8 hilos × 250 eventos y 8 hilos × 100 frenados. Contratos JSON (3). Integración real C++ + Python + Java (2). |

Se ejecutan con `make test`.

## 9. Conclusiones, limitaciones y trabajo futuro
- El diseño final cumple con los tres paradigmas y con un acoplamiento mínimo: cada módulo se prueba solo, y Java depende únicamente de la interfaz `ProveedorPercepcion`.
- **El hallazgo central de ingeniería** fue medir antes de optimizar: la instrumentación por etapas mostró que el ordenamiento ocupaba ≈ 70 % del tiempo y limitaba el speedup a ≈ 1.2×; la paralelización de esa etapa es la mejora de mayor impacto.
- **Limitación:** la última ronda de fusión sigue siendo secuencial (Θ(N)). Una fusión paralela por co-rangos (dividir los dos bloques con búsqueda binaria para que varios hilos fusionen en paralelo) eliminaría ese tramo; se descartó para mantener la complejidad del código acorde con un proyecto de curso.
- **Limitación:** `getHistorial()` copia el historial en cada llamada (O(E)); es aceptable con pocos eventos, y para historiales muy grandes convendría un historial acotado.
- **Limitación:** la comunicación por procesos externos añade latencia por llamada; JNI sería más rápido a costa de complejidad. Gracias al patrón Adapter, solo cambiaría `AdaptadorKernelCpp`.
- **Pendiente de validar:** los resultados de §6.6 en hardware multinúcleo.

## 10. Reproducibilidad
```bash
make                  # compila C++ y Java
make test             # 5 verificaciones de C++, 11 pruebas Python, 26 pruebas Java
make run-integracion  # ciclo completo C++ + Python + Java
make benchmark        # tabla de speedup (secuencial vs paralelo; 1, 2, 4, 8 hilos)
./cpp/lidar_kernel --n 2000000 --threads 4 --stages   # tiempo por etapa
```
Requisitos: g++ con OpenMP, JDK 17+, Python ≥ 3.10; en Windows, WSL.
