package g1.dominio;

/** Obstáculo detectado. Inmutable: los invariantes se validan una sola vez, al construir. */
public record Obstaculo(double distanciaM, double velocidadRelativaMs, double anchoLateralM) {
    public Obstaculo {
        if (!Double.isFinite(distanciaM) || distanciaM < 0)
            throw new IllegalArgumentException("distanciaM debe ser finita y >= 0");
        if (!Double.isFinite(velocidadRelativaMs))
            throw new IllegalArgumentException("velocidadRelativaMs debe ser finita");
        if (!Double.isFinite(anchoLateralM) || anchoLateralM < 0)
            throw new IllegalArgumentException("anchoLateralM debe ser finito y >= 0");
    }
}
