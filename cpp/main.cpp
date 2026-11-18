// Ejecutable de verificación y benchmark inicial: secuencial vs. OpenMP.
#include "lidar_kernel.hpp"
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <utility>
#ifdef _OPENMP
#include <omp.h>
#endif

static double now() {
    using namespace std::chrono;
    return duration<double>(steady_clock::now().time_since_epoch()).count();
}

int main(int argc, char** argv) {
    std::size_t n = 2'000'000; int threads = 0; bool json = false, stages = false; int reps = -1;
    for (int i = 1; i < argc; ++i) {
        if (!std::strcmp(argv[i], "--n") && i + 1 < argc) n = std::strtoull(argv[++i], nullptr, 10);
        else if (!std::strcmp(argv[i], "--threads") && i + 1 < argc) threads = std::atoi(argv[++i]);
        else if (!std::strcmp(argv[i], "--json")) json = true;
        else if (!std::strcmp(argv[i], "--stages")) stages = true;
        else if (!std::strcmp(argv[i], "--reps") && i + 1 < argc) reps = std::atoi(argv[++i]);
    }
#ifdef _OPENMP
    if (threads > 0) omp_set_num_threads(threads);
    const int nt = omp_get_max_threads();
#else
    const int nt = 1;
#endif
#ifdef _OPENMP
    // Se crean los hilos de OpenMP antes de medir: en el vehículo vivirían todo el programa,
    // y así el tiempo de creación no se cuenta dentro de la primera etapa paralela.
    #pragma omp parallel
    { volatile int calentamiento = omp_get_thread_num(); (void)calentamiento; }
#endif
    const lidar::Params prm;
    const auto cloud = lidar::synthetic_cloud(n, 42);

    // Medición justa: (1) una corrida de calentamiento de cada modo, descartada, para que ambos
    // encuentren la memoria ya reservada (si no, el que corre primero paga los fallos de página);
    // (2) varias repeticiones alternadas y se toma el MÍNIMO de cada modo.
    const bool warm = !json;                    // en modo --json (integración con Java) no se repite
    if (reps < 0) reps = json ? 1 : 3;
    lidar::Result rs, rp;
    double ts = 1e30, tp = 1e30;
    auto medir = [&](bool par, lidar::Result& mejor, double& tmin) {
        const double t0 = now();
        lidar::Result r = lidar::process(cloud, prm, par);
        const double t = now() - t0;
        if (t < tmin) { tmin = t; mejor = std::move(r); }
    };
    if (warm) { double d1 = 1e30, d2 = 1e30; lidar::Result descartado; medir(false, descartado, d1); medir(true, descartado, d2); }
    for (int k = 0; k < reps; ++k) { medir(false, rs, ts); medir(true, rp, tp); }

    const bool same = rs.n_voxels == rp.n_voxels && rs.filtered.x == rp.filtered.x &&
                      rs.filtered.y == rp.filtered.y && rs.filtered.z == rp.filtered.z &&
                      rs.min_obstacle_dist == rp.min_obstacle_dist;
    if (json) {  // contrato de salida para la integración con Java (Fase 2)
        std::printf("{\"n_in\":%zu,\"n_ground\":%zu,\"n_noise_pts\":%zu,\"n_voxels\":%zu,"
                    "\"min_obstacle_dist\":%.3f,\"emergency_brake\":%s}\n",
                    rp.n_in, rp.n_ground, rp.n_noise_pts, rp.n_voxels, rp.min_obstacle_dist,
                    rp.emergency_brake ? "true" : "false");
        return same ? 0 : 1;
    }
    std::printf("N=%zu  hilos=%d\n", n, nt);
    std::printf("suelo=%zu  fuera_rango=%zu  ruido=%zu  voxeles=%zu\n", rp.n_ground, rp.n_out_range, rp.n_noise_pts, rp.n_voxels);
    std::printf("obstaculo_min=%.2f m  frenado_emergencia=%s\n", rp.min_obstacle_dist, rp.emergency_brake ? "SI" : "NO");
    std::printf("secuencial=%.4f s  paralelo=%.4f s  speedup=%.2fx\n", ts, tp, ts / tp);
    std::printf("resultados identicos: %s\n", same ? "OK" : "FALLO");
    if (stages) {
        static const char* names[7] = {"1 mascara suelo/alcance", "2 compactacion", "3 claves de voxel",
            "4 ordenamiento", "5 segmentacion (secuencial)", "6 centroides", "7 minimo en corredor"};
        std::printf("\nTiempo por etapa (ms):\n  %-30s %10s %10s %9s\n", "etapa", "secuencial", "paralelo", "speedup");
        double ts_tot = 0, tp_tot = 0;
        for (int k = 0; k < 7; ++k) {
            ts_tot += rs.stage_ms[k]; tp_tot += rp.stage_ms[k];
            std::printf("  %-30s %10.2f %10.2f %8.2fx\n", names[k], rs.stage_ms[k], rp.stage_ms[k],
                        rp.stage_ms[k] > 0 ? rs.stage_ms[k] / rp.stage_ms[k] : 0.0);
        }
        std::printf("  %-30s %10.2f %10.2f %8.2fx\n", "TOTAL", ts_tot, tp_tot, ts_tot / tp_tot);
        std::printf("  de la etapa 4 (paralelo): bloques=%.2f ms, fusion final=%.2f ms\n",
                    rp.stage_ms[3] - rp.merge_ms, rp.merge_ms);
    }
    return same ? 0 : 1;
}
