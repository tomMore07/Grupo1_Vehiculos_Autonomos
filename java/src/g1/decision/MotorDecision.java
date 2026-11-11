package g1.decision;

import g1.actuadores.ActuadorFrenado;
import g1.dominio.Anomalia;
import g1.dominio.EstadoVehiculo;
import g1.dominio.EventoTelemetria;
import g1.dominio.NivelRiesgo;
import g1.dominio.Obstaculo;
import g1.percepcion.ProveedorPercepcion;
import g1.riesgo.EstrategiaEvaluacionRiesgo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orquesta percepción → evaluación de riesgo (Strategy) → frenado (Template Method). */
public class MotorDecision {
    private EstrategiaEvaluacionRiesgo estrategia;
    private final ActuadorFrenado actuador;
    private final List<EventoTelemetria> historial = new ArrayList<>();

    public MotorDecision(EstrategiaEvaluacionRiesgo estrategia, ActuadorFrenado actuador) {
        this.estrategia = Objects.requireNonNull(estrategia, "estrategia");
        this.actuador = Objects.requireNonNull(actuador, "actuador");
    }

    public NivelRiesgo evaluarYActuar(Obstaculo obs, EstadoVehiculo est) {
        NivelRiesgo nivel = estrategia.evaluar(obs, est);
        String detalle = "obstáculo a " + String.format("%.2f", obs.distanciaM()) + " m";
        if (nivel == NivelRiesgo.CRITICO) {
            detalle += " → frenado de emergencia: " + actuador.ejecutarSecuenciaFrenado(1.0);
        } else if (nivel == NivelRiesgo.ALTO) {
            detalle += " → frenado preventivo: " + actuador.ejecutarSecuenciaFrenado(0.5);
        }
        registrar(nivel, detalle);
        return nivel;
    }

    /** Un ciclo completo: consulta la percepción, registra anomalías y decide. */
    public Optional<NivelRiesgo> procesarPercepcion(ProveedorPercepcion percepcion, EstadoVehiculo est) {
        Objects.requireNonNull(percepcion, "percepcion");
        for (Anomalia a : percepcion.anomaliasRecientes())
            registrar(NivelRiesgo.MEDIO, "anomalía de telemetría t=" + a.t() + " z=" + String.format("%.1f", a.zscore()));
        return percepcion.obstaculoMasCercano().map(o -> evaluarYActuar(o, est));
    }

    public void setEstrategia(EstrategiaEvaluacionRiesgo nueva) {
        this.estrategia = Objects.requireNonNull(nueva, "estrategia");
    }

    /** Vista de solo lectura del historial (no permite modificarlo desde fuera). */
    public List<EventoTelemetria> getHistorial() {
        return Collections.unmodifiableList(historial);
    }

    private void registrar(NivelRiesgo nivel, String detalle) {
        historial.add(new EventoTelemetria(System.currentTimeMillis(), nivel, detalle));
    }
}
