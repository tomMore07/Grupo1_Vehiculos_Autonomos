package g1.riesgo;

import g1.dominio.EstadoVehiculo;
import g1.dominio.NivelRiesgo;
import g1.dominio.Obstaculo;

/** Patrón Strategy: política intercambiable de evaluación de riesgo. */
public interface EstrategiaEvaluacionRiesgo {
    NivelRiesgo evaluar(Obstaculo obs, EstadoVehiculo est);
}
