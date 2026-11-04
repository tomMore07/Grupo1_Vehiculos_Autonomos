#include "lidar_kernel.hpp"
#include <algorithm>
#include <cmath>
#include <omp.h>

std::vector<Point> LidarKernel::filtrarPuntos(const std::vector<Point>& nubePuntos, float umbralDistancia) {
    std::vector<Point> puntosFiltrados;
    
    // Protección de memoria contigua y paralelismo con OpenMP
    #pragma omp parallel
    {
        std::vector<Point> puntosLocales;
        
        #pragma omp for nowait
        for (size_t i = 0; i < nubePuntos.size(); ++i) {
            float distancia = std::sqrt(nubePuntos[i].x * nubePuntos[i].x + 
                                        nubePuntos[i].y * nubePuntos[i].y);
            if (distancia >= umbralDistancia) {
                puntosLocales.push_back(nubePuntos[i]);
            }
        }
        
        #pragma omp critical
        {
            puntosFiltrados.insert(puntosFiltrados.end(), puntosLocales.begin(), puntosLocales.end());
        }
    }
    
    return puntosFiltrados;
}
