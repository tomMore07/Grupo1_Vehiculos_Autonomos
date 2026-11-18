#!/usr/bin/env bash
# Benchmark secuencial vs. paralelo con distintos números de hilos.
# Uso: scripts/benchmark.sh [N] [repeticiones]     (por defecto N=2000000, 3 repeticiones)
# Imprime una tabla Markdown lista para pegar en el informe. Se toma el MÍNIMO de las repeticiones.
N=${1:-2000000}; REP=${2:-3}; BIN=./cpp/lidar_kernel
[ -x "$BIN" ] || { echo "Compile primero con 'make'"; exit 1; }
echo "Núcleos disponibles: $(nproc)   N=$N   repeticiones=$REP"
echo
echo "| Hilos | Secuencial (s) | Paralelo (s) | Speedup | Eficiencia |"
echo "|---|---|---|---|---|"
for T in 1 2 4 8; do
  best_s=999; best_p=999
  for _ in $(seq "$REP"); do
    out=$($BIN --n "$N" --threads "$T" | grep secuencial)
    s=$(echo "$out" | sed 's/secuencial=\([0-9.]*\) s.*/\1/')
    p=$(echo "$out" | sed 's/.*paralelo=\([0-9.]*\) s.*/\1/')
    best_s=$(awk -v a="$best_s" -v b="$s" 'BEGIN{print (b<a)?b:a}')
    best_p=$(awk -v a="$best_p" -v b="$p" 'BEGIN{print (b<a)?b:a}')
  done
  awk -v T="$T" -v s="$best_s" -v p="$best_p" 'BEGIN{printf "| %d | %.4f | %.4f | %.2fx | %.0f%% |\n", T, s, p, s/p, 100*s/p/T}'
done
