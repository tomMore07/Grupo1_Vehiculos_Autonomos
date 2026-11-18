#include "lidar_kernel.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>
#include <memory>
#include <random>
#include <utility>
#ifdef _OPENMP
#include <omp.h>
#endif

namespace lidar {

namespace {
constexpr std::int64_t OFFSET = 1 << 20;  // 21 bits por eje -> clave de 63 bits
// (clave de vóxel, índice del punto). Struct simple sin inicializadores: permite crear el arreglo SIN
// rellenarlo de ceros, de modo que la primera escritura ocurra dentro del bucle paralelo (cada hilo
// "toca" sus propias páginas de memoria) en lugar de en un relleno secuencial previo.
struct KeyIdx { std::uint64_t key; std::uint32_t idx; };
inline bool operator<(const KeyIdx& a, const KeyIdx& b) { return a.key != b.key ? a.key < b.key : a.idx < b.idx; }

// Envoltorios para que el código compile también sin OpenMP (entonces todo corre en 1 hilo).
inline int max_threads() {
#ifdef _OPENMP
    return omp_get_max_threads();
#else
    return 1;
#endif
}
inline int thread_id() {
#ifdef _OPENMP
    return omp_get_thread_num();
#else
    return 0;
#endif
}
inline int team_size() {
#ifdef _OPENMP
    return omp_get_num_threads();
#else
    return 1;
#endif
}
inline double ms_since(std::chrono::steady_clock::time_point t0) {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
}

inline std::uint64_t voxel_key(float x, float y, float z, float inv) {
    auto q = [&](float v) { return static_cast<std::uint64_t>(static_cast<std::int64_t>(std::floor(v * inv)) + OFFSET) & 0x1FFFFF; };
    return (q(x) << 42) | (q(y) << 21) | q(z);
}

// Ordenamiento por bloques + fusión (merge sort paralelo):
//   1) se divide el arreglo en p bloques y cada hilo ordena el suyo con std::sort  -> O((M/p) log(M/p))
//   2) se fusionan los bloques de a pares en log2(p) rondas; en cada ronda los pares son independientes.
// Como cada elemento es único (clave + índice), el resultado es idéntico al de un sort secuencial.
void sort_keys(KeyIdx* a, std::int64_t m, bool parallel, double& merge_ms) {
    int p = parallel ? max_threads() : 1;
    if (m < 8192) p = 1;                       // para entradas pequeñas no vale la pena dividir
    merge_ms = 0;
    if (p == 1) { std::sort(a, a + m); return; }

    std::vector<std::int64_t> b(p + 1);        // límites de los bloques
    for (int c = 0; c <= p; ++c) b[c] = m * c / p;

    #pragma omp parallel for schedule(static)
    for (int c = 0; c < p; ++c)                // cada hilo ordena su bloque
        std::sort(a + b[c], a + b[c + 1]);

    const auto t0 = std::chrono::steady_clock::now();
    for (int width = 1; width < p; width *= 2) {
        #pragma omp parallel for schedule(static)
        for (int c = 0; c < p; c += 2 * width) {
            const int mid = c + width;
            if (mid >= p) continue;            // bloque sin pareja en esta ronda
            const int end = std::min(c + 2 * width, p);
            std::inplace_merge(a + b[c], a + b[mid], a + b[end]);
        }
    }
    merge_ms = ms_since(t0);
}
} // namespace

Result process(const PointCloud& in, const Params& p, bool parallel) {
    Result r;
    const std::int64_t n = static_cast<std::int64_t>(in.size());
    r.n_in = static_cast<std::size_t>(n);
    auto t_prev = std::chrono::steady_clock::now();
    auto mark = [&](int stage) {  // registra el tiempo de la etapa que acaba de terminar
        const auto t = std::chrono::steady_clock::now();
        r.stage_ms[stage] = std::chrono::duration<double, std::milli>(t - t_prev).count();
        t_prev = t;
    };

    // ---- Etapa 1: filtro de suelo y alcance (O(N), paralelo). mask: 0 ok, 1 suelo, 2 fuera de rango
    std::unique_ptr<std::uint8_t[]> mask(new std::uint8_t[n]);   // sin inicializar: se llena en el bucle paralelo
    const float zmin = p.ground_z + p.ground_margin, r2max = p.max_range * p.max_range;
    #pragma omp parallel for if(parallel) schedule(static)
    for (std::int64_t i = 0; i < n; ++i) {
        const float d2 = in.x[i]*in.x[i] + in.y[i]*in.y[i] + in.z[i]*in.z[i];
        mask[i] = (in.z[i] <= zmin) ? 1 : (d2 > r2max ? 2 : 0);
    }
    mark(0);

    // ---- Etapa 2: compactación paralela (O(N)). Cada hilo cuenta los puntos buenos de su tramo,
    //      se calcula el desplazamiento de cada tramo (suma de prefijos) y cada hilo escribe en su zona.
    //      Como los tramos son contiguos, el orden final es idéntico al secuencial.
    const int T = parallel ? max_threads() : 1;
    std::vector<std::int64_t> c_keep(T, 0), c_gnd(T, 0), c_rng(T, 0), offset(T + 1, 0);
    std::unique_ptr<std::uint32_t[]> kept;
    std::int64_t m = 0;                                // puntos conservados
    #pragma omp parallel num_threads(T)
    {
        const int nt = team_size(), t = thread_id();   // nt puede ser menor que T: se usa el real
        const std::int64_t lo = n * t / nt, hi = n * (t + 1) / nt;
        std::int64_t k = 0, g = 0, o = 0;
        for (std::int64_t i = lo; i < hi; ++i) {
            if (mask[i] == 0) ++k; else if (mask[i] == 1) ++g; else ++o;
        }
        c_keep[t] = k; c_gnd[t] = g; c_rng[t] = o;
        #pragma omp barrier
        #pragma omp single
        {   // un solo hilo calcula los desplazamientos (nt valores) y reserva el arreglo
            for (int j = 0; j < nt; ++j) offset[j + 1] = offset[j] + c_keep[j];
            m = offset[nt];
            kept.reset(new std::uint32_t[m]);
        }                                               // barrera implícita al final de 'single'
        std::int64_t pos = offset[t];
        for (std::int64_t i = lo; i < hi; ++i)
            if (mask[i] == 0) kept[pos++] = static_cast<std::uint32_t>(i);
    }
    for (int j = 0; j < T; ++j) { r.n_ground += c_gnd[j]; r.n_out_range += c_rng[j]; }
    mark(1);

    // ---- Etapa 3: claves de vóxel (O(M), paralelo)
    const float inv = 1.0f / p.voxel_size;
    std::unique_ptr<KeyIdx[]> keys(new KeyIdx[m]);
    #pragma omp parallel for if(parallel) schedule(static)
    for (std::int64_t k = 0; k < m; ++k) {
        const std::uint32_t i = kept[k];
        keys[k] = KeyIdx{voxel_key(in.x[i], in.y[i], in.z[i], inv), i};
    }
    mark(2);

    // ---- Etapa 4: ordenamiento por clave, desempate por índice (O(M log M)). Paralelo por bloques.
    sort_keys(keys.get(), m, parallel, r.merge_ms);
    mark(3);

    // ---- Etapa 5: límites de cada vóxel (O(M)) y filtro de ruido (vóxeles dispersos)
    std::vector<std::pair<std::int64_t, std::int64_t>> runs;  // [inicio, fin)
    for (std::int64_t s = 0; s < m;) {
        std::int64_t e = s + 1;
        while (e < m && keys[e].key == keys[s].key) ++e;
        if (e - s >= p.min_pts_voxel) runs.emplace_back(s, e);
        else r.n_noise_pts += static_cast<std::size_t>(e - s);
        s = e;
    }
    mark(4);

    // ---- Etapa 6: centroides por vóxel (O(M), paralelo; cada hilo escribe celdas distintas)
    const std::int64_t v = static_cast<std::int64_t>(runs.size());
    r.n_voxels = static_cast<std::size_t>(v);
    r.filtered.x.resize(v); r.filtered.y.resize(v); r.filtered.z.resize(v); r.filtered.intensity.resize(v);
    #pragma omp parallel for if(parallel) schedule(static)
    for (std::int64_t j = 0; j < v; ++j) {
        double sx = 0, sy = 0, sz = 0, si = 0;
        for (std::int64_t k = runs[j].first; k < runs[j].second; ++k) {
            const std::uint32_t i = keys[k].idx;
            sx += in.x[i]; sy += in.y[i]; sz += in.z[i]; si += in.intensity[i];
        }
        const double c = static_cast<double>(runs[j].second - runs[j].first);
        r.filtered.x[j] = static_cast<float>(sx / c);
        r.filtered.y[j] = static_cast<float>(sy / c);
        r.filtered.z[j] = static_cast<float>(sz / c);
        r.filtered.intensity[j] = static_cast<float>(si / c);
    }
    mark(5);

    // ---- Etapa 7: obstáculo más cercano en el corredor frontal (reducción min, paralelo)
    float best = std::numeric_limits<float>::max();
    #pragma omp parallel for if(parallel) reduction(min:best) schedule(static)
    for (std::int64_t j = 0; j < v; ++j) {
        if (r.filtered.x[j] > 0.0f && std::fabs(r.filtered.y[j]) <= p.corridor_half_w)
            best = std::min(best, r.filtered.x[j]);
    }
    mark(6);
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
