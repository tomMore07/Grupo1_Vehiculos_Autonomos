# Informe técnico final de ingeniería

**Universidad Sergio Arboleda · Escuela de Ciencias Exactas e Ingeniería**  
**Programa de Ingeniería en Ciencias de la Computación e Inteligencia Artificial**  
**Paradigmas de Programación (SIST0176) · Proyecto Final Integrador**  
**Grupo 1 · Detección de anomalías e inferencia en tiempo real para vehículos autónomos**  
**Integrantes** · Sara Valentina Díaz Rojas, Tomás Felipe Morera Pérez y Cristhian Santiago Pinilla Vega  
**Fecha** · 18 de Noviembre de 2026

## 1. Resumen e introducción

El Grupo 1 desarrolló un prototipo multiparadigma que procesa nubes de puntos LiDAR, detecta anomalías en flujos de telemetría y simula decisiones de frenado de emergencia. El objetivo fue integrar tres componentes con responsabilidades distintas y evaluar el costo temporal y espacial de sus algoritmos. El procesamiento geoespacial se implementó en C++17 con OpenMP, la detección de anomalías en Python y la lógica de decisión en Java 17.

La validación incluye la ejecución integrada y pruebas de los módulos. El análisis de rendimiento usa nubes sintéticas y compara el kernel secuencial con su versión paralela.

## 2. Arquitectura de dominio
Java inicia los procesos de C++ y Python y lee sus resultados en JSON. El kernel entrega la distancia al obstáculo más cercano en el corredor y el pipeline aporta los eventos de anomalía. El motor de decisión utiliza la percepción mediante una interfaz y coordina la secuencia de frenado simulada.

Al guardar este informe en `docs/` del repositorio, se pueden consultar el [diagrama UML] y los [diagramas de flujo de datos y de secuencial].

- **Template Method:** `ActuadorFrenado.ejecutarSecuenciaFrenado()` es `final` y fija el orden verificar → aplicar → registrar; `FrenoHidraulico` y `FrenoRegenerativo` aportan los pasos variables.
- **Strategy:** `EstrategiaEvaluacionRiesgo` con `RiesgoPorDistancia` y `RiesgoPorTiempoColision`, intercambiables en ejecución.
- **Adapter / DIP:** `AdaptadorKernelCpp` implementa `ProveedorPercepcion`; `MotorDecision` nunca depende de C++ ni de Python.
- **Encapsulamiento:** estado privado, `record` inmutables con invariantes validados, sin fuga de referencias (instantáneas con `List.copyOf`) y métodos `synchronized` para uso concurrente.

## 3. Implementación por paradigma

### 3.1 Imperativo: kernel C++ (`cpp/`)
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

### 3.2 Orientado a objetos: motor de decisión (`java/`)
13 clases en 5 paquetes (`dominio`, `riesgo`, `actuadores`, `percepcion`, `decision`) más la clase `Main` de demostración. Puntos clave:

- **Invariantes protegidos:** un `Obstaculo` con distancia negativa no puede existir; el constructor compacto del `record` lo rechaza.
- **Instantáneas en lugar de vistas:** `getHistorial()` devuelve `List.copyOf(historial)`. Con `Collections.unmodifiableList` el receptor vería los cambios posteriores del motor y podría recibir `ConcurrentModificationException` al recorrer la lista; con la copia, la lista recibida es estática. Cuesta O(E) por llamada, a cambio de seguridad.
- **Concurrencia:** los métodos públicos de `MotorDecision` y `ActuadorFrenado` son `synchronized`; los campos que escribe un hilo y lee otro (`presionLineaBar`, `torqueNm`) son `volatile`.

### 3.3 Funcional: pipeline Python (`python/pipeline.py`)
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

## 4. Control de efectos secundarios y seguridad en concurrencia
| Módulo | Mecanismo |
|---|---|
| Python | Functional Core / Imperative Shell: núcleo puro sin estructuras mutables; efectos solo en `source_stream` y `main`. |
| C++ | Escrituras en posiciones exclusivas por hilo; `reduction(min:best)`; `pos` privado en la compactación; resultado idéntico al secuencial verificado en cada ejecución. |
| Java | `record` inmutables; estado privado; instantáneas `List.copyOf`; `synchronized` en `MotorDecision` y `ActuadorFrenado`; `volatile` en campos leídos entre hilos. |

## 5. Análisis de complejidad (Big O)

#

## 6. Pruebas y benchmarks

### 6.1 Metodología
Medición con `make benchmark` (`scripts/benchmark.sh`): N = 2·10⁶ puntos, nube sintética con semilla fija, hilos ∈ {1, 2, 4, 8}, compilación con `g++ -O3 -fopenmp`. Para que la comparación sea justa, cada ejecución del programa hace **(1) una corrida de calentamiento de cada modo, descartada, y (2) tres repeticiones alternadas secuencial/paralelo, tomando el mínimo de cada modo**; el script repite la ejecución 3 veces y conserva el mínimo. Cada corrida verifica que el resultado paralelo sea idéntico al secuencial. Speedup = tiempo secuencial / tiempo paralelo; eficiencia = speedup / hilos.

**Lección sobre el método (sesgo detectado).** Una primera versión medía una sola corrida de cada modo, la secuencial primero. Al probar con **1 hilo** el modo "paralelo" salió 1.32× más rápido que el secuencial, lo que llevó a revisar el protocolo porque la segunda corrida reutilizaba la memoria que la primera ya había reservado y se ahorraba los fallos de página. Con el calentamiento y las repeticiones, la prueba con 1 hilo da 1.00×, que es lo esperado. Toda medición debe pasar esa prueba de cordura (1 hilo ⇒ speedup ≈ 1) antes de reportarse.

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

Lectura: (1) el ordenamiento, que antes era el techo (1.06× en total), ahora se acelera 2.42× y su tiempo paralelo (45.78 ms) es cercano a la estimación citada de 45.7 ms para esa etapa. (2) El modelo predijo 63.9 ms en total y se midieron 94.4 ms: la diferencia está casi toda en las etapas 1 y 3, que **no aceleraron**. Dos causas identificadas: el relleno secuencial con ceros de los arreglos grandes y la creación de los hilos de OpenMP dentro de la primera región paralela; ambas se corrigieron (memoria sin inicializar y calentamiento). (3) Este 1.68× pudo estar inflado por el sesgo descrito en §6.1, por lo que **no debe citarse como resultado final**: la medición definitiva queda pendiente en §6.6.

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
Ejecución con `--threads 4 --stages` en un entorno de **un solo núcleo** (los hilos se ejecutan en serie, la tabla permite examinar la distribución del costo por etapa):

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

### 6.6 Validación de rendimiento pendiente

El informe base registra una línea de referencia y una medición preliminar en el computador del grupo. Falta la serie definitiva con la versión final en hardware multinúcleo. Para completar la comparación deben registrarse CPU, núcleos físicos, sistema operativo, tiempos secuencial y paralelo, speedup y eficiencia para 1, 2, 4 y 8 hilos.

### 6.7 Pruebas de correctitud e integración

La ejecución integrada en Windows se confirmó en la revisión del proyecto mediante `java -cp java/out g1.app.Main .`. La suite documentada en el repositorio cubre los siguientes casos.
| Módulo | Pruebas | Qué verifican |
|---|---:|---|
| C++ | 5 | Resultado paralelo idéntico al secuencial con 1, 2, 3, 4 y 8 hilos (`make test-cpp`). |
| Python | 11 | Pureza, no mutación de entradas, ventanas como tuplas, flujo infinito perezoso, ausencia de estructuras mutables en el núcleo, contrato JSON. |
| Java | 26 | Inmutabilidad y aliasing (8): campos `final`, invariantes, historial inmutable, **instantánea estática**, recorrido sin `ConcurrentModificationException`, demostración del fallo de la vista, bitácora, dependencias nulas. Template Method (4). LSP (2). Strategy y motor (5). **Thread safety (2):** 8 hilos × 250 eventos y 8 hilos × 100 frenados. Contratos JSON (3). Integración real C++ + Python + Java (2). |

Se ejecutan con `make test`.

## 7. Conclusiones y limitaciones

- El prototipo integra los tres paradigmas mediante procesos independientes. La interfaz `ProveedorPercepcion` desacopla el motor de decisión de la ejecución de C++ y Python.
- El ordenamiento representó el 70.3 % del tiempo en la línea base. En la medición preliminar con cuatro hilos, el ordenamiento por bloques redujo el tiempo de esa etapa de 110.94 a 45.78 ms. La fusión final y la segmentación siguen formando parte del camino crítico.
- El historial inmutable protege al consumidor de cambios posteriores y requiere copiar los eventos acumulados. La comunicación por procesos externos añade latencia al ciclo integrado.
- La validación documentada cubre un prototipo con datos sintéticos y frenado simulado. La medición definitiva multinúcleo queda pendiente.

## Referencias

- Universidad Sergio Arboleda. *Especificación del Proyecto Final Integrador. Paradigmas de Programación (SIST0176)*. Secciones 3 a 5.
- Grupo 1. [Arquitectura UML y flujo de datos](02_arquitectura_uml.md) y [documentación del repositorio](../README.md).

## Anexo. Reproducibilidad

Desde la raíz del repositorio, en un entorno con GNU Make, g++ con OpenMP, JDK 17 y Python 3.10 o posterior, siga la configuración del [README](../README.md).

```bash
make
make test
make run-integracion
make benchmark
./cpp/lidar_kernel --n 2000000 --threads 4 --stages
```

Para la ejecución integrada confirmada en Windows, con los módulos compilados y las rutas configuradas para ese entorno, utilice:

```powershell
java -cp java/out g1.app.Main .
```
