package g1.dominio;

/** Niveles de riesgo ordenados de menor a mayor severidad. */
public enum NivelRiesgo {
    BAJO, MEDIO, ALTO, CRITICO;

    public boolean esAlMenos(NivelRiesgo otro) {
        return compareTo(otro) >= 0;
    }
}
