package site.zetx.cloudflyer.examples;

import site.zetx.cloudflyer.CloudflareSolver;
import site.zetx.cloudflyer.Response;
import site.zetx.cloudflyer.exceptions.CFSolverException;

/**
 * Example: Solve Cloudflare Challenge using cfsolver SDK.
 *
 * This script demonstrates how to use the CloudflareSolver to bypass
 * Cloudflare's challenge protection on the demo site.
 *
 * Usage:
 *     mvn compile
 *     set CLOUDFLYER_API_KEY=your_api_key
 *     mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.examples.SdkChallenge"
 *
 * Options:
 *     --proxy <url>       HTTP proxy for direct requests and LinkSocks upstream
 *     --api-proxy <url>   HTTP proxy for API requests (defaults to --proxy)
 *     --no-linksocks      Disable LinkSocks (not recommended)
 *     --masktunnel        Enable MaskTunnel for TLS fingerprint simulation
 */
public class SdkChallenge {

    private static final String DEMO_URL = "https://cloudflyer.zetx.site/demo/challenge";

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
        boolean maskTunnel = hasFlag(args, "--masktunnel");

        System.out.println("Target URL: " + DEMO_URL);
        System.out.println("API Base: " + apiBase);
        System.out.println("LinkSocks: " + (noLinkSocks ? "disabled" : "enabled"));
        System.out.println("MaskTunnel: " + (maskTunnel ? "enabled" : "disabled"));
        if (proxy != null && !proxy.isEmpty()) {
            System.out.println("Proxy: " + proxy);
        }
        if (apiProxy != null && !apiProxy.isEmpty()) {
            System.out.println("API Proxy: " + apiProxy);
        }

        CloudflareSolver.Builder builder = CloudflareSolver.builder(apiKey)
                .apiBase(apiBase)
                .solve(true)
                .onChallenge(true)
                .useLinkSocks(!noLinkSocks)
                .useMaskTunnel(maskTunnel);
        
        if (proxy != null && !proxy.isEmpty()) {
            builder.proxy(proxy);
        }
        if (apiProxy != null && !apiProxy.isEmpty()) {
            builder.apiProxy(apiProxy);
        }

        try (CloudflareSolver solver = builder.build()) {
            System.out.println("Sending request to demo page...");
            Response response = solver.get(DEMO_URL);

            System.out.println("Response status: " + response.statusCode());
            
            // Print response body preview
            String body = response.body();
            System.out.println("Response body length: " + body.length());
            String preview = body.length() > 1000 ? body.substring(0, 1000) + "..." : body;
            System.out.println("Response body preview:\n" + preview);

            if (response.statusCode() == 200) {
                String bodyLower = body.toLowerCase();
                if (bodyLower.contains("cf-turnstile") && bodyLower.contains("challenge")) {
                    System.out.println("\nWARNING: Challenge page still present - solve may have failed");
                } else if (bodyLower.contains("challenge passed") || bodyLower.contains("successfully")) {
                    System.out.println("\nChallenge bypassed successfully!");
                } else {
                    System.out.println("\nGot 200 response (check content above)");
                }
            } else {
                System.err.println("Request failed with status " + response.statusCode());
                String errorPreview = response.body();
                if (errorPreview.length() > 500) {
                    errorPreview = errorPreview.substring(0, 500);
                }
                System.err.println("Response: " + errorPreview);
            }
        } catch (CFSolverException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
