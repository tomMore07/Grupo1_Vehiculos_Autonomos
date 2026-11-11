package g1.actuadores;

public record ResultadoFrenado(boolean exitoso, double presionAplicada) {
    public ResultadoFrenado {
        if (!(presionAplicada >= 0 && presionAplicada <= 1))
            throw new IllegalArgumentException("presionAplicada debe estar en [0,1]");
    }
}
