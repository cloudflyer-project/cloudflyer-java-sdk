package site.zetx.cloudflyer;

/**
 * CFSolver - Java SDK for CloudFlyer API
 * 
 * Automatically bypass Cloudflare challenges using the CloudFlyer cloud service.
 * 
 * <pre>{@code
 * CloudflareSolver solver = new CloudflareSolver("your-api-key");
 * Response response = solver.get("https://protected-site.com");
 * System.out.println(response.body());
 * }</pre>
 * 
 * @version 0.2.0
 */
public final class CFSolver {
    
    public static final String VERSION = "0.2.0";
    
    private CFSolver() {
        // Utility class
    }
}
