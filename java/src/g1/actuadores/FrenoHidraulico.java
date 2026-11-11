package g1.actuadores;

public final class FrenoHidraulico extends ActuadorFrenado {
    private static final double NIVEL_MINIMO = 0.20;
    private final double nivelLiquido;          // 0..1
    private double presionLineaBar;

    public FrenoHidraulico(double nivelLiquido) {
        if (!(nivelLiquido >= 0 && nivelLiquido <= 1))
            throw new IllegalArgumentException("nivelLiquido debe estar en [0,1]");
        this.nivelLiquido = nivelLiquido;
    }

    @Override protected boolean verificarSistema() { return nivelLiquido >= NIVEL_MINIMO; }

    @Override protected void aplicarPresion(double presion) { presionLineaBar = presion * 150.0; }

    public double getPresionLineaBar() { return presionLineaBar; }
}
