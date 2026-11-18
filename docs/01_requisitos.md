# Requisitos del sistema (Grupo 1)

**Caso:** detección de anomalías e inferencia en tiempo real para vehículos autónomos.

## Requisitos funcionales
| ID | Requisito | Módulo |
|----|-----------|--------|
| RF1 | Filtrar suelo y puntos fuera de alcance de la nube LIDAR | C++ |
| RF2 | Eliminar ruido (vóxeles dispersos) y reducir la nube a centroides | C++ |
| RF3 | Calcular la distancia al obstáculo más cercano en el corredor de marcha | C++ |
| RF4 | Paralelizar el kernel con OpenMP con resultados idénticos a la versión secuencial | C++ |
| RF5 | Calcular promedios móviles sobre telemetría (flujo potencialmente infinito) | Python |
| RF6 | Detectar anomalías (z-score contra la ventana previa) de forma perezosa | Python |
| RF7 | Evaluar el riesgo con estrategias intercambiables | Java |
| RF8 | Ejecutar la secuencia de frenado con pasos fijos y pasos variables por tipo de freno | Java |
| RF9 | Mantener un historial sin exponer estado mutable (sin aliasing) | Java |

## Requisitos no funcionales
- **RNF1 Determinismo:** misma entrada ⇒ misma salida, en modo secuencial o paralelo.
- **RNF2 Pureza:** el núcleo del módulo funcional no hace I/O, no muta sus entradas y no usa estructuras mutables.
- **RNF3 Inmutabilidad y encapsulamiento:** `record` con invariantes validados; ninguna referencia a estado interno mutable sale de una clase.
- **RNF4 Thread safety:** el motor y el actuador pueden usarse desde varios hilos; el kernel no tiene condiciones de carrera (cada hilo escribe en celdas distintas; `reduction` para el mínimo).
- **RNF5 Rendimiento:** el kernel debe ser O(N log N) y escalar con el número de hilos.

## Contratos de interfaz entre módulos
**C++ → Java** (`lidar_kernel --json`, una línea por nube; `min_obstacle_dist = -1` si no hay obstáculo):
```json
{"n_in":100000,"n_ground":41687,"n_noise_pts":51426,"n_voxels":89,"min_obstacle_dist":8.528,"emergency_brake":true}
```
**Python → Java** (`pipeline.py --json --limit N`, una anomalía por línea):
```json
{"t": 97, "value": 13.483, "mean": 24.962, "zscore": -32.173}
```
**Dentro de Java:** la interfaz `ProveedorPercepcion` oculta cómo se obtienen los datos (DIP). El kernel solo reporta geometría; la política de riesgo vive en Java.
