package g1.dominio;

import java.util.Objects;

/** Entrada inmutable del historial del motor. */
public record EventoTelemetria(NivelRiesgo nivel, String descripcion) {
    public EventoTelemetria {
        Objects.requireNonNull(nivel, "nivel");
        Objects.requireNonNull(descripcion, "descripcion");
    }
}
