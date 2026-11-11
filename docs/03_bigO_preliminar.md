# Análisis Big O preliminar

> **Versión formal con demostraciones, mediciones y análisis de Amdahl:** `02_informe_avance_bigO.pdf`.

Sea **N** el número de puntos de entrada, **M ≤ N** los que sobreviven al filtro de suelo/alcance y **V ≤ M** los vóxeles válidos.

| Etapa (cpp/lidar_kernel.cpp) | Tiempo | Espacio extra | Paralelizable |
|---|---|---|---|
| 1. Máscara suelo/alcance | Θ(N) | Θ(N) | Sí (`parallel for`) |
| 2. Compactación | Θ(N) | Θ(M) | No (orden determinista) |
| 3. Claves de vóxel | Θ(M) | Θ(M) | Sí |
| 4. `std::sort` por clave | Θ(M log M) | O(1)–O(log M) | No (fase 2: ordenamiento paralelo) |
| 5. Segmentación en vóxeles | Θ(M) | O(V) | No |
| 6. Centroides | Θ(M) | Θ(V) | Sí |
| 7. Mínimo en corredor | Θ(V) | O(1) | Sí (`reduction`) |

**Tiempo total:** T(N) = c₁N + c₂M + c₃·M log M + c₄V ∈ Θ(N) + Θ(M log M) ⊆ **O(N log N)**, pues M ≤ N ⇒ M log M ≤ N log N y las etapas lineales quedan absorbidas por el término dominante: para N ≥ 2, N ≤ N log₂N.

**Espacio total:** máscara N bytes + índices M·4 B + claves M·16 B + salida V·16 B ∈ **Θ(N)**.

**Con p hilos (modelo ideal):** T_p ≈ (c₁N + c₃'M + c₆M)/p + c₃·M log M + c₂M + c₄M. La parte secuencial (ordenamiento, compactación, segmentación) limita el speedup según la ley de Amdahl; el ordenamiento es el cuello de botella a atacar en la Fase 3.

## Pipeline funcional
- `sliding_windows`, `moving_average`: O(N·W) con W el tamaño de ventana (recalcula la media por ventana); **espacio O(W)** gracias a la evaluación perezosa. Mejora posible a O(N) con suma acumulada.
- `detect_anomalies`: O(N·W) tiempo, O(W) espacio.
