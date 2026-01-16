package site.zetx.cloudflyer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import okhttp3.OkHttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages the MaskTunnel executable for TLS fingerprint simulation.
 * 
 * MaskTunnel is a proxy that mimics browser TLS fingerprints (JA3/JA4)
 * to bypass Cloudflare's TLS fingerprint detection.
 */
public class MaskTunnelManager implements Closeable {
    
    private static final Logger logger = Logger.getLogger(MaskTunnelManager.class.getName());
    private final String addr;
    private final int port;
    private final String upstreamProxy;
    private final ToolDownloader toolDownloader;
    private Process process;
    private Thread outputThread;
    private Thread errorThread;
    
    public MaskTunnelManager(String addr, int port, String upstreamProxy, OkHttpClient httpClient) {
        this.addr = addr;
        this.port = port;
        this.upstreamProxy = upstreamProxy;
        this.toolDownloader = new ToolDownloader(httpClient);
    }
    
    public MaskTunnelManager(int port, String upstreamProxy, OkHttpClient httpClient) {
        this("127.0.0.1", port, upstreamProxy, httpClient);
    }
    
    /**
     * Get the proxy URL for this MaskTunnel instance.
     */
    public String getProxyUrl() {
        return "http://" + addr + ":" + port;
    }
    
    /**
     * Start the MaskTunnel process.
     */
    public void start() throws IOException {
        if (process != null && process.isAlive()) {
            logger.info("MaskTunnel already running");
            return;
        }
        
        Path executable = ensureExecutable();
        List<String> command = buildCommand(executable);
        
        logger.info("MaskTunnel command: " + String.join(" ", command));
        logger.info("Starting MaskTunnel...");
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        process = pb.start();
        
        startOutputReader(process.getInputStream(), "stdout");
        startErrorReader(process.getErrorStream(), "stderr");
        
        // Wait for startup
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (!process.isAlive()) {
            throw new IOException("MaskTunnel process exited unexpectedly");
        }
        
        logger.info("MaskTunnel started on " + getProxyUrl());
    }
    
    private List<String> buildCommand(Path executable) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.add("--addr");
        command.add(addr);
        command.add("--port");
        command.add(String.valueOf(port));
        
        if (upstreamProxy != null && !upstreamProxy.isEmpty()) {
            command.add("--upstream-proxy");
            command.add(upstreamProxy);
        }
        
        return command;
    }
    
    private void startOutputReader(InputStream stream, String name) {
        outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[masktunnel " + name + "] " + line);
                }
            } catch (IOException e) {
                // Stream closed
            }
        }, "masktunnel-" + name);
        outputThread.setDaemon(true);
        outputThread.start();
    }
    
    private void startErrorReader(InputStream stream, String name) {
        errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.warning("[masktunnel " + name + "] " + line);
                }
            } catch (IOException e) {
                // Stream closed
            }
        }, "masktunnel-" + name);
        errorThread.setDaemon(true);
        errorThread.start();
    }
    
    private Path ensureExecutable() throws IOException {
        return toolDownloader.ensureTool("masktunnel");
    }
    
    /**
     * Reset all TLS sessions.
     */
    public boolean resetSessions() {
        if (process == null || !process.isAlive()) {
            logger.warning("MaskTunnel is not running");
            return false;
        }
        
        try {
            URL url = new URL("http://" + addr + ":" + port + "/__masktunnel__/reset");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int status = conn.getResponseCode();
            if (status == 200) {
                logger.fine("MaskTunnel sessions reset successfully");
                return true;
            } else {
                logger.warning("MaskTunnel reset returned status " + status);
                return false;
            }
        } catch (IOException e) {
            logger.warning("Failed to reset MaskTunnel sessions: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void close() {
        if (process != null) {
            logger.info("Stopping MaskTunnel...");
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            process = null;
        }
    }
}
