package javax.management;

/**
 * Stub for Android: JMX is not available. SSHD references JMException when
 * peeling exceptions; this minimal class satisfies resolution.
 */
public class JMException extends Exception {
    public JMException() {
        super();
    }

    public JMException(String message) {
        super(message);
    }
}
