Grupo 1 — Detección de anomalías e inferencia en tiempo real (vehículos autónomos)

Proyecto Final Integrador · Paradigmas de Programación (SIST0176) · Universidad Sergio Arboleda

Estado: Entrega Parcial (Sesión 3, 30 %) — Fase 1 (Sesiones 1 y 2) y Fase 2 (Sesión 3) completadas. Los tres módulos están implementados, integrados y probados.

Descripción

Plataforma a bordo de un vehículo autónomo que procesa telemetría de sensores para detectar obstáculos y anomalías y decidir un frenado de emergencia. Cada módulo usa el paradigma que mejor explota su tarea:

Módulo	Paradigma	Carpeta	Estado
1. Kernel LIDAR	Imperativo, C++17 + OpenMP	cpp/	Kernel de 7 etapas O(N log N) sobre memoria contigua (SoA), versión secuencial y paralela con resultados idénticos, medición por etapa
2. Motor de decisión	Orientado a objetos, Java 17	java/	Dominio inmutable (record), Strategy, Template Method, Adapter; 22 pruebas automáticas
3. Pipeline de telemetría	Funcional, Python 3.10+	python/	Funciones puras, evaluación perezosa, currificación, salida JSON; 8 pruebas unitarias
Arquitectura e integración
Sensores → C++ (obstáculo más cercano) ──┐
                                         ├→ AdaptadorKernelCpp → MotorDecision → ActuadorFrenado
Sensores → Python (anomalías) ───────────┘      (Java)            (Strategy)     (Template Method)

Los módulos son procesos independientes que se comunican por la salida estándar en formato JSON. Java los consume a través de la interfaz ProveedorPercepcion, por lo que el dominio no depende de C++ ni de Python (DIP).

Patrones y principios: Template Method (ActuadorFrenado.ejecutarSecuenciaFrenado(), final), Strategy (RiesgoPorDistancia, RiesgoPorTiempoColision), Adapter (AdaptadorKernelCpp), SOLID.
Inmutabilidad y aliasing: record con invariantes validados, Collections.unmodifiableList para el historial y List.copyOf para resultados.
Functional Core, Imperative Shell: el núcleo de pipeline.py es puro; el azar y la impresión quedan en la cáscara.
Estructura del repositorio
.
├── cpp/        Kernel LIDAR (lidar_kernel.hpp/.cpp, main.cpp)
├── java/       Motor de decisión (src/g1/..., test/g1/Pruebas.java)
├── python/     Pipeline funcional (pipeline.py, test_pipeline.py)
├── docs/       Documentación y entregables
├── Makefile    Automatización de compilación, pruebas y ejecución
└── README.md
Requisitos
g++ con soporte OpenMP (-fopenmp) y make
JDK 17 o superior
Python 3.10 o superior
Linux, macOS o Windows con WSL (el adaptador de Java ejecuta cpp/lidar_kernel y python3, por lo que no funciona en Windows nativo)

Instalación en Ubuntu/WSL:

bash
sudo apt update && sudo apt install -y build-essential openjdk-21-jdk python3
Uso
bash
make                  # compila C++ y Java
make test             # pruebas de Python (8) y Java (22, incluye integración real)
make run-integracion  # ciclo completo: C++ + Python + Java
make run-cpp          # benchmark secuencial vs OpenMP (verifica resultados idénticos)
make run-py           # demo: anomalías sobre un generador infinito
make clean            # elimina binarios y clases compiladas

Opciones del kernel C++:

bash
./cpp/lidar_kernel --n 2000000 --threads 4 --stages   # tiempo por etapa y límite de Amdahl
./cpp/lidar_kernel --n 100000 --json                  # salida JSON para integración con Java

Pipeline de Python:

bash
cd python
python3 pipeline.py --json --limit 5   # una anomalía por línea en JSON
python3 -m unittest -v

Resultado esperado de make test: Ran 8 tests ... OK y Resultado: 22 OK, 0 con fallo. En make run-integracion el nivel de riesgo debe ser CRITICO con el frenado de emergencia registrado en el historial.

Contratos de interfaz
Enlace	Formato	Ejemplo
C++ → Java	Una línea JSON por nube (min_obstacle_dist = -1 si no hay obstáculo)	{"n_in":100000,"n_voxels":89,"min_obstacle_dist":8.528,"emergency_brake":true}
Python → Java	Una anomalía por línea	{"t": 97, "value": 13.483, "mean": 24.962, "zscore": -32.173}
Documentación
docs/01_arquitectura_uml.pdf — Documento de Arquitectura y Especificación UML (diagrama de clases, flujo de datos, patrones y SOLID)
docs/02_informe_avance_bigO.pdf — Informe de Avance Técnico y Análisis Big O (demostraciones, mediciones y análisis de Amdahl)
docs/01_requisitos.md — requisitos y contratos de interfaz
docs/02_arquitectura_uml.md — borrador del UML en Mermaid (la versión final está en el PDF)
docs/03_bigO_preliminar.md — borrador del análisis de complejidad
docs/gen_uml.py — regenera uml_clases.svg y flujo_datos.svg (requiere Graphviz)
Resultados de rendimiento (línea base)

Medición del kernel con N = 2 000 000 puntos y 4 hilos:

Métrica	Valor
Speedup real (secuencial vs. paralelo)	1.06×
Peso del ordenamiento (etapa secuencial)	≈ 70 % del tiempo
Fracción secuencial f	≈ 0.81
Speedup máximo según Amdahl (p = 4)	1.17×

El cuello de botella es el ordenamiento de claves de vóxel, que aún es secuencial.

Próximos pasos (Sesión 4)
Paralelizar el ordenamiento y la compactación para superar el límite de Amdahl.
Benchmarks con 1, 2, 4 y 8 hilos y tabla comparativa de speedup.
Pruebas de thread safety en Java y en el kernel.
Refactorización completa de Functional Core / Imperative Shell.
Integrantes Grupo 1
Sara Valentina Díaz Rojas
Tomás Felipe Morera Pérez
Cristhian Santiago Pinilla Vega
