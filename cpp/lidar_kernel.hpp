// Módulo 1 (Imperativo) - Kernel geoespacial de filtrado de nubes de puntos LIDAR
// Estructura SoA (Structure of Arrays) sobre memoria contigua (std::vector).
#pragma once
#include <cstddef>
#include <cstdint>
#include <vector>

namespace lidar {

struct PointCloud {
    std::vector<float> x, y, z, intensity;
    std::size_t size() const { return x.size(); }
    void reserve(std::size_t n) { x.reserve(n); y.reserve(n); z.reserve(n); intensity.reserve(n); }
    void push(float px, float py, float pz, float pi) {
        x.push_back(px); y.push_back(py); z.push_back(pz); intensity.push_back(pi);
    }
};

struct Params {
    float voxel_size       = 0.20f;  // m, lado del vóxel
    float ground_z         = -1.50f; // m, altura del suelo respecto al sensor
    float ground_margin    = 0.15f;  // m, tolerancia sobre el suelo
    float max_range        = 60.0f;  // m, alcance útil
    int   min_pts_voxel    = 3;      // vóxeles con menos puntos = ruido
    float corridor_half_w  = 1.5f;   // m, semiancho del corredor de marcha
    float brake_distance   = 12.0f;  // m, umbral de frenado de emergencia
};

struct Result {
    PointCloud filtered;             // centroides de vóxeles válidos
    std::size_t n_in = 0, n_ground = 0, n_out_range = 0, n_noise_pts = 0;
    std::size_t n_voxels = 0;
    float min_obstacle_dist = -1.0f; // -1 = sin obstáculo en el corredor
    bool  emergency_brake = false;
};

// parallel=false -> versión secuencial; true -> OpenMP. Ambas producen resultados idénticos.
Result process(const PointCloud& in, const Params& p, bool parallel);

PointCloud synthetic_cloud(std::size_t n, std::uint32_t seed);

} // namespace lidar
