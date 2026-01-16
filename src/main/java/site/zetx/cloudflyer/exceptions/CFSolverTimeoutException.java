package site.zetx.cloudflyer.exceptions;

/**
 * Raised when operation times out.
 */
public class CFSolverTimeoutException extends CFSolverException {
    
    public CFSolverTimeoutException(String message) {
        super(message);
    }
    
    public CFSolverTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
