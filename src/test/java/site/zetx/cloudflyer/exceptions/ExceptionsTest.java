package site.zetx.cloudflyer.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for exception classes.
 */
class ExceptionsTest {

    @Test
    @DisplayName("CFSolverException should store message")
    void testCFSolverException() {
        CFSolverException ex = new CFSolverException("Test error");
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("CFSolverException should store cause")
    void testCFSolverExceptionWithCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        CFSolverException ex = new CFSolverException("Test error", cause);
        assertEquals("Test error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("CFSolverAPIException should extend CFSolverException")
    void testCFSolverAPIException() {
        CFSolverAPIException ex = new CFSolverAPIException("API error");
        assertInstanceOf(CFSolverException.class, ex);
        assertEquals("API error", ex.getMessage());
    }

    @Test
    @DisplayName("CFSolverChallengeException should extend CFSolverException")
    void testCFSolverChallengeException() {
        CFSolverChallengeException ex = new CFSolverChallengeException("Challenge failed");
        assertInstanceOf(CFSolverException.class, ex);
        assertEquals("Challenge failed", ex.getMessage());
    }

    @Test
    @DisplayName("CFSolverConnectionException should extend CFSolverException")
    void testCFSolverConnectionException() {
        CFSolverConnectionException ex = new CFSolverConnectionException("Connection failed");
        assertInstanceOf(CFSolverException.class, ex);
        assertEquals("Connection failed", ex.getMessage());
    }

    @Test
    @DisplayName("CFSolverTimeoutException should extend CFSolverException")
    void testCFSolverTimeoutException() {
        CFSolverTimeoutException ex = new CFSolverTimeoutException("Timeout");
        assertInstanceOf(CFSolverException.class, ex);
        assertEquals("Timeout", ex.getMessage());
    }

    @Test
    @DisplayName("All exceptions should be throwable")
    void testExceptionsAreThrowable() {
        assertThrows(CFSolverException.class, () -> {
            throw new CFSolverException("test");
        });
        assertThrows(CFSolverAPIException.class, () -> {
            throw new CFSolverAPIException("test");
        });
        assertThrows(CFSolverChallengeException.class, () -> {
            throw new CFSolverChallengeException("test");
        });
        assertThrows(CFSolverConnectionException.class, () -> {
            throw new CFSolverConnectionException("test");
        });
        assertThrows(CFSolverTimeoutException.class, () -> {
            throw new CFSolverTimeoutException("test");
        });
    }
}
