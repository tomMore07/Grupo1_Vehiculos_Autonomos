# Proyecto Final Integrador - Grupo 1
## Detección de Anomalías e Inferencia en Tiempo Real para Vehículos Autónomos

Repositorio oficial del equipo para la asignatura **SIST0176 - Paradigmas de Programación**. El sistema implementa una plataforma a bordo capaz de procesar telemetría masiva de sensores en milisegundos mediante la integración de tres paradigmas: Imperativo, Orientado a Objetos y Funcional.

---

## 🏗️ Arquitectura Multiparadigma

1. **Módulo Imperativo (C++17):** Kernel geoespacial para filtrado de nubes de puntos LIDAR $O(N \log N)$ optimizado con memoria contigua y paralelismo en CPU mediante **OpenMP**.
2. **Módulo Orientado a Objetos (Java 17+):** Motor de actuadores bajo principios **SOLID**, patrones GoF (*Template Method* y *Strategy*) y prevención de *aliasing* con colecciones inmutables.
3. **Módulo Funcional (Python 3.10+):** Pipeline declarativo puro con funciones de orden superior (`map`, `filter`, `reduce`), inmutabilidad y evaluación perezosa para promedios móviles y detección de anomalías.

---

## 🚀 Instrucciones de Compilación y Ejecución

### Módulo Imperativo (C++)
\`\`\`bash
cd modulo-imperativo-cpp
mkdir build && cd build
cmake ..
make
./kernel_lidar
\`\`\`

### Módulo Orientado a Objetos (Java)
\`\`\`bash
cd modulo-poo-java
mvn clean compile
mvn test
\`\`\`

### Módulo Funcional (Python)
\`\`\`bash
cd modulo-funcional-py
pip install -r requirements.txt
python src/main.py
\`\`\`

---
**Universidad Sergio Arboleda**  
Escuela de Ciencias Exactas e Ingeniería  
Ingeniería en Ciencias de la Computación e Inteligencia Artificial (ICCIA)
