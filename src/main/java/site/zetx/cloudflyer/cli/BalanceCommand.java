package site.zetx.cloudflyer.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import okhttp3.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Balance command - Check account balance.
 */
@Command(
    name = "balance",
    description = "Check account balance"
)
public class BalanceCommand implements Runnable {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    @Option(names = {"--api-proxy"}, description = "Proxy for API calls (scheme://host:port)")
    String apiProxy;
    
    @Option(names = {"--json"}, description = "Output result as JSON")
    boolean outputJson;
    
    @Override
    public void run() {
        CFSolverCLI.requireApiKey();
        
        String apiKey = CFSolverCLI.getApiKey();
        String apiBase = CFSolverCLI.getApiBase();
        
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS);
        
        if (apiProxy != null && !apiProxy.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(apiProxy);
                String host = uri.getHost();
                int port = uri.getPort();
                if (port == -1) {
                    port = uri.getScheme().equals("https") ? 443 : 8080;
                }
                clientBuilder.proxy(new java.net.Proxy(java.net.Proxy.Type.HTTP, 
                        new java.net.InetSocketAddress(host, port)));
            } catch (Exception e) {
                System.err.println("[x] Invalid proxy URL: " + apiProxy);
                System.exit(1);
            }
        }
        
        OkHttpClient client = clientBuilder.build();
        
        JsonObject payload = new JsonObject();
        payload.addProperty("apiKey", apiKey);
        
        RequestBody body = RequestBody.create(gson.toJson(payload), JSON);
        Request request = new Request.Builder()
                .url(apiBase + "/api/getBalance")
                .post(body)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 200) {
                if (outputJson) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("success", false);
                    error.put("error", "HTTP " + response.code());
                    System.out.println(gson.toJson(error));
                } else {
                    System.err.println("[x] Error: HTTP " + response.code());
                }
                System.exit(1);
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject data = gson.fromJson(responseBody, JsonObject.class);
            
            if (data.has("errorId") && data.get("errorId").getAsInt() != 0) {
                String errorDesc = data.has("errorDescription") ? 
                        data.get("errorDescription").getAsString() : "Unknown error";
                if (outputJson) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("success", false);
                    error.put("error", errorDesc);
                    System.out.println(gson.toJson(error));
                } else {
                    System.err.println("[x] Error: " + errorDesc);
                }
                System.exit(1);
            }
            
            if (outputJson) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("balance", data.has("balance") ? data.get("balance").getAsDouble() : 0);
                System.out.println(gson.toJson(result));
            } else {
                double balance = data.has("balance") ? data.get("balance").getAsDouble() : 0;
                System.out.println("[+] Balance: " + balance);
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
        }
    }
}
