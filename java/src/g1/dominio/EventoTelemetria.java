package g1.dominio;

import java.util.Objects;

public record EventoTelemetria(long timestampMs, NivelRiesgo nivel, String descripcion) {
    public EventoTelemetria {
        Objects.requireNonNull(nivel, "nivel");
        Objects.requireNonNull(descripcion, "descripcion");
    }
}
