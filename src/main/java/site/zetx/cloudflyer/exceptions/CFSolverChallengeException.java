package site.zetx.cloudflyer.exceptions;

/**
 * Raised when challenge solving fails.
 */
public class CFSolverChallengeException extends CFSolverException {
    
    public CFSolverChallengeException(String message) {
        super(message);
    }
    
    public CFSolverChallengeException(String message, Throwable cause) {
        super(message, cause);
    }
}
