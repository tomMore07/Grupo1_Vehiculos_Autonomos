package g1.percepcion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lector mínimo de objetos JSON planos ({"clave": valor, ...}) para los contratos del proyecto. */
public final class JsonPlano {
    private static final Pattern PAR = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"[^\"]*\"|[^,}\\s]+)");

    private JsonPlano() { }

    /** Devuelve un mapa de solo lectura clave → valor (texto sin comillas). */
    public static Map<String, String> parsear(String linea) {
        String s = linea.trim();
        if (!s.startsWith("{") || !s.endsWith("}"))
            throw new PercepcionException("JSON plano inválido: " + linea);
        Map<String, String> m = new LinkedHashMap<>();
        Matcher mt = PAR.matcher(s);
        while (mt.find()) {
            String v = mt.group(2);
            if (v.startsWith("\"")) v = v.substring(1, v.length() - 1);
            m.put(mt.group(1), v);
        }
        return Collections.unmodifiableMap(m);
    }

    public static double doble(Map<String, String> m, String clave) {
        String v = m.get(clave);
        if (v == null) throw new PercepcionException("Falta la clave '" + clave + "'");
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new PercepcionException("Valor no numérico en '" + clave + "': " + v, e);
        }
    }
}
