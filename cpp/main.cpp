// Ejecutable de verificación y benchmark inicial: secuencial vs. OpenMP.
#include "lidar_kernel.hpp"
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#ifdef _OPENMP
#include <omp.h>
#endif

static double now() {
    using namespace std::chrono;
    return duration<double>(steady_clock::now().time_since_epoch()).count();
}

int main(int argc, char** argv) {
    std::size_t n = 2'000'000; int threads = 0; bool json = false;
    for (int i = 1; i < argc; ++i) {
        if (!std::strcmp(argv[i], "--n") && i + 1 < argc) n = std::strtoull(argv[++i], nullptr, 10);
        else if (!std::strcmp(argv[i], "--threads") && i + 1 < argc) threads = std::atoi(argv[++i]);
        else if (!std::strcmp(argv[i], "--json")) json = true;
    }
#ifdef _OPENMP
    if (threads > 0) omp_set_num_threads(threads);
    const int nt = omp_get_max_threads();
#else
    const int nt = 1;
#endif
    const lidar::Params prm;
    const auto cloud = lidar::synthetic_cloud(n, 42);

    double t0 = now(); auto rs = lidar::process(cloud, prm, false); double ts = now() - t0;
    t0 = now();        auto rp = lidar::process(cloud, prm, true);  double tp = now() - t0;

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
    return same ? 0 : 1;
}
