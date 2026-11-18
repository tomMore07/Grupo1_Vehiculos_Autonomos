package g1.decision;

import g1.actuadores.ActuadorFrenado;
import g1.dominio.Anomalia;
import g1.dominio.EventoTelemetria;
import g1.dominio.NivelRiesgo;
import g1.dominio.Obstaculo;
import g1.percepcion.ProveedorPercepcion;
import g1.riesgo.EstrategiaEvaluacionRiesgo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Orquesta percepción → evaluación de riesgo (Strategy) → frenado (Template Method).
 * Encapsulamiento: el historial es privado y solo se entrega como instantánea inmutable.
 * Los métodos son synchronized: el motor puede usarse desde varios hilos sin corromper el historial.
 */
public class MotorDecision {
    private EstrategiaEvaluacionRiesgo estrategia;
    private final ActuadorFrenado actuador;
    private final List<EventoTelemetria> historial = new ArrayList<>();

    public MotorDecision(EstrategiaEvaluacionRiesgo estrategia, ActuadorFrenado actuador) {
        this.estrategia = Objects.requireNonNull(estrategia, "estrategia");
        this.actuador = Objects.requireNonNull(actuador, "actuador");
    }

    public synchronized NivelRiesgo evaluarYActuar(Obstaculo obs) {
        NivelRiesgo nivel = estrategia.evaluar(obs);
        String detalle = "obstáculo a " + String.format("%.2f", obs.distanciaM()) + " m";
        if (nivel == NivelRiesgo.CRITICO) {
            detalle += frenar(1.0, "emergencia");
        } else if (nivel == NivelRiesgo.ALTO) {
            detalle += frenar(0.5, "preventivo");
        }
        historial.add(new EventoTelemetria(nivel, detalle));
        return nivel;
    }

    /** Un ciclo completo: consulta la percepción, registra las anomalías y decide. */
    public synchronized Optional<NivelRiesgo> procesarPercepcion(ProveedorPercepcion percepcion) {
        Objects.requireNonNull(percepcion, "percepcion");
        for (Anomalia a : percepcion.anomaliasRecientes())
            historial.add(new EventoTelemetria(NivelRiesgo.MEDIO,
                "anomalía de telemetría t=" + a.t() + " z=" + String.format("%.1f", a.zscore())));
        return percepcion.obstaculoMasCercano().map(this::evaluarYActuar);
    }

    public synchronized void setEstrategia(EstrategiaEvaluacionRiesgo nueva) {
        this.estrategia = Objects.requireNonNull(nueva, "estrategia");
    }

    /**
     * Instantánea inmutable del historial en este instante. Es una COPIA (List.copyOf), no una vista:
     * los eventos que el motor agregue después no aparecen en ella, y recorrerla nunca lanza
     * ConcurrentModificationException aunque otro hilo siga registrando eventos.
     */
    public synchronized List<EventoTelemetria> getHistorial() {
        return List.copyOf(historial);
    }

    private String frenar(double presion, String tipo) {
        return actuador.ejecutarSecuenciaFrenado(presion)
            ? " → frenado " + tipo + " aplicado"
            : " → frenado " + tipo + " FALLÓ (verificación del sistema)";
    }
}
