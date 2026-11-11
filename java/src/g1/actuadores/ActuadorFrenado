package g1.actuadores;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Patrón Template Method: {@link #ejecutarSecuenciaFrenado(double)} fija el ORDEN de los pasos
 * (final, no sobrescribible); las subclases solo aportan los pasos variables.
 */
public abstract class ActuadorFrenado {
    private final List<String> bitacora = new ArrayList<>();

    /** Método plantilla. */
    public final ResultadoFrenado ejecutarSecuenciaFrenado(double presion) {
        if (!(presion >= 0 && presion <= 1))
            throw new IllegalArgumentException("presion debe estar en [0,1]");
        registrarEvento("Inicio de secuencia de frenado (presion=" + presion + ")");
        if (!verificarSistema()) {
            registrarEvento("Verificación fallida: frenado abortado");
            return new ResultadoFrenado(false, 0.0);
        }
        aplicarPresion(presion);
        registrarEvento("Frenado aplicado");
        return new ResultadoFrenado(true, presion);
    }

    /** Paso variable 1. */
    protected abstract boolean verificarSistema();

    /** Paso variable 2. */
    protected abstract void aplicarPresion(double presion);

    /** Hook con comportamiento por defecto; las subclases pueden extenderlo. */
    protected void registrarEvento(String msg) {
        bitacora.add(msg);
    }

    /** Vista de solo lectura: el estado interno no se filtra (sin aliasing). */
    public List<String> getBitacora() {
        return Collections.unmodifiableList(bitacora);
    }
}
