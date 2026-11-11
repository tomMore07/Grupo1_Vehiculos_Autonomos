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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador: traduce la salida JSON del kernel C++ y del pipeline Python a objetos del dominio.
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
            throw new PercepcionException("No existe el kernel compilado: " + exe + " (ejecute 'make')");
        List<String> lineas = ejecutar(List.of(exe.toString(), "--n", String.valueOf(nPuntos), "--json"), raiz);
        if (lineas.isEmpty()) throw new PercepcionException("El kernel no produjo JSON");
        Map<String, String> r = JsonPlano.parsear(lineas.get(0));
        double dist = JsonPlano.doble(r, "min_obstacle_dist");
        if (dist < 0) return Optional.empty();       // -1: sin obstáculo en el corredor
        // El kernel solo ve geometría estática: el obstáculo se aproxima a la velocidad del vehículo.
        return Optional.of(new Obstaculo(dist, velocidadVehiculoMs, 0.0));
    }

    @Override
    public List<Anomalia> anomaliasRecientes() {
        Path dir = raiz.resolve("python");
        List<String> lineas = ejecutar(
            List.of("python3", "pipeline.py", "--json", "--limit", String.valueOf(limiteAnomalias)), dir);
        List<Anomalia> res = new ArrayList<>();
        for (String l : lineas) {
            Map<String, String> m = JsonPlano.parsear(l);
            res.add(new Anomalia((long) JsonPlano.doble(m, "t"), JsonPlano.doble(m, "value"),
                                 JsonPlano.doble(m, "mean"), JsonPlano.doble(m, "zscore")));
        }
        return List.copyOf(res);                      // copia inmutable
    }

    private static List<String> ejecutar(List<String> cmd, Path dir) {
        try {
            Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
            List<String> json = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) if (l.trim().startsWith("{")) json.add(l);
            }
            if (!p.waitFor(60, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new PercepcionException("Tiempo agotado: " + cmd); }
            if (p.exitValue() != 0) throw new PercepcionException("Código de salida " + p.exitValue() + ": " + cmd);
            return json;
        } catch (IOException e) {
            throw new PercepcionException("No se pudo ejecutar " + cmd, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PercepcionException("Interrumpido", e);
        }
    }
}
