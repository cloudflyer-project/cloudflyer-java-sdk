package site.zetx.cloudflyer.exceptions;

/**
 * Raised when API request fails.
 */
public class CFSolverAPIException extends CFSolverException {
    
    public CFSolverAPIException(String message) {
        super(message);
    }
    
    public CFSolverAPIException(String message, Throwable cause) {
        super(message, cause);
    }
}
