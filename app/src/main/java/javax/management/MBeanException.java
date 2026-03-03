package javax.management;

/**
 * Stub for Android: JMX is not available. SSHD may reference MBeanException
 * when peeling exceptions; this minimal class satisfies resolution.
 */
public class MBeanException extends JMException {
    private final Exception targetException;

    public MBeanException(Exception e) {
        super(e != null ? e.getMessage() : null);
        this.targetException = e;
    }

    public MBeanException(Exception e, String message) {
        super(message);
        this.targetException = e;
    }

    public Exception getTargetException() {
        return targetException;
    }
}
