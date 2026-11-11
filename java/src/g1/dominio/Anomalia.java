package g1.dominio;

/** Anomalía detectada por el pipeline funcional (Python). */
public record Anomalia(long t, double valor, double media, double zscore) { }
