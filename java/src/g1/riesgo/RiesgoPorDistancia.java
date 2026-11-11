package g1.riesgo;

import g1.dominio.EstadoVehiculo;
import g1.dominio.NivelRiesgo;
import g1.dominio.Obstaculo;
import java.util.Objects;

/** Riesgo según la distancia al obstáculo (umbrales fijos). */
public final class RiesgoPorDistancia implements EstrategiaEvaluacionRiesgo {
    private final double umbralCriticoM;
    private final double umbralAltoM;

    public RiesgoPorDistancia(double umbralCriticoM, double umbralAltoM) {
        if (!(umbralCriticoM > 0 && umbralAltoM > umbralCriticoM))
            throw new IllegalArgumentException("Se requiere 0 < critico < alto");
        this.umbralCriticoM = umbralCriticoM;
        this.umbralAltoM = umbralAltoM;
    }

    @Override
    public NivelRiesgo evaluar(Obstaculo obs, EstadoVehiculo est) {
        Objects.requireNonNull(obs, "obs");
        double d = obs.distanciaM();
        if (d <= umbralCriticoM) return NivelRiesgo.CRITICO;
        if (d <= umbralAltoM) return NivelRiesgo.ALTO;
        if (d <= 2 * umbralAltoM) return NivelRiesgo.MEDIO;
        return NivelRiesgo.BAJO;
    }
}
