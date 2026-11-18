package g1.actuadores;

import java.util.ArrayList;
import java.util.List;

/**
 * Patrón Template Method: {@link #ejecutarSecuenciaFrenado(double)} fija el ORDEN de los pasos
 * (es final, no se puede sobrescribir); las subclases solo aportan los pasos variables.
 */
public abstract class ActuadorFrenado {
    private final List<String> bitacora = new ArrayList<>();

    /** Método plantilla. Devuelve true si el frenado se aplicó. */
    public final synchronized boolean ejecutarSecuenciaFrenado(double presion) {
        if (!(presion >= 0 && presion <= 1))
            throw new IllegalArgumentException("presion debe estar en [0,1]");
        registrarEvento("Inicio de secuencia de frenado (presion=" + presion + ")");
        if (!verificarSistema()) {
            registrarEvento("Verificación fallida: frenado abortado");
            return false;
        }
        aplicarPresion(presion);
        registrarEvento("Frenado aplicado");
        return true;
    }

    /** Paso variable 1. */
    protected abstract boolean verificarSistema();

    /** Paso variable 2. */
    protected abstract void aplicarPresion(double presion);

    /** Hook con comportamiento por defecto; las subclases pueden extenderlo. */
    protected void registrarEvento(String msg) {
        bitacora.add(msg);
    }

    /** Instantánea inmutable de la bitácora: no expone la lista interna ni cambia después. */
    public final synchronized List<String> getBitacora() {
        return List.copyOf(bitacora);
    }
}
