package site.zetx.cloudflyer.exceptions;

/**
 * Base exception for all CFSolver errors.
 */
public class CFSolverException extends Exception {
    
    public CFSolverException(String message) {
        super(message);
    }
    
    public CFSolverException(String message, Throwable cause) {
        super(message, cause);
    }
}
