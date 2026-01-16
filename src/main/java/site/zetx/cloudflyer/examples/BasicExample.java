package site.zetx.cloudflyer;

import site.zetx.cloudflyer.exceptions.CFSolverException;

/**
 * Basic usage example for CFSolver Java SDK.
 */
public class BasicExample {
    
    public static void main(String[] args) {
        String apiKey = System.getenv("CLOUDFLYER_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set CLOUDFLYER_API_KEY environment variable");
            System.exit(1);
        }
        
        CloudflareSolver solver = new CloudflareSolver(apiKey);
        
        try {
            Response response = solver.get("https://example.com");
            System.out.println("Status: " + response.statusCode());
            System.out.println("Body length: " + response.body().length());
        } catch (CFSolverException e) {
            e.printStackTrace();
        }
    }
}
