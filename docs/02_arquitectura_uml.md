classDiagram
    direction BT

    class SecuenciaFrenado {
        <<abstract>>
        # float distanciaUmbral
        + ejecutarSecuenciaFrenado(List~Point~ puntos) void
        # calcularTrayectoria(List~Point~ puntos)* float
        # aplicarPresionFrenos(float intensidad)* void
        + registrarTelemetria() void
    }

    class FrenadoEmergencia {
        - float factorCritico
        # float calcularTrayectoria(List~Point~ puntos)
        # aplicarPresionFrenos(float intensidad)
    }

    class FrenadoPreventivo {
        - float factorSuave
        # float calcularTrayectoria(List~Point~ puntos)
        # aplicarPresionFrenos(float intensidad)
    }

    class EstrategiaEvaluacionRiesgo {
        <<interface>>
        + evaluarRiesgo(List~Point~ puntos) float
    }

    class EvaluacionLineal {
        + evaluarRiesgo(List~Point~ puntos) float
    }

    class GestorTelemetria {
        - List~Point~ historialPuntos
        - EstrategiaEvaluacionRiesgo estrategia
        + GestorTelemetria(EstrategiaEvaluacionRiesgo estrategia)
        + actualizarLecturas(List~Point~ nuevasLecturas) void
        + getHistorialPuntos() List~Point~
    }

    SecuenciaFrenado <|-- FrenadoEmergencia : Generalización
    SecuenciaFrenado <|-- FrenadoPreventivo : Generalización
    EstrategiaEvaluacionRiesgo <|.. EvaluacionLineal : Implementación
    GestorTelemetria --> EstrategiaEvaluacionRiesgo : Usa (Strategy)
    GestorTelemetria --> SecuenciaFrenado : Delega frenado
