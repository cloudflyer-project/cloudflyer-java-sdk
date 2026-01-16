package site.zetx.cloudflyer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Response class.
 */
class ResponseTest {

    @Test
    @DisplayName("Should create response with correct status code")
    void testStatusCode() {
        Response response = new Response(200, "OK", new HashMap<>());
        assertEquals(200, response.statusCode());
    }

    @Test
    @DisplayName("Should return response body")
    void testBody() {
        String body = "{\"message\": \"Hello\"}";
        Response response = new Response(200, body, new HashMap<>());
        assertEquals(body, response.body());
    }

    @Test
    @DisplayName("Should return headers map")
    void testHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "value");
        
        Response response = new Response(200, "", headers);
        assertEquals(2, response.headers().size());
        assertEquals("application/json", response.headers().get("Content-Type"));
    }

    @Test
    @DisplayName("Should get header by exact name")
    void testHeaderExactMatch() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        Response response = new Response(200, "", headers);
        assertEquals("application/json", response.header("Content-Type"));
    }

    @Test
    @DisplayName("Should get header case-insensitively")
    void testHeaderCaseInsensitive() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        Response response = new Response(200, "", headers);
        assertEquals("application/json", response.header("content-type"));
        assertEquals("application/json", response.header("CONTENT-TYPE"));
    }

    @Test
    @DisplayName("Should return null for missing header")
    void testHeaderMissing() {
        Response response = new Response(200, "", new HashMap<>());
        assertNull(response.header("X-Missing"));
    }

    @Test
    @DisplayName("Should identify successful 2xx responses")
    void testIsSuccessful() {
        assertTrue(new Response(200, "", new HashMap<>()).isSuccessful());
        assertTrue(new Response(201, "", new HashMap<>()).isSuccessful());
        assertTrue(new Response(204, "", new HashMap<>()).isSuccessful());
        assertTrue(new Response(299, "", new HashMap<>()).isSuccessful());
    }

    @Test
    @DisplayName("Should identify non-successful responses")
    void testIsNotSuccessful() {
        assertFalse(new Response(100, "", new HashMap<>()).isSuccessful());
        assertFalse(new Response(199, "", new HashMap<>()).isSuccessful());
        assertFalse(new Response(300, "", new HashMap<>()).isSuccessful());
        assertFalse(new Response(400, "", new HashMap<>()).isSuccessful());
        assertFalse(new Response(403, "", new HashMap<>()).isSuccessful());
        assertFalse(new Response(500, "", new HashMap<>()).isSuccessful());
        assertFalse(new Response(503, "", new HashMap<>()).isSuccessful());
    }
}
