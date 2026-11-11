package g1;

import g1.actuadores.*;
import g1.decision.MotorDecision;
import g1.dominio.*;
import g1.percepcion.*;
import g1.riesgo.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Ejecutor de pruebas sin dependencias externas. Ejecutar desde la raíz del proyecto. */
public final class Pruebas {
    private static int ok = 0, fallos = 0;

    private static void prueba(String nombre, Runnable r) {
        try { r.run(); ok++; System.out.println("  OK     " + nombre); }
        catch (Throwable t) { fallos++; System.out.println("  FALLO  " + nombre + " -> " + t); }
    }
    private static void afirmar(boolean c, String m) { if (!c) throw new AssertionError(m); }
    private static <T extends Throwable> void lanza(Class<T> tipo, Runnable r) {
        try { r.run(); } catch (Throwable t) { if (tipo.isInstance(t)) return; throw new AssertionError("Se esperaba " + tipo.getSimpleName() + " y llegó " + t); }
        throw new AssertionError("Se esperaba " + tipo.getSimpleName());
    }
    private static boolean todosFinal(Class<?> c) {
        return Arrays.stream(c.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()))
                     .allMatch(f -> Modifier.isFinal(f.getModifiers()));
    }

    private static final EstadoVehiculo VEH = new EstadoVehiculo(15.0, 8.0);

    public static void main(String[] args) {
        System.out.println("== Inmutabilidad y aliasing ==");
        prueba("records del dominio tienen solo campos final", () -> {
            for (Class<?> c : List.of(Obstaculo.class, EstadoVehiculo.class, EventoTelemetria.class, Anomalia.class, ResultadoFrenado.class))
                afirmar(todosFinal(c), c.getSimpleName() + " tiene campos no final");
        });
        prueba("Obstaculo rechaza invariantes inválidos", () -> {
            lanza(IllegalArgumentException.class, () -> new Obstaculo(-1, 0, 0));
            lanza(IllegalArgumentException.class, () -> new Obstaculo(Double.NaN, 0, 0));
        });
        prueba("getHistorial() no permite modificar desde fuera", () -> {
            var m = new MotorDecision(new RiesgoPorDistancia(5, 10), new FrenoHidraulico(1));
            m.evaluarYActuar(new Obstaculo(50, 10, 0), VEH);
            lanza(UnsupportedOperationException.class, () -> m.getHistorial().add(new EventoTelemetria(0, NivelRiesgo.BAJO, "x")));
            lanza(UnsupportedOperationException.class, () -> m.getHistorial().clear());
        });
        prueba("unmodifiableList es una VISTA: una copia (List.copyOf) congela el instante", () -> {
            var m = new MotorDecision(new RiesgoPorDistancia(5, 10), new FrenoHidraulico(1));
            m.evaluarYActuar(new Obstaculo(50, 10, 0), VEH);
            List<EventoTelemetria> vista = m.getHistorial(), copia = List.copyOf(vista);
            m.evaluarYActuar(new Obstaculo(60, 10, 0), VEH);
            afirmar(vista.size() == 2 && copia.size() == 1, "vista=" + vista.size() + " copia=" + copia.size());
        });
        prueba("bitácora del actuador es de solo lectura", () -> {
            var f = new FrenoHidraulico(1);
            f.ejecutarSecuenciaFrenado(0.5);
            lanza(UnsupportedOperationException.class, () -> f.getBitacora().add("hack"));
        });
        prueba("JsonPlano devuelve mapa inmodificable", () ->
            lanza(UnsupportedOperationException.class, () -> JsonPlano.parsear("{\"a\":1}").put("b", "2")));
        prueba("el motor rechaza dependencias nulas", () -> {
            lanza(NullPointerException.class, () -> new MotorDecision(null, new FrenoHidraulico(1)));
            lanza(NullPointerException.class, () -> new MotorDecision(new RiesgoPorDistancia(5, 10), null));
        });

        System.out.println("== Template Method ==");
        prueba("ejecutarSecuenciaFrenado es final", () -> {
            try {
                Method m = ActuadorFrenado.class.getMethod("ejecutarSecuenciaFrenado", double.class);
                afirmar(Modifier.isFinal(m.getModifiers()), "no es final");
            } catch (NoSuchMethodException e) { throw new AssertionError(e); }
        });
        prueba("orden de pasos: verificar → aplicar", () -> {
            List<String> orden = new ArrayList<>();
            new ActuadorFrenado() {
                @Override protected boolean verificarSistema() { orden.add("verificar"); return true; }
                @Override protected void aplicarPresion(double p) { orden.add("aplicar"); }
            }.ejecutarSecuenciaFrenado(1.0);
            afirmar(orden.equals(List.of("verificar", "aplicar")), orden.toString());
        });
        prueba("si verificar falla, no se aplica presión", () -> {
            boolean[] aplicado = {false};
            var r = new ActuadorFrenado() {
                @Override protected boolean verificarSistema() { return false; }
                @Override protected void aplicarPresion(double p) { aplicado[0] = true; }
            }.ejecutarSecuenciaFrenado(1.0);
            afirmar(!aplicado[0] && !r.exitoso(), "se aplicó presión con verificación fallida");
        });
        prueba("presión fuera de [0,1] es rechazada", () ->
            lanza(IllegalArgumentException.class, () -> new FrenoHidraulico(1).ejecutarSecuenciaFrenado(1.5)));

        System.out.println("== LSP (subclases sustituibles) ==");
        prueba("cualquier ActuadorFrenado cumple el contrato del resultado", () -> {
            for (ActuadorFrenado a : List.of(new FrenoHidraulico(0.9), new FrenoRegenerativo(0.3))) {
                var r = a.ejecutarSecuenciaFrenado(0.7);
                afirmar(r.exitoso() && r.presionAplicada() == 0.7, a.getClass().getSimpleName());
            }
        });
        prueba("fallos específicos de cada freno (líquido bajo / batería llena)", () -> {
            afirmar(!new FrenoHidraulico(0.05).ejecutarSecuenciaFrenado(1).exitoso(), "hidráulico");
            afirmar(!new FrenoRegenerativo(0.99).ejecutarSecuenciaFrenado(1).exitoso(), "regenerativo");
        });

        System.out.println("== Strategy y MotorDecision ==");
        prueba("RiesgoPorDistancia: umbrales", () -> {
            var e = new RiesgoPorDistancia(5, 10);
            afirmar(e.evaluar(new Obstaculo(4, 0, 0), VEH) == NivelRiesgo.CRITICO, "critico");
            afirmar(e.evaluar(new Obstaculo(8, 0, 0), VEH) == NivelRiesgo.ALTO, "alto");
            afirmar(e.evaluar(new Obstaculo(15, 0, 0), VEH) == NivelRiesgo.MEDIO, "medio");
            afirmar(e.evaluar(new Obstaculo(40, 0, 0), VEH) == NivelRiesgo.BAJO, "bajo");
        });
        prueba("RiesgoPorTiempoColision: TTC y velocidad de cierre", () -> {
            var e = new RiesgoPorTiempoColision(1.5, 3.0);
            afirmar(e.evaluar(new Obstaculo(15, 15, 0), VEH) == NivelRiesgo.CRITICO, "ttc=1s");
            afirmar(e.evaluar(new Obstaculo(40, 15, 0), VEH) == NivelRiesgo.ALTO, "ttc=2.7s");
            afirmar(e.evaluar(new Obstaculo(5, -3, 0), VEH) == NivelRiesgo.BAJO, "se aleja");
        });
        prueba("cambiar la estrategia cambia la decisión (mismo obstáculo)", () -> {
            var obs = new Obstaculo(40, 15, 0);
            var m = new MotorDecision(new RiesgoPorDistancia(5, 10), new FrenoHidraulico(1));
            afirmar(m.evaluarYActuar(obs, VEH) == NivelRiesgo.BAJO, "por distancia");
            m.setEstrategia(new RiesgoPorTiempoColision(1.5, 3.0));
            afirmar(m.evaluarYActuar(obs, VEH) == NivelRiesgo.ALTO, "por TTC");
        });
        prueba("riesgo crítico dispara el frenado y queda en el historial", () -> {
            var f = new FrenoHidraulico(1);
            var m = new MotorDecision(new RiesgoPorDistancia(5, 10), f);
            m.evaluarYActuar(new Obstaculo(3, 10, 0), VEH);
            afirmar(f.getPresionLineaBar() == 150.0, "presión de línea");
            afirmar(m.getHistorial().get(0).nivel() == NivelRiesgo.CRITICO, "historial");
        });

        System.out.println("== Contratos JSON ==");
        prueba("parseo del JSON del kernel C++", () -> {
            var m = JsonPlano.parsear("{\"n_in\":100000,\"min_obstacle_dist\":8.528,\"emergency_brake\":true}");
            afirmar(JsonPlano.doble(m, "min_obstacle_dist") == 8.528, "distancia");
            afirmar("true".equals(m.get("emergency_brake")), "bandera");
        });
        prueba("parseo del JSON del pipeline Python", () -> {
            var m = JsonPlano.parsear("{\"t\": 97, \"value\": 13.483, \"mean\": 24.962, \"zscore\": -32.173}");
            afirmar(JsonPlano.doble(m, "zscore") == -32.173, "zscore");
        });
        prueba("JSON inválido lanza PercepcionException", () ->
            lanza(PercepcionException.class, () -> JsonPlano.parsear("no es json")));

        System.out.println("== Integración real C++ + Python + Java ==");
        Path raiz = Path.of(".");
        if (Files.isExecutable(raiz.resolve("cpp/lidar_kernel"))) {
            prueba("el adaptador lee el kernel C++ y el pipeline Python", () -> {
                var p = new AdaptadorKernelCpp(raiz, 100_000, 15.0, 3);
                Optional<Obstaculo> o = p.obstaculoMasCercano();
                afirmar(o.isPresent() && o.get().distanciaM() > 0, "obstáculo");
                List<Anomalia> an = p.anomaliasRecientes();
                afirmar(an.size() == 3, "anomalías=" + an.size());
                lanza(UnsupportedOperationException.class, () -> an.add(null));
            });
            prueba("ciclo completo: percepción → decisión → frenado", () -> {
                var f = new FrenoHidraulico(0.8);
                var m = new MotorDecision(new RiesgoPorTiempoColision(1.5, 3.0), f);
                var nivel = m.procesarPercepcion(new AdaptadorKernelCpp(raiz, 100_000, 15.0, 2), VEH);
                afirmar(nivel.isPresent() && nivel.get() == NivelRiesgo.CRITICO, "nivel=" + nivel);
                afirmar(f.getPresionLineaBar() == 150.0, "no frenó");
                afirmar(m.getHistorial().size() == 3, "eventos=" + m.getHistorial().size());
            });
        } else {
            System.out.println("  (omitidas: compile el kernel con 'make')");
        }

        System.out.println("\nResultado: " + ok + " OK, " + fallos + " con fallo");
        if (fallos > 0) System.exit(1);
    }
}
