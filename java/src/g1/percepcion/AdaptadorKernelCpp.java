package g1.percepcion;

import g1.dominio.Anomalia;
import g1.dominio.Obstaculo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patrón Adapter: traduce la salida JSON del kernel C++ y del pipeline Python a objetos del dominio.
 * Se comunica por procesos externos; si se migra a JNI solo cambia esta clase.
 */
public final class AdaptadorKernelCpp implements ProveedorPercepcion {
    private final Path raiz;
    private final int nPuntos;
    private final double velocidadVehiculoMs;
    private final int limiteAnomalias;

    public AdaptadorKernelCpp(Path raizProyecto, int nPuntos, double velocidadVehiculoMs, int limiteAnomalias) {
        if (nPuntos <= 0) throw new IllegalArgumentException("nPuntos debe ser > 0");
        if (limiteAnomalias <= 0) throw new IllegalArgumentException("limiteAnomalias debe ser > 0");
        this.raiz = raizProyecto;
        this.nPuntos = nPuntos;
        this.velocidadVehiculoMs = velocidadVehiculoMs;
        this.limiteAnomalias = limiteAnomalias;
    }

    @Override
    public Optional<Obstaculo> obstaculoMasCercano() {
        Path exe = raiz.resolve("cpp").resolve("lidar_kernel");
        if (!Files.isExecutable(exe))
            throw new IllegalStateException("No existe el kernel compilado: " + exe + " (ejecute 'make')");
        List<String> lineas = ejecutar(List.of(exe.toString(), "--n", String.valueOf(nPuntos), "--json"), raiz);
        if (lineas.isEmpty()) throw new IllegalStateException("El kernel no produjo JSON");
        double dist = leerCampo(lineas.get(0), "min_obstacle_dist");
        if (dist < 0) return Optional.empty();       // -1: sin obstáculo en el corredor
        // El kernel solo ve geometría estática: el obstáculo se aproxima a la velocidad del vehículo.
        return Optional.of(new Obstaculo(dist, velocidadVehiculoMs));
    }

    @Override
    public List<Anomalia> anomaliasRecientes() {
        List<String> lineas = ejecutar(
            List.of("python3", "pipeline.py", "--json", "--limit", String.valueOf(limiteAnomalias)),
            raiz.resolve("python"));
        List<Anomalia> res = new ArrayList<>();
        for (String l : lineas)
            res.add(new Anomalia((long) leerCampo(l, "t"), leerCampo(l, "value"), leerCampo(l, "zscore")));
        return List.copyOf(res);                      // copia inmutable
    }

    /** Lee un campo numérico de un objeto JSON plano, p. ej. {"t": 97, "zscore": -3.2}. */
    public static double leerCampo(String json, String clave) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(clave) + "\"\\s*:\\s*(-?[0-9.eE+-]+)").matcher(json);
        if (!m.find()) throw new IllegalArgumentException("Falta el campo '" + clave + "' en: " + json);
        return Double.parseDouble(m.group(1));
    }

    private static List<String> ejecutar(List<String> cmd, Path dir) {
        try {
            Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
            List<String> json = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) if (l.trim().startsWith("{")) json.add(l);
            }
            if (!p.waitFor(60, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("Tiempo agotado: " + cmd); }
            if (p.exitValue() != 0) throw new IllegalStateException("Código de salida " + p.exitValue() + ": " + cmd);
            return json;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo ejecutar " + cmd, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido", e);
        }
    }
}
