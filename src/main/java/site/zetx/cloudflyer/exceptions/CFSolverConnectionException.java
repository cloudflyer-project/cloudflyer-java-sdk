package site.zetx.cloudflyer.exceptions;

/**
 * Raised when connection to service fails.
 */
public class CFSolverConnectionException extends CFSolverException {
    
    public CFSolverConnectionException(String message) {
        super(message);
    }
    
    public CFSolverConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
