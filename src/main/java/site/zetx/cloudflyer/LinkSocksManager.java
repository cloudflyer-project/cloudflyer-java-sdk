package site.zetx.cloudflyer;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages LinkSocks executable download and process lifecycle.
 * 
 * LinkSocks is used as a reverse proxy provider to enable challenge solving
 * through the client's network environment.
 */
class LinkSocksManager {
    
    private static final Logger logger = Logger.getLogger(LinkSocksManager.class.getName());
    private static final Gson gson = new Gson();
    
    private final String apiBase;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final String upstreamProxy;
    private final ToolDownloader toolDownloader;
    private final int startupWaitMs;
    
    private Process linksocksProcess;
    private LinkSocksConfig config;
    private String connectorToken;
    private boolean connected = false;
    
    /**
     * LinkSocks configuration from API.
     */
    static class LinkSocksConfig {
        String url;
        String token;
        @com.google.gson.annotations.SerializedName("connector_token")
        String connectorToken;
        
        String getWsUrl() {
            if (url == null) return null;
            if (url.startsWith("https://")) {
                return "wss://" + url.substring(8);
            } else if (url.startsWith("http://")) {
                return "ws://" + url.substring(7);
            }
            return url;
        }
    }
    
    LinkSocksManager(String apiBase, String apiKey, OkHttpClient httpClient, String upstreamProxy) {
        this(apiBase, apiKey, httpClient, upstreamProxy, 2000);
    }
    
    LinkSocksManager(String apiBase, String apiKey, OkHttpClient httpClient, String upstreamProxy, int startupWaitMs) {
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.upstreamProxy = upstreamProxy;
        this.startupWaitMs = startupWaitMs;
        this.toolDownloader = new ToolDownloader(httpClient);
    }
    
    /**
     * Get LinkSocks configuration from API.
     */
    LinkSocksConfig getConfig() throws IOException {
        if (config != null) {
            return config;
        }
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiBase + "/api/linksocks/getLinkSocks")
                .post(RequestBody.create("", MediaType.get("application/json")));
        
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }
        
        try (okhttp3.Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (response.code() != 200) {
                String error = response.body() != null ? response.body().string() : "HTTP " + response.code();
                throw new IOException("Failed to get linksocks config: " + error);
            }
            
            String body = response.body() != null ? response.body().string() : "";
            config = gson.fromJson(body, LinkSocksConfig.class);
            
            if (config == null || config.url == null || config.token == null) {
                throw new IOException("Invalid linksocks config: " + body);
            }
            
            return config;
        }
    }
    
    /**
     * Connect to LinkSocks server as a provider.
     */
    synchronized void connect() throws IOException {
        if (connected && linksocksProcess != null && linksocksProcess.isAlive()) {
            return;
        }
        
        // Get config from API
        LinkSocksConfig cfg = getConfig();
        
        // Ensure linksocks executable exists using ToolDownloader
        Path executable = toolDownloader.ensureTool("linksocks");
        
        // Build command
        ProcessBuilder pb = buildProviderCommand(executable, cfg);
        
        logger.info("Starting LinkSocks provider...");
        linksocksProcess = pb.start();
        
        // Capture output for debugging
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();
        startOutputReader(linksocksProcess.getInputStream(), "stdout", stdoutBuilder);
        startOutputReader(linksocksProcess.getErrorStream(), "stderr", stderrBuilder);
        
        // Wait for connection
        try {
            Thread.sleep(startupWaitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (!linksocksProcess.isAlive()) {
            int exitCode = linksocksProcess.exitValue();
            String stdout = stdoutBuilder.toString().trim();
            String stderr = stderrBuilder.toString().trim();
            String errorMsg = "LinkSocks process exited with code " + exitCode;
            if (!stderr.isEmpty()) {
                errorMsg += ": " + stderr;
            } else if (!stdout.isEmpty()) {
                errorMsg += ": " + stdout;
            }
            throw new IOException(errorMsg);
        }
        
        // Use connector token from config
        connectorToken = cfg.connectorToken;
        if (connectorToken == null || connectorToken.isEmpty()) {
            connectorToken = generateToken();
        }
        
        connected = true;
        logger.info("LinkSocks provider connected");
    }
    
    /**
     * Get the connector token for API requests.
     */
    String getConnectorToken() {
        return connectorToken;
    }
    
    /**
     * Get the WebSocket URL for API requests.
     */
    String getWsUrl() {
        return config != null ? config.getWsUrl() : null;
    }
    
    /**
     * Close the LinkSocks process.
     */
    synchronized void close() {
        if (linksocksProcess != null) {
            linksocksProcess.destroy();
            try {
                linksocksProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                linksocksProcess.destroyForcibly();
            }
            linksocksProcess = null;
        }
        connected = false;
    }
    
    private ProcessBuilder buildProviderCommand(Path executable, LinkSocksConfig cfg) {
        // linksocks provider -t <token> -u <ws_url> [-x <proxy>]
        // Note: We don't use -c flag because the API already created the connector_token for us
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(executable.toString());
        command.add("provider");
        command.add("-t");
        command.add(cfg.token);
        command.add("-u");
        command.add(cfg.getWsUrl());
        
        if (upstreamProxy != null && !upstreamProxy.isEmpty()) {
            // Convert HTTP proxy to SOCKS5 format if needed
            // linksocks only supports socks5:// upstream proxy
            String socksProxy = upstreamProxy;
            if (socksProxy.startsWith("http://") || socksProxy.startsWith("https://")) {
                // HTTP proxy not directly supported, skip upstream proxy for linksocks
                // The proxy will be used by MaskTunnel instead
                logger.warning("LinkSocks only supports SOCKS5 upstream proxy, HTTP proxy will be ignored for LinkSocks");
            } else {
                command.add("-x");
                command.add(socksProxy);
            }
        }
        
        logger.info("LinkSocks command: " + String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        return pb;
    }
    
    private void startOutputReader(InputStream stream, String name, StringBuilder builder) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[linksocks " + name + "] " + line);
                    if (builder != null) {
                        builder.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                // Stream closed
            }
        });
        thread.setDaemon(true);
        thread.setName("linksocks-" + name);
        thread.start();
    }
    
    private void startOutputReader(InputStream stream, String name) {
        startOutputReader(stream, name, null);
    }
    
    private String generateToken() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
