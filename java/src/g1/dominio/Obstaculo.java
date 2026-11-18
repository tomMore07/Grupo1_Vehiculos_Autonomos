package g1.dominio;

/** Obstáculo detectado. Inmutable: los invariantes se validan una sola vez, al construir. */
public record Obstaculo(double distanciaM, double velocidadRelativaMs) {
    public Obstaculo {
        if (!Double.isFinite(distanciaM) || distanciaM < 0)
            throw new IllegalArgumentException("distanciaM debe ser finita y >= 0");
        if (!Double.isFinite(velocidadRelativaMs))
            throw new IllegalArgumentException("velocidadRelativaMs debe ser finita");
    }
}
