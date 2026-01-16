package site.zetx.cloudflyer.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import site.zetx.cloudflyer.CloudflareSolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Solve command group for Cloudflare and Turnstile challenges.
 */
@Command(
    name = "solve",
    description = "Solve Cloudflare challenges",
    subcommands = {
        SolveCommand.CloudflareCommand.class,
        SolveCommand.TurnstileCommand.class
    }
)
public class SolveCommand implements Runnable {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    @Override
    public void run() {
        System.out.println("Usage: cfsolver solve <cloudflare|turnstile> [options]");
    }
    
    @Command(name = "cloudflare", description = "Solve Cloudflare challenge for a URL")
    static class CloudflareCommand implements Runnable {
        
        @Parameters(index = "0", description = "Target URL")
        String url;
        
        @Option(names = {"-X", "--proxy"}, description = "Proxy for HTTP requests (scheme://host:port)")
        String proxy;
        
        @Option(names = {"--api-proxy"}, description = "Proxy for API calls (scheme://host:port)")
        String apiProxy;
        
        @Option(names = {"-T", "--timeout"}, description = "Timeout in seconds", defaultValue = "120")
        int timeout;
        
        @Option(names = {"--json"}, description = "Output result as JSON")
        boolean outputJson;
        
        @Option(names = {"--no-linksocks"}, description = "Disable LinkSocks")
        boolean noLinkSocks;
        
        @Override
        public void run() {
            CFSolverCLI.requireApiKey();
            
            CFSolverCLI.verboseLog("Solving Cloudflare challenge for: " + url);
            
            CloudflareSolver.Builder builder = CloudflareSolver.builder(CFSolverCLI.getApiKey())
                    .apiBase(CFSolverCLI.getApiBase())
                    .timeout(timeout * 1000)
                    .solve(true)
                    .onChallenge(false)
                    .useLinkSocks(!noLinkSocks);
            
            if (proxy != null) {
                builder.proxy(proxy);
            }
            if (apiProxy != null) {
                builder.apiProxy(apiProxy);
            }
            
            try (CloudflareSolver solver = builder.build()) {
                // Make a request to trigger challenge solving
                site.zetx.cloudflyer.Response response = solver.get(url);
                
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("url", url);
                result.put("status_code", response.statusCode());
                
                if (outputJson) {
                    System.out.println(gson.toJson(result));
                } else {
                    System.out.println("[+] Challenge solved successfully!");
                    System.out.println("    URL: " + url);
                    System.out.println("    Status: " + response.statusCode());
                }
            } catch (Exception e) {
                if (outputJson) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("success", false);
                    error.put("error", e.getMessage());
                    System.out.println(gson.toJson(error));
                } else {
                    System.err.println("[x] Error: " + e.getMessage());
                }
                System.exit(1);
            }
        }
    }
    
    @Command(name = "turnstile", description = "Solve Turnstile challenge and get token")
    static class TurnstileCommand implements Runnable {
        
        @Parameters(index = "0", description = "Website URL")
        String url;
        
        @Parameters(index = "1", description = "Turnstile sitekey")
        String sitekey;
        
        @Option(names = {"--api-proxy"}, description = "Proxy for API calls (scheme://host:port)")
        String apiProxy;
        
        @Option(names = {"-T", "--timeout"}, description = "Timeout in seconds", defaultValue = "120")
        int timeout;
        
        @Option(names = {"--json"}, description = "Output result as JSON")
        boolean outputJson;
        
        @Option(names = {"--no-linksocks"}, description = "Disable LinkSocks")
        boolean noLinkSocks;
        
        @Override
        public void run() {
            CFSolverCLI.requireApiKey();
            
            CFSolverCLI.verboseLog("Solving Turnstile challenge for: " + url);
            CFSolverCLI.verboseLog("Site key: " + sitekey);
            
            CloudflareSolver.Builder builder = CloudflareSolver.builder(CFSolverCLI.getApiKey())
                    .apiBase(CFSolverCLI.getApiBase())
                    .timeout(timeout * 1000)
                    .useLinkSocks(!noLinkSocks);
            
            if (apiProxy != null) {
                builder.apiProxy(apiProxy);
            }
            
            try (CloudflareSolver solver = builder.build()) {
                String token = solver.solveTurnstile(url, sitekey);
                
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("url", url);
                result.put("sitekey", sitekey);
                result.put("token", token);
                
                if (outputJson) {
                    System.out.println(gson.toJson(result));
                } else {
                    System.out.println("[+] Turnstile solved successfully!");
                    String tokenPreview = token;
                    if (token.length() > 80) {
                        tokenPreview = token.substring(0, 80) + "...";
                    }
                    System.out.println("    Token: " + tokenPreview);
                    System.out.println("    Token length: " + token.length());
                }
            } catch (Exception e) {
                if (outputJson) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("success", false);
                    error.put("error", e.getMessage());
                    System.out.println(gson.toJson(error));
                } else {
                    System.err.println("[x] Error: " + e.getMessage());
                }
                System.exit(1);
            }
        }
    }
}
