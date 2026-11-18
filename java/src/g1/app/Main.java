package g1.app;

import g1.actuadores.FrenoHidraulico;
import g1.decision.MotorDecision;
import g1.dominio.EventoTelemetria;
import g1.dominio.NivelRiesgo;
import g1.percepcion.AdaptadorKernelCpp;
import g1.riesgo.RiesgoPorTiempoColision;
import java.nio.file.Path;
import java.util.Optional;

/** Demo de integración multiparadigma: C++ (kernel) + Python (pipeline) + Java (decisión). */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        Path raiz = Path.of(args.length > 0 ? args[0] : ".");
        double velocidadMs = 15.0;
        var percepcion = new AdaptadorKernelCpp(raiz, 200_000, velocidadMs, 3);
        var motor = new MotorDecision(new RiesgoPorTiempoColision(1.5, 3.0), new FrenoHidraulico(0.8));

        Optional<NivelRiesgo> nivel = motor.procesarPercepcion(percepcion);
        System.out.println("Nivel de riesgo: " + nivel.map(Enum::name).orElse("SIN OBSTÁCULO"));
        System.out.println("Historial de eventos:");
        for (EventoTelemetria e : motor.getHistorial())
            System.out.println("  [" + e.nivel() + "] " + e.descripcion());
    }
}
