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
import java.util.concurrent.atomic.AtomicReference;

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
    private static MotorDecision motorPorDistancia() {
        return new MotorDecision(new RiesgoPorDistancia(5, 10), new FrenoHidraulico(1));
    }

    private static final Obstaculo LEJOS = new Obstaculo(50, 10);

    public static void main(String[] args) {
        System.out.println("== Inmutabilidad y aliasing ==");
        prueba("records del dominio tienen solo campos final", () -> {
            for (Class<?> c : List.of(Obstaculo.class, EventoTelemetria.class, Anomalia.class))
                afirmar(todosFinal(c), c.getSimpleName() + " tiene campos no final");
        });
        prueba("Obstaculo rechaza invariantes inválidos", () -> {
            lanza(IllegalArgumentException.class, () -> new Obstaculo(-1, 0));
            lanza(IllegalArgumentException.class, () -> new Obstaculo(Double.NaN, 0));
        });
        prueba("getHistorial() devuelve una lista inmutable", () -> {
            var m = motorPorDistancia();
            m.evaluarYActuar(LEJOS);
            lanza(UnsupportedOperationException.class, () -> m.getHistorial().add(new EventoTelemetria(NivelRiesgo.BAJO, "x")));
            lanza(UnsupportedOperationException.class, () -> m.getHistorial().clear());
        });
        prueba("getHistorial() es una INSTANTÁNEA: los eventos nuevos no la modifican", () -> {
            var m = motorPorDistancia();
            m.evaluarYActuar(LEJOS);
            List<EventoTelemetria> foto = m.getHistorial();
            m.evaluarYActuar(LEJOS);
            m.evaluarYActuar(LEJOS);
            afirmar(foto.size() == 1, "la instantánea cambió: " + foto.size());
            afirmar(m.getHistorial().size() == 3, "el historial real debe tener 3 eventos");
        });
        prueba("recorrer el historial mientras el motor agrega eventos NO lanza ConcurrentModificationException", () -> {
            var m = motorPorDistancia();
            m.evaluarYActuar(LEJOS); m.evaluarYActuar(LEJOS);
            for (EventoTelemetria e : m.getHistorial()) m.evaluarYActuar(LEJOS);   // agrega mientras recorre
            afirmar(m.getHistorial().size() == 4, "eventos=" + m.getHistorial().size());
        });
        prueba("demostración: una VISTA unmodifiableList sí lanza ConcurrentModificationException", () -> {
            List<Integer> interna = new ArrayList<>(List.of(1, 2, 3));
            List<Integer> vista = Collections.unmodifiableList(interna);   // el problema que se corrigió
            lanza(ConcurrentModificationException.class, () -> { for (Integer x : vista) interna.add(x); });
        });
        prueba("bitácora del actuador es una instantánea inmutable", () -> {
            var f = new FrenoHidraulico(1);
            f.ejecutarSecuenciaFrenado(0.5);
            List<String> foto = f.getBitacora();
            f.ejecutarSecuenciaFrenado(0.5);
            lanza(UnsupportedOperationException.class, () -> foto.add("hack"));
            afirmar(foto.size() == 2 && f.getBitacora().size() == 4, "foto=" + foto.size());
        });
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
            boolean exito = new ActuadorFrenado() {
                @Override protected boolean verificarSistema() { return false; }
                @Override protected void aplicarPresion(double p) { aplicado[0] = true; }
            }.ejecutarSecuenciaFrenado(1.0);
            afirmar(!aplicado[0] && !exito, "se aplicó presión con verificación fallida");
        });
        prueba("presión fuera de [0,1] es rechazada", () ->
            lanza(IllegalArgumentException.class, () -> new FrenoHidraulico(1).ejecutarSecuenciaFrenado(1.5)));

        System.out.println("== LSP (subclases sustituibles) ==");
        prueba("cualquier ActuadorFrenado cumple el mismo contrato", () -> {
            for (ActuadorFrenado a : List.of(new FrenoHidraulico(0.9), new FrenoRegenerativo(0.3)))
                afirmar(a.ejecutarSecuenciaFrenado(0.7), a.getClass().getSimpleName());
        });
        prueba("fallos específicos de cada freno (líquido bajo / batería llena)", () -> {
            afirmar(!new FrenoHidraulico(0.05).ejecutarSecuenciaFrenado(1), "hidráulico");
            afirmar(!new FrenoRegenerativo(0.99).ejecutarSecuenciaFrenado(1), "regenerativo");
        });

        System.out.println("== Strategy y MotorDecision ==");
        prueba("RiesgoPorDistancia: umbrales", () -> {
            var e = new RiesgoPorDistancia(5, 10);
            afirmar(e.evaluar(new Obstaculo(4, 0)) == NivelRiesgo.CRITICO, "critico");
            afirmar(e.evaluar(new Obstaculo(8, 0)) == NivelRiesgo.ALTO, "alto");
            afirmar(e.evaluar(new Obstaculo(15, 0)) == NivelRiesgo.MEDIO, "medio");
            afirmar(e.evaluar(new Obstaculo(40, 0)) == NivelRiesgo.BAJO, "bajo");
        });
        prueba("RiesgoPorTiempoColision: TTC y velocidad de cierre", () -> {
            var e = new RiesgoPorTiempoColision(1.5, 3.0);
            afirmar(e.evaluar(new Obstaculo(15, 15)) == NivelRiesgo.CRITICO, "ttc=1s");
            afirmar(e.evaluar(new Obstaculo(40, 15)) == NivelRiesgo.ALTO, "ttc=2.7s");
            afirmar(e.evaluar(new Obstaculo(5, -3)) == NivelRiesgo.BAJO, "se aleja");
        });
        prueba("cambiar la estrategia cambia la decisión (mismo obstáculo)", () -> {
            var obs = new Obstaculo(40, 15);
            var m = motorPorDistancia();
            afirmar(m.evaluarYActuar(obs) == NivelRiesgo.BAJO, "por distancia");
            m.setEstrategia(new RiesgoPorTiempoColision(1.5, 3.0));
            afirmar(m.evaluarYActuar(obs) == NivelRiesgo.ALTO, "por TTC");
        });
        prueba("riesgo crítico dispara el frenado y queda en el historial", () -> {
            var f = new FrenoHidraulico(1);
            var m = new MotorDecision(new RiesgoPorDistancia(5, 10), f);
            m.evaluarYActuar(new Obstaculo(3, 10));
            afirmar(f.getPresionLineaBar() == 150.0, "presión de línea");
            afirmar(m.getHistorial().get(0).nivel() == NivelRiesgo.CRITICO, "historial");
        });
        prueba("si el freno falla, el historial lo registra", () -> {
            var m = new MotorDecision(new RiesgoPorDistancia(5, 10), new FrenoHidraulico(0.05));
            m.evaluarYActuar(new Obstaculo(3, 10));
            afirmar(m.getHistorial().get(0).descripcion().contains("FALLÓ"), m.getHistorial().get(0).descripcion());
        });

        System.out.println("== Thread safety ==");
        prueba("8 hilos registrando eventos a la vez: no se pierde ninguno", () -> {
            var m = motorPorDistancia();
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread[] hilos = new Thread[8];
            for (int i = 0; i < hilos.length; i++) {
                hilos[i] = new Thread(() -> {
                    try { for (int k = 0; k < 250; k++) m.evaluarYActuar(LEJOS); }
                    catch (Throwable t) { error.set(t); }
                });
                hilos[i].start();
            }
            Thread lector = new Thread(() -> {       // mientras tanto, otro hilo recorre instantáneas
                try { for (int k = 0; k < 200; k++) for (EventoTelemetria e : m.getHistorial()) e.nivel(); }
                catch (Throwable t) { error.set(t); }
            });
            lector.start();
            try { for (Thread h : hilos) h.join(); lector.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            afirmar(error.get() == null, "excepción en un hilo: " + error.get());
            afirmar(m.getHistorial().size() == 2000, "eventos=" + m.getHistorial().size());
        });
        prueba("8 hilos frenando a la vez: la bitácora queda completa", () -> {
            var f = new FrenoHidraulico(1);
            Thread[] hilos = new Thread[8];
            for (int i = 0; i < hilos.length; i++) {
                hilos[i] = new Thread(() -> { for (int k = 0; k < 100; k++) f.ejecutarSecuenciaFrenado(0.5); });
                hilos[i].start();
            }
            try { for (Thread h : hilos) h.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            afirmar(f.getBitacora().size() == 1600, "entradas=" + f.getBitacora().size());   // 2 por frenado
        });

        System.out.println("== Contratos JSON ==");
        prueba("lectura del JSON del kernel C++", () -> {
            String j = "{\"n_in\":100000,\"min_obstacle_dist\":8.528,\"emergency_brake\":true}";
            afirmar(AdaptadorKernelCpp.leerCampo(j, "min_obstacle_dist") == 8.528, "distancia");
        });
        prueba("lectura del JSON del pipeline Python", () -> {
            String j = "{\"t\": 97, \"value\": 13.483, \"mean\": 24.962, \"zscore\": -32.173}";
            afirmar(AdaptadorKernelCpp.leerCampo(j, "zscore") == -32.173, "zscore");
            afirmar(AdaptadorKernelCpp.leerCampo(j, "t") == 97, "t");
        });
        prueba("campo ausente lanza IllegalArgumentException", () ->
            lanza(IllegalArgumentException.class, () -> AdaptadorKernelCpp.leerCampo("{\"a\":1}", "b")));

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
                var nivel = m.procesarPercepcion(new AdaptadorKernelCpp(raiz, 100_000, 15.0, 2));
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
