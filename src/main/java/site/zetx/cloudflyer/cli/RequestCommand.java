package site.zetx.cloudflyer.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import site.zetx.cloudflyer.CloudflareSolver;
import site.zetx.cloudflyer.Response;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request command - Make HTTP request with automatic challenge bypass.
 */
@Command(
    name = "request",
    description = "Make HTTP request with automatic challenge bypass"
)
public class RequestCommand implements Runnable {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    @Parameters(index = "0", description = "Target URL")
    String url;
    
    @Option(names = {"-X", "--proxy"}, description = "Proxy for HTTP requests (scheme://host:port)")
    String proxy;
    
    @Option(names = {"--api-proxy"}, description = "Proxy for API calls (scheme://host:port)")
    String apiProxy;
    
    @Option(names = {"-m", "--method"}, description = "HTTP method", defaultValue = "GET")
    String method;
    
    @Option(names = {"-d", "--data"}, description = "Request body data")
    String data;
    
    @Option(names = {"-H", "--header"}, description = "Request header (can be used multiple times)")
    List<String> headers = new ArrayList<>();
    
    @Option(names = {"-o", "--output"}, description = "Output file path")
    String output;
    
    @Option(names = {"--json"}, description = "Output response info as JSON")
    boolean outputJson;
    
    @Option(names = {"-T", "--timeout"}, description = "Timeout in seconds", defaultValue = "120")
    int timeout;
    
    @Option(names = {"--no-linksocks"}, description = "Disable LinkSocks")
    boolean noLinkSocks;
    
    @Option(names = {"--masktunnel"}, description = "Enable MaskTunnel for TLS fingerprint simulation")
    boolean useMaskTunnel;
    
    @Override
    public void run() {
        CFSolverCLI.requireApiKey();
        
        CFSolverCLI.verboseLog("Making " + method + " request to: " + url);
        
        CloudflareSolver.Builder builder = CloudflareSolver.builder(CFSolverCLI.getApiKey())
                .apiBase(CFSolverCLI.getApiBase())
                .timeout(timeout * 1000)
                .solve(true)
                .onChallenge(true)
                .useLinkSocks(!noLinkSocks)
                .useMaskTunnel(useMaskTunnel);
        
        if (proxy != null) {
            builder.proxy(proxy);
        }
        if (apiProxy != null) {
            builder.apiProxy(apiProxy);
        }
        
        try (CloudflareSolver solver = builder.build()) {
            // Parse headers
            Map<String, String> headerMap = new LinkedHashMap<>();
            for (String h : headers) {
                int idx = h.indexOf(':');
                if (idx > 0) {
                    String key = h.substring(0, idx).trim();
                    String value = h.substring(idx + 1).trim();
                    headerMap.put(key, value);
                }
            }
            
            Response response;
            String upperMethod = method.toUpperCase();
            
            switch (upperMethod) {
                case "GET":
                    response = solver.get(url, headerMap.isEmpty() ? null : headerMap);
                    break;
                case "POST":
                    response = solver.post(url, data, headerMap.isEmpty() ? null : headerMap);
                    break;
                case "PUT":
                    response = solver.put(url, data, headerMap.isEmpty() ? null : headerMap);
                    break;
                case "DELETE":
                    response = solver.delete(url, headerMap.isEmpty() ? null : headerMap);
                    break;
                case "PATCH":
                    response = solver.patch(url, data, headerMap.isEmpty() ? null : headerMap);
                    break;
                default:
                    System.err.println("Unsupported method: " + method);
                    System.exit(1);
                    return;
            }
            
            String body = response.body();
            
            if (output != null && !output.isEmpty()) {
                try (FileOutputStream fos = new FileOutputStream(output)) {
                    fos.write(body.getBytes(StandardCharsets.UTF_8));
                }
                System.out.println("[+] Response saved to: " + output);
            } else if (outputJson) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("url", url);
                result.put("method", upperMethod);
                result.put("status_code", response.statusCode());
                result.put("headers", response.headers());
                result.put("content_length", body.length());
                System.out.println(gson.toJson(result));
            } else {
                System.out.print(body);
            }
            
        } catch (IOException e) {
            if (outputJson) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("success", false);
                error.put("error", e.getMessage());
                System.out.println(gson.toJson(error));
            } else {
                System.err.println("[x] Error: " + e.getMessage());
            }
            System.exit(1);
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
