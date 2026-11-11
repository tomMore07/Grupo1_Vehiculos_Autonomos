package g1.dominio;

public record EstadoVehiculo(double velocidadMs, double desaceleracionMaxMs2) {
    public EstadoVehiculo {
        if (!Double.isFinite(velocidadMs) || velocidadMs < 0)
            throw new IllegalArgumentException("velocidadMs debe ser finita y >= 0");
        if (!Double.isFinite(desaceleracionMaxMs2) || desaceleracionMaxMs2 <= 0)
            throw new IllegalArgumentException("desaceleracionMaxMs2 debe ser > 0");
    }
}
