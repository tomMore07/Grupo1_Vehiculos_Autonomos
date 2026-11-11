UNIVERSIDAD SERGIO ARBOLEDA · ESCUELA DE CIENCIAS EXACTAS E INGENIERÍA 
Informe de Avance Técnico y Análisis Big O
Proyecto Final Integrador · Paradigmas de Programación Entrega Parcial (Sesión 3, 30 %)
Grupo 1: Detección de anomalías e inferencia en tiempo real para vehículos autónomos
Integrantes: Sara Díaz, Tomás Morera y Santiago Pinilla.


Notación. N = número de puntos de entrada. M = número de puntos que sobreviven al filtro de suelo y alcance (M ≤ N). V = número de vóxeles válidos (V ≤ M/3, pues min_pts_voxel = 3; en particular V ≤ M). Todas las operaciones aritméticas, comparaciones y accesos a posiciones de un std::vector cuestan Θ(1). Se usa log₂ y se cuenta el peor caso salvo que se indique lo contrario.

Complejidad temporal del kernel C++ (cpp/lidar_kernel.cpp)
Etapa	Qué hace	Costo	Justificación
1	Máscara suelo/alcance	T₁ = k₁·N	Un bucle con cuerpo de costo constante sobre N puntos.
2	Compactación de índices	T₂ = k₂·N	Un bucle sobre N; push_back es O(1) (con reserve(N) previo no hay realocaciones).
3	Claves de vóxel	T₃ = k₃·M	Un bucle sobre los M puntos conservados; cada clave se calcula en Θ(1).
4	std::sort de (clave, índice)	T₄ ≤ k₄·M·log₂M	Desde C++11 la norma exige O(M log M) comparaciones también en el peor caso (introsort). Cada comparación de un par es Θ(1).
5	Segmentación en vóxeles	T₅ ≤ k₅·M	El bucle interno avanza e y el externo asigna s = e: cada posición se visita una sola vez, luego hay ≤ M iteraciones en total.
6	Centroides	T₆ ≤ k₆·M + k₆′·V	Σⱼ (fin − inicio) ≤ M: los rangos de vóxeles son disjuntos; más V divisiones.
7	Mínimo en el corredor	T₇ = k₇·V	Un bucle sobre V centroides.
Teorema 1. T(N) ∈ O(N log N).

Demostración. Sumando las etapas:
T(N) ≤ (k₁+k₂)·N + (k₃+k₅+k₆)·M + k₆′·V + k₇·V + k₄·M·log₂M.
Sea N ≥ 2. Entonces: (i) V ≤ M ≤ N; (ii) log₂N ≥ 1, luego N ≤ N·log₂N; (iii) la función h(x) = x·log₂x es creciente para x ≥ 1, y como M ≤ N, M·log₂M ≤ N·log₂N.
Aplicando (i)–(iii) a cada término:
T(N) ≤ (k₁+k₂+k₃+k₅+k₆+k₆′+k₇)·N·log₂N + k₄·N·log₂N = c·N·log₂N,
con c = k₁+k₂+k₃+k₄+k₅+k₆+k₆′+k₇ y N₀ = 2. Por la definición, T ∈ O(N log N). ∎
Proposición 2 (cota ajustada). Si M ≥ α·N para una constante α ∈ (0,1], entonces T(N) ∈ Θ(N log N).

Demostración. La cota superior es el Teorema 1. Para la inferior, en el modelo de comparaciones ordenar M claves distintas requiere Ω(M log M) comparaciones en el peor caso, y M·log₂M ≥ αN·(log₂N + log₂α). Esta cantidad es ≥ (α/2)·N·log₂N siempre que log₂N ≥ 2·log₂(1/α), es decir, N ≥ 1/α². Luego T(N) ≥ c′·N·log₂N para N ≥ 1/α². ∎
Observación: en los datos sintéticos, M/N ≈ 0.55 (α ≈ 0.55 ⇒ N₀ ≈ 4), de modo que la hipótesis se cumple.
4. Complejidad espacial
Estructura	Tamaño en bytes	Cota
Entrada (4 arreglos float)	16·N	No se cuenta como espacio adicional.
mask (uint8_t)	N	Θ(N)
kept (uint32_t, reserve(N))	4·N reservados	Θ(N)
keys (pair<uint64_t,uint32_t>, 16 B con relleno)	16·M	Θ(M)
runs (pair<int64_t,int64_t>)	≤ 16·V	O(V)
Salida (4 arreglos float de V)	16·V	Θ(V)
Teorema 3. El espacio adicional es S(N) ∈ Θ(N).
Demostración. S(N) = N + 4N + 16M + 32V ≤ N + 4N + 16N + 32N = 53·N bytes (usando V ≤ M ≤ N), luego S ∈ O(N). Además S(N) ≥ N + 4N = 5N por mask y kept, luego S ∈ Ω(N). ∎ El ordenamiento std::sort es in-place (O(log M) de pila).
5. Paralelismo: análisis de Amdahl y hallazgo crítico
Se instrumentó cada etapa (./lidar_kernel --n 2000000 --stages, corrida secuencial):

Etapa	Paralela	Tiempo (ms)	%
1 · Máscara suelo/alcance	Sí (parallel for)	13.03	9.0
2 · Compactación	No	11.73	8.1
3 · Claves de vóxel	Sí	14.78	10.2
4 · Ordenamiento	No	96.65	66.9
5 · Segmentación	No	5.33	3.7
6 · Centroides	Sí	2.91	2.0
7 · Mínimo en corredor	Sí (reduction)	0.02	0.0
Fracción secuencial f = (11.73 + 96.65 + 5.33) / 144.45 = 0.787. Ley de Amdahl: S(p) ≤ 1 / (f + (1 − f)/p).

p (hilos)	S máximo
4	1.19×
8	1.23×
∞	1.27×
Hallazgo: con el diseño actual el speedup real quedará limitado a ≈ 1.2×, porque el ordenamiento secuencial domina el 67 % del tiempo. La rúbrica exige speedup real frente a la versión secuencial, por lo que la paralelización del ordenamiento es la tarea prioritaria de la Sesión 4. Alternativas: (a) ordenar bloques en paralelo y fusionarlos; (b) ordenamiento radix por dígitos de la clave (O(M·k/p)); (c) __gnu_parallel::sort con -fopenmp; (d) compactación con prefijo-suma paralelo. Con f ≈ 0.2 el techo teórico para p = 8 sube a ≈ 3.3×.
Limitación de la medición: el entorno donde se desarrolló esta entrega tiene un solo núcleo (speedup medido = 1.00×). Las tablas de speedup real (4, 8 hilos) deben medirse en el hardware del grupo y reportarse en el informe final.
6. Validación empírica del crecimiento N log N
Tiempo de la versión secuencial (mínimo de 3 ejecuciones, 1 núcleo, g++ -O3). Si T(N) ≈ c·N·log₂N, el cociente T/(N·log₂N) debe mantenerse aproximadamente constante.

N	T (s)	T/(N·log₂N) (ns)	T/N (ns/punto)
250 000	0.0123	2.74	49.2
500 000	0.0262	2.77	52.4
1 000 000	0.0632	3.17	63.2
2 000 000	0.1193	2.85	59.7
4 000 000	0.2820	3.22	70.5
El cociente se mantiene entre 2.7 y 3.2 ns (variación del 17 %) mientras N crece 16 veces, y el tiempo por punto crece lentamente (49 → 70 ns), coherente con N log N y no con N² (que lo habría multiplicado por 16). El ligero aumento de T/(N log N) para N grandes se atribuye a la jerarquía de caché (las claves dejan de caber en caché durante el ordenamiento), un efecto que el modelo teórico no captura.

7. Complejidad del pipeline funcional (python/pipeline.py)
Sea W el tamaño de la ventana y N el número de lecturas consumidas.

Función	Tiempo	Espacio	Justificación
mean_var	Θ(W)	O(1)	Dos reduce sobre W elementos.
sliding_windows	Θ(N·W)	O(W)	Por cada lectura se construye una tupla de W elementos; solo se conserva el deque de W.
moving_average	Θ(N·W)	O(W)	N ventanas × mean_var Θ(W).
detect_anomalies	Θ(N·W)	O(W)	Igual; filter y map son perezosos.
Gracias a la evaluación perezosa, el espacio es independiente de N, lo que permite operar sobre un generador infinito. Si se piden k anomalías y estas aparecen cada P lecturas, el trabajo es O(k·P·W). Mejora identificada: mantener una suma acumulada reduciría moving_average a Θ(N).

8. Complejidad del módulo Java
Operación	Tiempo	Observación
Estrategia.evaluar	O(1)	Comparaciones con umbrales.
ejecutarSecuenciaFrenado	O(1)	Tres pasos de costo constante más el registro en bitácora (ArrayList.add amortizado O(1)).
MotorDecision.evaluarYActuar	O(1) amortizado	Espacio del historial: O(E), con E = número de eventos.
getHistorial()	O(1)	Es una vista; List.copyOf costaría O(E).
JsonPlano.parsear	O(L)	L = longitud de la línea (expresión regular sin retroceso catastrófico).
procesarPercepcion	O(A) + costo externo	A anomalías; el costo dominante es el de los procesos C++ y Python.
9. Pruebas de inmutabilidad y prevención de aliasing (Java)
22 pruebas automáticas (make test-java), todas exitosas: campos final en los record (reflexión), rechazo de invariantes inválidos, historial y bitácora de solo lectura, diferencia vista/copia (unmodifiableList vs. List.copyOf), ejecutarSecuenciaFrenado final, orden de pasos del Template Method, sustituibilidad de las subclases (LSP), umbrales de ambas estrategias, contratos JSON y el ciclo completo con los procesos reales de C++ y Python.

10. Trabajo pendiente (Sesión 4 en adelante)
Prioridad	Tarea
Alta	Paralelizar el ordenamiento y la compactación para superar el límite de Amdahl (§5); medir speedup real con 1, 2, 4 y 8 hilos en hardware multinúcleo.
Alta	Pruebas de thread safety del módulo Java y del kernel (ejecución repetida, ThreadSanitizer).
Media	Refactorización Functional Core / Imperative Shell completa (separar por completo el generador impuro de pipeline.py).
Media	Evaluar JNI frente a procesos externos (latencia por llamada).
Baja	Promedio móvil en O(N) con suma acumulada.
