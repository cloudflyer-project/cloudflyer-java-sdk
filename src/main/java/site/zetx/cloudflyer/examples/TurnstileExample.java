package site.zetx.cloudflyer;

import site.zetx.cloudflyer.exceptions.CFSolverException;

import java.util.HashMap;
import java.util.Map;

/**
 * Turnstile solving example for CFSolver Java SDK.
 */
public class TurnstileExample {
    
    public static void main(String[] args) {
        String apiKey = System.getenv("CLOUDFLYER_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set CLOUDFLYER_API_KEY environment variable");
            System.exit(1);
        }
        
        CloudflareSolver solver = new CloudflareSolver(apiKey);
        
        try {
            // Solve a Turnstile challenge
            System.out.println("Solving Turnstile challenge...");
            String token = solver.solveTurnstile(
                "https://example.com/page-with-turnstile",
                "your-turnstile-sitekey"
            );
            System.out.println("Token: " + token);
            
            // Use the token in a form submission
            Map<String, String> formData = new HashMap<>();
            formData.put("cf-turnstile-response", token);
            
            Response response = solver.post("https://example.com/submit", formData);
            System.out.println("Submit response: " + response.statusCode());
            
        } catch (CFSolverException e) {
            e.printStackTrace();
        }
    }
}
