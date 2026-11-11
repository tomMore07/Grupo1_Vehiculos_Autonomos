package g1.riesgo;

import g1.dominio.EstadoVehiculo;
import g1.dominio.NivelRiesgo;
import g1.dominio.Obstaculo;
import java.util.Objects;

/** Riesgo según el tiempo a colisión (TTC = distancia / velocidad de cierre). */
public final class RiesgoPorTiempoColision implements EstrategiaEvaluacionRiesgo {
    private final double ttcCriticoS;
    private final double ttcAltoS;

    public RiesgoPorTiempoColision(double ttcCriticoS, double ttcAltoS) {
        if (!(ttcCriticoS > 0 && ttcAltoS > ttcCriticoS))
            throw new IllegalArgumentException("Se requiere 0 < critico < alto");
        this.ttcCriticoS = ttcCriticoS;
        this.ttcAltoS = ttcAltoS;
    }

    @Override
    public NivelRiesgo evaluar(Obstaculo obs, EstadoVehiculo est) {
        Objects.requireNonNull(obs, "obs");
        double cierre = obs.velocidadRelativaMs();   // > 0 cuando se aproxima
        if (cierre <= 0) return NivelRiesgo.BAJO;
        double ttc = obs.distanciaM() / cierre;
        if (ttc <= ttcCriticoS) return NivelRiesgo.CRITICO;
        if (ttc <= ttcAltoS) return NivelRiesgo.ALTO;
        if (ttc <= 2 * ttcAltoS) return NivelRiesgo.MEDIO;
        return NivelRiesgo.BAJO;
    }
}
