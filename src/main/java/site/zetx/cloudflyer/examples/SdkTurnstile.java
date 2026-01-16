package site.zetx.cloudflyer.examples;

import site.zetx.cloudflyer.CloudflareSolver;
import site.zetx.cloudflyer.exceptions.CFSolverException;

/**
 * Example: Solve Cloudflare Turnstile using cfsolver SDK.
 *
 * This script demonstrates how to use the CloudflareSolver to solve
 * Turnstile challenges and obtain the token.
 *
 * Usage:
 *     mvn compile
 *     set CLOUDFLYER_API_KEY=your_api_key
 *     mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.examples.SdkTurnstile"
 *
 * Options:
 *     --proxy <url>       HTTP proxy for direct requests and LinkSocks upstream
 *     --api-proxy <url>   HTTP proxy for API requests (defaults to --proxy)
 *     --no-linksocks      Disable LinkSocks (not recommended)
 */
public class SdkTurnstile {

    private static final String DEMO_URL = "https://cloudflyer.zetx.site/demo/turnstile";
    private static final String SITE_KEY = "0x4AAAAAACJkAlPHW8xr1T2J";

    private static String getArg(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String name) {
        for (String arg : args) {
            if (name.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("CLOUDFLYER_API_KEY");
        String apiBase = System.getenv("CLOUDFLYER_API_BASE");
        if (apiBase == null || apiBase.isEmpty()) {
            apiBase = "https://solver.zetx.site";
        }

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set CLOUDFLYER_API_KEY environment variable");
            System.exit(1);
        }

        String proxy = getArg(args, "--proxy");
        String apiProxy = getArg(args, "--api-proxy");
        boolean noLinkSocks = hasFlag(args, "--no-linksocks");

        System.out.println("Target URL: " + DEMO_URL);
        System.out.println("Site Key: " + SITE_KEY);
        System.out.println("LinkSocks: " + (noLinkSocks ? "disabled" : "enabled"));
        if (proxy != null && !proxy.isEmpty()) {
            System.out.println("Proxy: " + proxy);
        }
        if (apiProxy != null && !apiProxy.isEmpty()) {
            System.out.println("API Proxy: " + apiProxy);
        }

        CloudflareSolver.Builder builder = CloudflareSolver.builder(apiKey)
                .apiBase(apiBase)
                .useLinkSocks(!noLinkSocks);
        
        if (proxy != null && !proxy.isEmpty()) {
            builder.proxy(proxy);
        }
        if (apiProxy != null && !apiProxy.isEmpty()) {
            builder.apiProxy(apiProxy);
        }

        try (CloudflareSolver solver = builder.build()) {
            System.out.println("Solving Turnstile challenge...");
            String token = solver.solveTurnstile(DEMO_URL, SITE_KEY);

            System.out.println("Turnstile solved successfully!");
            System.out.println("Token: " + token.substring(0, Math.min(80, token.length())) + "...");
            System.out.println("Token length: " + token.length());
        } catch (CFSolverException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
