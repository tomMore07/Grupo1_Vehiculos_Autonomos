#ifndef LIDAR_KERNEL_HPP
#define LIDAR_KERNEL_HPP

#include <vector>

struct Point {
    float x, y, z;
    float intensity;
};

class LidarKernel {
public:
    // Kernel geoespacial optimizado en O(N log N) con OpenMP
    static std::vector<Point> filtrarPuntos(const std::vector<Point>& nubePuntos, float umbralDistancia);
};

#endif // LIDAR_KERNEL_HPP
