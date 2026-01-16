package site.zetx.cloudflyer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CFSolver class.
 */
class CFSolverTest {

    @Test
    @DisplayName("Should have correct version")
    void testVersion() {
        assertNotNull(CFSolver.VERSION);
        assertTrue(CFSolver.VERSION.matches("\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    @DisplayName("Version should match expected format")
    void testVersionFormat() {
        String[] parts = CFSolver.VERSION.split("\\.");
        assertEquals(3, parts.length, "Version should have 3 parts (major.minor.patch)");
        
        for (String part : parts) {
            assertDoesNotThrow(() -> Integer.parseInt(part), 
                "Each version part should be a valid integer");
        }
    }
}
