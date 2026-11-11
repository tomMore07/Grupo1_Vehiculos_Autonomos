package g1.percepcion;

public class PercepcionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PercepcionException(String msg) { super(msg); }
    public PercepcionException(String msg, Throwable causa) { super(msg, causa); }
}
