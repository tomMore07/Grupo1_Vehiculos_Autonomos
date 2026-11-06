# Sesión 1 — Análisis del caso y requisitos (Grupo 1)

**Caso:** Detección de anomalías e inferencia en tiempo real para vehículos autónomos.
**Integrantes:** _(completar: 3–4 nombres, roles y usuario Git)_

## Requisitos funcionales
| ID | Requisito | Módulo |
|----|-----------|--------|
| RF1 | Filtrar suelo y puntos fuera de alcance de la nube LIDAR | C++ |
| RF2 | Eliminar ruido (vóxeles dispersos) y reducir la nube a centroides | C++ |
| RF3 | Calcular la distancia al obstáculo más cercano en el corredor de marcha | C++ |
| RF4 | Paralelizar el kernel con OpenMP con resultados idénticos a la versión secuencial | C++ |
| RF5 | Calcular promedios móviles sobre telemetría (stream potencialmente infinito) | Python |
| RF6 | Detectar anomalías (z-score contra ventana previa) de forma perezosa | Python |
| RF7 | Evaluar el riesgo con estrategias intercambiables | Java |
| RF8 | Ejecutar la secuencia de frenado de emergencia con pasos fijos y pasos variables por tipo de freno | Java |
| RF9 | Mantener historial de eventos sin exponer estado mutable (sin aliasing) | Java |

## Requisitos no funcionales
- **RNF1 Latencia:** procesar una nube de 2 M puntos en < 200 ms (referencia: 0.15 s en 1 hilo).
- **RNF2 Determinismo:** misma entrada ⇒ misma salida, secuencial o paralelo.
- **RNF3 Pureza:** el módulo funcional no realiza I/O ni muta sus entradas (Functional Core, Imperative Shell).
- **RNF4 Inmutabilidad:** `record` y `Collections.unmodifiableList` en Java.
- **RNF5 Thread safety:** sin *race conditions* (cada hilo escribe celdas distintas; `reduction` para el mínimo).

## Contratos de interfaz entre módulos
**C++ → Java** (salida `--json` del ejecutable, línea JSON por nube):
```json
{"n_in":100000,"n_ground":41687,"n_noise_pts":51426,"n_voxels":89,"min_obstacle_dist":8.528,"emergency_brake":true}
```
`min_obstacle_dist = -1` si no hay obstáculo en el corredor.

**Python → Java:** cada anomalía como `{"t":int,"value":float,"mean":float,"zscore":float}`.

**Java (consumidor):** la interfaz `ProveedorPercepcion` oculta cómo se obtienen los datos (proceso externo, JNI, etc.) → DIP.
