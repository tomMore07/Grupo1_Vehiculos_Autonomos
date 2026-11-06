# Grupo 1 — Detección de anomalías e inferencia en tiempo real (vehículos autónomos)
Proyecto Final Integrador · Paradigmas de Programación (SIST0176) · Universidad Sergio Arboleda

**Estado: Fase 1 (Sesiones 1 y 2)** — Módulo Java pendiente (Sesión 3); el diseño ya está en `docs/`.

| Módulo | Paradigma | Carpeta | Estado |
|---|---|---|---|
| 1. Kernel LIDAR | Imperativo, C++17 + OpenMP | `cpp/` | Kernel inicial paralelizado |
| 2. Motor de actuadores | POO, Java 17 | `java/` | UML y contratos (`docs/02_arquitectura_uml.md`) |
| 3. Pipeline de telemetría | Funcional, Python 3.10+ | `python/` | Pipeline básico + tests |

## Uso
```bash
make run-cpp      # compila y compara secuencial vs OpenMP (verifica resultados idénticos)
./cpp/lidar_kernel --n 1000000 --threads 4
./cpp/lidar_kernel --n 100000 --json   # salida para integración con Java
make test-py      # pruebas unitarias del pipeline
make run-py       # demo: anomalías sobre un generador infinito
```
Requisitos: g++ con OpenMP (`-fopenmp`), Python ≥ 3.10.

## Documentación
- `docs/01_requisitos.md` — requisitos y contratos de interfaz
- `docs/02_arquitectura_uml.md` — UML (Mermaid) y flujo de datos
- `docs/03_bigO_preliminar.md` — análisis de complejidad
