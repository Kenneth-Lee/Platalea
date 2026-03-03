package javax.management;

/**
 * Stub for Android: JMX is not available. SSHD uses this in
 * GenericUtils.peelException() for instanceof check; this minimal class
 * satisfies class resolution.
 */
public class ReflectionException extends JMException {
    private final Exception targetException;

    public ReflectionException(Exception e) {
        super(e != null ? e.getMessage() : null);
        this.targetException = e;
    }

    public ReflectionException(Exception e, String message) {
        super(message);
        this.targetException = e;
    }

    public Exception getTargetException() {
        return targetException;
    }
}
