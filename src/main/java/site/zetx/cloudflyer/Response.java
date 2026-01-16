package site.zetx.cloudflyer;

import java.util.Map;

/**
 * HTTP response wrapper.
 */
public class Response {
    
    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;
    
    public Response(int statusCode, String body, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers;
    }
    
    /**
     * Get the HTTP status code.
     * 
     * @return status code
     */
    public int statusCode() {
        return statusCode;
    }
    
    /**
     * Get the response body as string.
     * 
     * @return response body
     */
    public String body() {
        return body;
    }
    
    /**
     * Get the response headers.
     * 
     * @return headers map
     */
    public Map<String, String> headers() {
        return headers;
    }
    
    /**
     * Get a specific header value (case-insensitive).
     * 
     * @param name header name
     * @return header value or null
     */
    public String header(String name) {
        // Try exact match first
        String value = headers.get(name);
        if (value != null) {
            return value;
        }
        // Case-insensitive search
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Check if the response was successful (2xx status code).
     * 
     * @return true if successful
     */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
