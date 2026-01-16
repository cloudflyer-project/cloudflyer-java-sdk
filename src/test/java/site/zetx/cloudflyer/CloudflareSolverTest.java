package site.zetx.cloudflyer;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import site.zetx.cloudflyer.exceptions.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CloudflareSolver class.
 */
class CloudflareSolverTest {

    private MockWebServer mockServer;
    private MockWebServer mockApiServer;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockApiServer = new MockWebServer();
        mockServer.start();
        mockApiServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
        mockApiServer.shutdown();
    }

    @Test
    @DisplayName("Should create solver with API key")
    void testCreateSolver() {
        try (CloudflareSolver solver = new CloudflareSolver("test-api-key")) {
            assertNotNull(solver);
        }
    }

    @Test
    @DisplayName("Should create solver with builder")
    void testBuilderPattern() {
        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase("https://custom-api.example.com")
                .timeout(60000)
                .solve(true)
                .onChallenge(true)
                .usePolling(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            assertNotNull(solver);
        }
    }

    @Test
    @DisplayName("Should make GET request without challenge")
    void testGetRequestNoChallengeNoSolve() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\": \"ok\"}")
                .addHeader("Content-Type", "application/json"));

        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase(mockApiServer.url("/").toString())
                .solve(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            
            Response response = solver.get(mockServer.url("/api/test").toString());
            
            assertEquals(200, response.statusCode());
            assertEquals("{\"status\": \"ok\"}", response.body());
            assertTrue(response.isSuccessful());
        }
    }

    @Test
    @DisplayName("Should make POST request with body")
    void testPostRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setBody("{\"id\": 123}")
                .addHeader("Content-Type", "application/json"));

        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase(mockApiServer.url("/").toString())
                .solve(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            
            Map<String, Object> body = new HashMap<>();
            body.put("name", "test");
            body.put("value", 42);
            
            Response response = solver.post(mockServer.url("/api/create").toString(), body);
            
            assertEquals(201, response.statusCode());
            
            RecordedRequest request = mockServer.takeRequest();
            assertEquals("POST", request.getMethod());
            assertTrue(request.getBody().readUtf8().contains("\"name\":\"test\""));
        }
    }

    @Test
    @DisplayName("Should make PUT request")
    void testPutRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"updated\": true}"));

        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase(mockApiServer.url("/").toString())
                .solve(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            
            Response response = solver.put(mockServer.url("/api/update").toString(), 
                    "{\"data\": \"value\"}");
            
            assertEquals(200, response.statusCode());
            
            RecordedRequest request = mockServer.takeRequest();
            assertEquals("PUT", request.getMethod());
        }
    }

    @Test
    @DisplayName("Should make DELETE request")
    void testDeleteRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(204));

        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase(mockApiServer.url("/").toString())
                .solve(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            
            Response response = solver.delete(mockServer.url("/api/item/1").toString());
            
            assertEquals(204, response.statusCode());
            
            RecordedRequest request = mockServer.takeRequest();
            assertEquals("DELETE", request.getMethod());
        }
    }

    @Test
    @DisplayName("Should make PATCH request")
    void testPatchRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"patched\": true}"));

        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase(mockApiServer.url("/").toString())
                .solve(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            
            Response response = solver.patch(mockServer.url("/api/item/1").toString(), 
                    "{\"field\": \"newValue\"}");
            
            assertEquals(200, response.statusCode());
            
            RecordedRequest request = mockServer.takeRequest();
            assertEquals("PATCH", request.getMethod());
        }
    }

    @Test
    @DisplayName("Should include custom headers in request")
    void testCustomHeaders() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("OK"));

        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase(mockApiServer.url("/").toString())
                .solve(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Custom-Header", "custom-value");
            headers.put("Authorization", "Bearer token123");
            
            solver.get(mockServer.url("/api/test").toString(), headers);
            
            RecordedRequest request = mockServer.takeRequest();
            assertEquals("custom-value", request.getHeader("X-Custom-Header"));
            assertEquals("Bearer token123", request.getHeader("Authorization"));
        }
    }

    @Test
    @DisplayName("Should handle server error responses")
    void testServerError() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Internal Server Error\"}"));

        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase(mockApiServer.url("/").toString())
                .solve(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            
            Response response = solver.get(mockServer.url("/api/test").toString());
            
            assertEquals(500, response.statusCode());
            assertFalse(response.isSuccessful());
        }
    }

    @Test
    @DisplayName("Should handle 403 non-Cloudflare response")
    void testNonCloudflare403() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setBody("Forbidden")
                .addHeader("Server", "nginx"));

        try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .apiBase(mockApiServer.url("/").toString())
                .solve(false)
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build()) {
            
            Response response = solver.get(mockServer.url("/api/test").toString());
            
            assertEquals(403, response.statusCode());
            assertEquals("Forbidden", response.body());
        }
    }

    @Test
    @DisplayName("Should close resources properly")
    void testClose() {
        CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                .useLinkSocks(false)
                .useMaskTunnel(false)
                .build();
        
        assertDoesNotThrow(solver::close);
    }

    @Test
    @DisplayName("Should work with try-with-resources")
    void testAutoCloseable() {
        assertDoesNotThrow(() -> {
            try (CloudflareSolver solver = CloudflareSolver.builder("test-api-key")
                    .useLinkSocks(false)
                    .useMaskTunnel(false)
                    .build()) {
                assertNotNull(solver);
            }
        });
    }
}
