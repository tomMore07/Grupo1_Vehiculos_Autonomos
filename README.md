# Grupo 1 — Detección de anomalías e inferencia en tiempo real (vehículos autónomos)
Proyecto Final Integrador · Paradigmas de Programación (SIST0176) · Universidad Sergio Arboleda

**Estado: versión final** — tres módulos implementados, integrados y probados; ajustes de la revisión aplicados.

## Descripción
Plataforma a bordo de un vehículo autónomo que filtra nubes de puntos LIDAR, detecta anomalías en la telemetría y decide un frenado de emergencia. Cada módulo usa el paradigma que mejor explota su tarea:

| Módulo | Paradigma | Carpeta | Contenido |
|---|---|---|---|
| 1. Kernel LIDAR | Imperativo, C++17 + OpenMP | `cpp/` | Kernel de 7 etapas O(N log N), memoria contigua (SoA), ordenamiento paralelo por bloques + fusión, compactación paralela |
| 2. Motor de decisión | POO, Java 17 | `java/` | 13 clases: Strategy, Template Method, Adapter; `record` inmutables; historial como instantánea (`List.copyOf`); thread safe |
| 3. Pipeline de telemetría | Funcional, Python 3.10+ | `python/` | Funciones puras, sin estructuras mutables, evaluación perezosa, currificación, `map`/`filter`/`reduce` |

## Arquitectura
```
Sensores → C++ (obstáculo más cercano) ──┐
                                         ├→ AdaptadorKernelCpp → MotorDecision → ActuadorFrenado
Sensores → Python (anomalías) ───────────┘      (Adapter)         (Strategy)     (Template Method)
```
Los módulos son procesos independientes que se comunican por **JSON**. `MotorDecision` solo conoce la interfaz `ProveedorPercepcion` (DIP).

## Estructura
```
.
├── cpp/        Kernel LIDAR (lidar_kernel.hpp/.cpp, main.cpp)
├── java/       Motor de decisión (src/g1/..., test/g1/Pruebas.java)
├── python/     Pipeline funcional (pipeline.py, test_pipeline.py)
├── scripts/    benchmark.sh (tabla de speedup)
├── docs/       Documentación y entregables
└── Makefile
```

## Requisitos
`g++` con OpenMP y `make` · JDK 17+ · Python ≥ 3.10 · en Windows, **WSL** (el adaptador ejecuta `cpp/lidar_kernel` y `python3`).
```bash
sudo apt update && sudo apt install -y build-essential openjdk-21-jdk python3
```

## Uso
```bash
make                  # compila C++ y Java
make test             # pruebas: C++ (5), Python (11), Java (26)
make run-integracion  # ciclo completo: C++ + Python + Java
make benchmark        # tabla de speedup: 1, 2, 4 y 8 hilos
make run-cpp          # kernel con tiempo por etapa
make clean
```
```bash
./cpp/lidar_kernel --n 2000000 --threads 4 --stages   # secuencial vs paralelo, etapa por etapa
./cpp/lidar_kernel --n 100000 --json                  # salida para Java
cd python && python3 pipeline.py --json --limit 5     # anomalías en JSON
```
Salida esperada de `make test`: `kernel con N hilo(s): resultado paralelo == secuencial`, `Ran 11 tests ... OK` y `Resultado: 26 OK, 0 con fallo`.

## Documentación (`docs/`)
| Archivo | Contenido |
|---|---|
| `01_arquitectura_uml.pdf` | Arquitectura: UML de clases, **DFD formal** (niveles 0 y 1), patrones GoF, SOLID y encapsulamiento |
| `02_arquitectura_uml.md` | Los mismos diagramas en **Mermaid** (clases, DFD, secuencia) |
| `03_informe_tecnico_final.pdf` (`.md`) | Informe técnico final: implementación, análisis Big O formal, benchmarks, side effects y pruebas |


## Pruebas de inmutabilidad y aliasing
`java/test/g1/Pruebas.java`, sección "Inmutabilidad y aliasing". Se ejecutan con `make test-java`. Las protecciones están en `MotorDecision.getHistorial()` y `ActuadorFrenado.getBitacora()` (devuelven `List.copyOf`).

## Rendimiento
El diseño inicial, con `std::sort` secuencial, obtuvo **1,06× de aceleración con cuatro hilos**. El ordenamiento representaba alrededor del **70 % del tiempo**, y la fracción secuencial total daba una cota de Amdahl de **1,17× con cuatro hilos**. La versión final ordena bloques en paralelo y los fusiona por rondas. El modelo de la sección 6.5 estimó **2,9× con cuatro hilos** y **4,6× con ocho**. En la medición con `make benchmark` en WSL, con dos millones de puntos sintéticos, se obtuvieron **2,33× con cuatro hilos** y **2,34× con ocho**. El menor tiempo paralelo fue **38,9 ms con cuatro hilos**. Estos resultados corresponden a la sección 6.6.

## Integrantes Grupo 1
* Sara Valentina Díaz Rojas
* Tomás Felipe Morera Pérez
* Cristhian Santiago Pinilla Vega
