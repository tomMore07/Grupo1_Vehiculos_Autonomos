package g1.actuadores;

public final class FrenoRegenerativo extends ActuadorFrenado {
    private static final double CARGA_MAXIMA_ACEPTABLE = 0.95;
    private final double cargaBateria;          // 0..1
    private double torqueNm;

    public FrenoRegenerativo(double cargaBateria) {
        if (!(cargaBateria >= 0 && cargaBateria <= 1))
            throw new IllegalArgumentException("cargaBateria debe estar en [0,1]");
        this.cargaBateria = cargaBateria;
    }

    /** Una batería casi llena no puede absorber energía regenerada. */
    @Override protected boolean verificarSistema() { return cargaBateria <= CARGA_MAXIMA_ACEPTABLE; }

    @Override protected void aplicarPresion(double presion) { torqueNm = presion * 400.0; }

    public double getTorqueNm() { return torqueNm; }
}
