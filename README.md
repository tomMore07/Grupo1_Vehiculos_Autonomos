# Grupo 1 — Detección de anomalías e inferencia en tiempo real (vehículos autónomos)
Proyecto Final Integrador · Paradigmas de Programación (SIST0176) · Universidad Sergio Arboleda

**Estado: Entrega Parcial (Sesión 3, 30 %)** — Fase 1 (Sesiones 1 y 2) y Fase 2 (Sesión 3) completadas. Los tres módulos están implementados, integrados y probados.

## Descripción
Plataforma a bordo de un vehículo autónomo que procesa telemetría de sensores para detectar obstáculos y anomalías y decidir un frenado de emergencia. Cada módulo usa el paradigma que mejor explota su tarea:

| Módulo | Paradigma | Carpeta | Estado |
|---|---|---|---|
| 1. Kernel LIDAR | Imperativo, C++17 + OpenMP | `cpp/` | Kernel de 7 etapas O(N log N) sobre memoria contigua (SoA), versión secuencial y paralela con resultados idénticos, medición por etapa |
| 2. Motor de decisión | Orientado a objetos, Java 17 | `java/` | Dominio inmutable (`record`), Strategy, Template Method, Adapter; 22 pruebas automáticas |
| 3. Pipeline de telemetría | Funcional, Python 3.10+ | `python/` | Funciones puras, evaluación perezosa, currificación, salida JSON; 8 pruebas unitarias |

## Arquitectura e integración
```
Sensores → C++ (obstáculo más cercano) ──┐
                                         ├→ AdaptadorKernelCpp → MotorDecision → ActuadorFrenado
Sensores → Python (anomalías) ───────────┘      (Java)            (Strategy)     (Template Method)
```

## Estructura del repositorio
```
.
├── cpp/        Kernel LIDAR (lidar_kernel.hpp/.cpp, main.cpp)
├── java/       Motor de decisión (src/g1/..., test/g1/Pruebas.java)
├── python/     Pipeline funcional (pipeline.py, test_pipeline.py)
├── docs/       Documentación y entregables
├── Makefile    Automatización de compilación, pruebas y ejecución
└── README.md
```


Instalación en Ubuntu/WSL:
```bash
sudo apt update && sudo apt install -y build-essential openjdk-21-jdk python3
```

## Uso
```bash
make                  # compila C++ y Java
make test             # pruebas de Python (8) y Java (22, incluye integración real)
make run-integracion  # ciclo completo: C++ + Python + Java
make run-cpp          # benchmark secuencial vs OpenMP (verifica resultados idénticos)
make run-py           # demo: anomalías sobre un generador infinito
make clean            # elimina binarios y clases compiladas
```

Opciones del kernel C++:
```bash
./cpp/lidar_kernel --n 2000000 --threads 4 --stages   # tiempo por etapa y límite de Amdahl
./cpp/lidar_kernel --n 100000 --json                  # salida JSON para integración con Java
```

Pipeline de Python:
```bash
cd python
python3 pipeline.py --json --limit 5   # una anomalía por línea en JSON
python3 -m unittest -v
```

## Integrantes Grupo 1
* Sara Valentina Díaz Rojas
* Tomás Felipe Morera Pérez
* Cristhian Santiago Pinilla Vega
