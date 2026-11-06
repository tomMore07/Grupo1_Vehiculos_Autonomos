#include "lidar_kernel.hpp"
#include <algorithm>
#include <cmath>
#include <limits>
#include <random>
#include <utility>
#ifdef _OPENMP
#include <omp.h>
#endif

namespace lidar {

namespace {
constexpr std::int64_t OFFSET = 1 << 20;  // 21 bits por eje -> clave de 63 bits

inline std::uint64_t voxel_key(float x, float y, float z, float inv) {
    auto q = [&](float v) { return static_cast<std::uint64_t>(static_cast<std::int64_t>(std::floor(v * inv)) + OFFSET) & 0x1FFFFF; };
    return (q(x) << 42) | (q(y) << 21) | q(z);
}
} // namespace

Result process(const PointCloud& in, const Params& p, bool parallel) {
    Result r;
    const std::int64_t n = static_cast<std::int64_t>(in.size());
    r.n_in = static_cast<std::size_t>(n);

    // ---- Etapa 1: filtro de suelo y alcance (O(N), paralelo). mask: 0 ok, 1 suelo, 2 fuera de rango
    std::vector<std::uint8_t> mask(n);
    const float zmin = p.ground_z + p.ground_margin, r2max = p.max_range * p.max_range;
    #pragma omp parallel for if(parallel) schedule(static)
    for (std::int64_t i = 0; i < n; ++i) {
        const float d2 = in.x[i]*in.x[i] + in.y[i]*in.y[i] + in.z[i]*in.z[i];
        mask[i] = (in.z[i] <= zmin) ? 1 : (d2 > r2max ? 2 : 0);
    }

    // ---- Etapa 2: compactación (O(N), secuencial para preservar orden determinista)
    std::vector<std::uint32_t> kept;
    kept.reserve(n);
    for (std::int64_t i = 0; i < n; ++i) {
        if (mask[i] == 0) kept.push_back(static_cast<std::uint32_t>(i));
        else if (mask[i] == 1) ++r.n_ground; else ++r.n_out_range;
    }

    // ---- Etapa 3: claves de vóxel (O(M), paralelo)
    const std::int64_t m = static_cast<std::int64_t>(kept.size());
    const float inv = 1.0f / p.voxel_size;
    std::vector<std::pair<std::uint64_t, std::uint32_t>> keys(m);
    #pragma omp parallel for if(parallel) schedule(static)
    for (std::int64_t k = 0; k < m; ++k) {
        const std::uint32_t i = kept[k];
        keys[k] = {voxel_key(in.x[i], in.y[i], in.z[i], inv), i};
    }

    // ---- Etapa 4: ordenamiento por clave, desempate por índice (O(M log M)) -> determinista
    std::sort(keys.begin(), keys.end());

    // ---- Etapa 5: límites de cada vóxel (O(M)) y filtro de ruido (vóxeles dispersos)
    std::vector<std::pair<std::int64_t, std::int64_t>> runs;  // [inicio, fin)
    for (std::int64_t s = 0; s < m;) {
        std::int64_t e = s + 1;
        while (e < m && keys[e].first == keys[s].first) ++e;
        if (e - s >= p.min_pts_voxel) runs.emplace_back(s, e);
        else r.n_noise_pts += static_cast<std::size_t>(e - s);
        s = e;
    }

    // ---- Etapa 6: centroides por vóxel (O(M), paralelo; cada hilo escribe celdas distintas)
    const std::int64_t v = static_cast<std::int64_t>(runs.size());
    r.n_voxels = static_cast<std::size_t>(v);
    r.filtered.x.resize(v); r.filtered.y.resize(v); r.filtered.z.resize(v); r.filtered.intensity.resize(v);
    #pragma omp parallel for if(parallel) schedule(static)
    for (std::int64_t j = 0; j < v; ++j) {
        double sx = 0, sy = 0, sz = 0, si = 0;
        for (std::int64_t k = runs[j].first; k < runs[j].second; ++k) {
            const std::uint32_t i = keys[k].second;
            sx += in.x[i]; sy += in.y[i]; sz += in.z[i]; si += in.intensity[i];
        }
        const double c = static_cast<double>(runs[j].second - runs[j].first);
        r.filtered.x[j] = static_cast<float>(sx / c);
        r.filtered.y[j] = static_cast<float>(sy / c);
        r.filtered.z[j] = static_cast<float>(sz / c);
        r.filtered.intensity[j] = static_cast<float>(si / c);
    }

    // ---- Etapa 7: obstáculo más cercano en el corredor frontal (reducción min, paralelo)
    float best = std::numeric_limits<float>::max();
    #pragma omp parallel for if(parallel) reduction(min:best) schedule(static)
    for (std::int64_t j = 0; j < v; ++j) {
        if (r.filtered.x[j] > 0.0f && std::fabs(r.filtered.y[j]) <= p.corridor_half_w)
            best = std::min(best, r.filtered.x[j]);
    }
    if (best != std::numeric_limits<float>::max()) {
        r.min_obstacle_dist = best;
        r.emergency_brake = best < p.brake_distance;
    }
    return r;
}

PointCloud synthetic_cloud(std::size_t n, std::uint32_t seed) {
    std::mt19937 gen(seed);
    std::uniform_real_distribution<float> ux(-50, 50), uy(-30, 30), uz(-1.6f, 2.5f), ui(0, 1), un(0, 1);
    std::normal_distribution<float> g(0.0f, 0.15f);
    PointCloud pc; pc.reserve(n);
    const std::size_t n_obs = n / 20;           // 5% obstáculo denso a ~9 m al frente
    for (std::size_t i = 0; i < n; ++i) {
        const float t = un(gen);
        if (i < n_obs)       pc.push(9.0f + g(gen), 0.5f + g(gen), 0.3f + g(gen), ui(gen));
        else if (t < 0.40f)  pc.push(ux(gen), uy(gen), -1.5f + 0.05f * g(gen), ui(gen));  // suelo
        else if (t < 0.45f)  pc.push(ux(gen) * 2, uy(gen), uz(gen), ui(gen));             // ruido disperso
        else                 pc.push(ux(gen), uy(gen), uz(gen), ui(gen));
    }
    return pc;
}

} // namespace lidar
