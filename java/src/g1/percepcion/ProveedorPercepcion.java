package g1.percepcion;

import g1.dominio.Anomalia;
import g1.dominio.Obstaculo;
import java.util.List;
import java.util.Optional;

/** Interfaz objetivo del patrón Adapter (y abstracción para DIP): el dominio no conoce C++ ni Python. */
public interface ProveedorPercepcion {
    Optional<Obstaculo> obstaculoMasCercano();

    List<Anomalia> anomaliasRecientes();
}
