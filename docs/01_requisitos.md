# Especificación de Requerimientos - Grupo 1
## Sistema de Detección de Anomalías e Inferencia en Tiempo Real para Vehículos Autónomos

### 1. Resumen Ejecutivo
El sistema implementa una plataforma a bordo crítica para procesar flujos masivos de telemetría (LIDAR, cámaras, radares), filtrando ruido y detectando obstáculos para la activación de frenados de emergencia en milisegundos.

### 2. Requerimientos por Módulo

* **Módulo Imperativo (C++17):**
  * Kernel geoespacial para filtrado de nubes de puntos LIDAR sobre memoria contigua.
  * Complejidad algorítmica estricta de $O(N \log N)$.
  * Paralelismo explícito en CPU mediante directivas OpenMP (`#pragma omp parallel for`).

* **Módulo Orientado a Objetos (Java 17+):**
  * Motor de actuadores bajo principios SOLID.
  * Implementación del patrón *Template Method* para la secuencia de frenado y patrón *Strategy* para la evaluación de riesgos.
  * Prevención de *aliasing* y protección de invariantes mediante `Collections.unmodifiableList`.

* **Módulo Funcional (Python 3.10+):**
  * Pipeline declarativo puro con funciones de orden superior (`map`, `filter`, `reduce`).
  * Inmutabilidad y transparencia referencial para promedios móviles y detección perezosa de anomalías.
