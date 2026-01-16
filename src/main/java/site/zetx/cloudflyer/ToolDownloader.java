package site.zetx.cloudflyer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages external tool downloads with version tracking.
 * 
 * Similar to Python's downloader.py, this class handles:
 * - Version management and tracking
 * - Automatic download from GitHub releases
 * - Mirror fallback support (gh-proxy.com)
 * - Platform-specific binary detection
 * - Cache directory management
 */
class ToolDownloader {
    
    private static final Logger logger = Logger.getLogger(ToolDownloader.class.getName());
    private static final Gson gson = new Gson();
    
    // Tool version definitions
    private static final Map<String, String> TOOL_VERSIONS = new HashMap<>();
    static {
        TOOL_VERSIONS.put("masktunnel", "v1.0.6");
        TOOL_VERSIONS.put("linksocks", "v1.7.6");
    }
    
    // GitHub release base URLs
    private static final Map<String, String> RELEASE_BASE = new HashMap<>();
    static {
        RELEASE_BASE.put("masktunnel", "https://github.com/cloudflyer-project/masktunnel/releases");
        RELEASE_BASE.put("linksocks", "https://github.com/linksocks/linksocks/releases");
    }
    
    // Mirror URLs for fallback
    private static final Map<String, String> RELEASE_BASE_PROXY = new HashMap<>();
    static {
        RELEASE_BASE_PROXY.put("masktunnel", "https://gh-proxy.com/https://github.com/cloudflyer-project/masktunnel/releases");
        RELEASE_BASE_PROXY.put("linksocks", "https://gh-proxy.com/https://github.com/linksocks/linksocks/releases");
    }
    
    private final OkHttpClient httpClient;
    
    ToolDownloader(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    /**
     * Get the required version for a tool.
     */
    static String getRequiredVersion(String tool) {
        return TOOL_VERSIONS.get(tool);
    }
    
    /**
     * Ensure a tool is available locally. Returns executable path or null.
     * 
     * Tries the following in order:
     * 1. PATH or current directory
     * 2. Cache directory (if version matches)
     * 3. Download from GitHub; fallback to gh-proxy mirror
     */
    Path ensureTool(String tool) throws IOException {
        String binName = getBinaryName(tool);
        
        // Check PATH first
        Path pathExecutable = findInPath(binName);
        if (pathExecutable != null) {
            logger.fine("Found " + tool + " in PATH: " + pathExecutable);
            return pathExecutable;
        }
        
        // Check cache directory
        Path cacheDir = getCacheDir();
        Path executable = cacheDir.resolve(binName);
        
        // Check if we need to update
        if (Files.exists(executable) && !needsUpdate(tool)) {
            logger.fine("Using cached " + tool + ": " + executable);
            return executable;
        }
        
        // Download required
        String version = TOOL_VERSIONS.get(tool);
        if (version == null) {
            throw new IOException("Unknown tool: " + tool);
        }
        
        logger.info("Downloading " + tool + " " + version + "...");
        downloadTool(tool, version, executable);
        
        // Update version tracking
        saveInstalledVersion(tool, version);
        
        logger.info("Installed " + tool + " " + version + " at " + executable);
        return executable;
    }
    
    /**
     * Check if a tool needs to be updated.
     */
    boolean needsUpdate(String tool) {
        Map<String, String> installed = loadInstalledVersions();
        String currentVersion = installed.get(tool);
        String requiredVersion = TOOL_VERSIONS.get(tool);
        
        if (currentVersion == null || requiredVersion == null) {
            return true;
        }
        
        return !currentVersion.equals(requiredVersion);
    }
    
    /**
     * Force update a tool regardless of current version.
     */
    boolean forceUpdate(String tool) {
        try {
            // Remove version info
            Map<String, String> versions = loadInstalledVersions();
            versions.remove(tool);
            saveInstalledVersions(versions);
            
            // Remove cached binary
            Path cacheDir = getCacheDir();
            Path executable = cacheDir.resolve(getBinaryName(tool));
            Files.deleteIfExists(executable);
            
            // Re-download
            ensureTool(tool);
            return true;
        } catch (IOException e) {
            logger.warning("Failed to force update " + tool + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get version information for all tools.
     */
    Map<String, Object> getVersionInfo() {
        Map<String, Object> result = new HashMap<>();
        result.put("required_versions", new HashMap<>(TOOL_VERSIONS));
        result.put("installed_versions", loadInstalledVersions());
        result.put("version_file", getVersionFilePath().toString());
        result.put("version_file_exists", Files.exists(getVersionFilePath()));
        
        // Check outdated tools
        List<Map<String, String>> outdated = new ArrayList<>();
        Map<String, String> installed = loadInstalledVersions();
        for (Map.Entry<String, String> entry : TOOL_VERSIONS.entrySet()) {
            String tool = entry.getKey();
            String required = entry.getValue();
            String current = installed.get(tool);
            if (current == null || !current.equals(required)) {
                Map<String, String> info = new HashMap<>();
                info.put("tool", tool);
                info.put("required", required);
                info.put("current", current != null ? current : "not installed");
                outdated.add(info);
            }
        }
        result.put("outdated_tools", outdated);
        
        return result;
    }
    
    // ========== Private Methods ==========
    
    private String getBinaryName(String tool) {
        String os = System.getProperty("os.name").toLowerCase();
        String extension = os.contains("win") ? ".exe" : "";
        return tool + extension;
    }
    
    private String getAssetName(String tool) {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        String platform;
        String extension = "";
        
        if (os.contains("win")) {
            platform = "windows";
            extension = ".exe";
        } else if (os.contains("mac") || os.contains("darwin")) {
            platform = "darwin";
        } else {
            platform = "linux";
        }
        
        String archName;
        if (arch.contains("amd64") || arch.contains("x86_64") || arch.equals("x64")) {
            archName = "amd64";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            archName = "arm64";
        } else if (arch.contains("386") || arch.contains("x86") || arch.equals("i386")) {
            archName = "386";
        } else {
            archName = "amd64"; // Default
        }
        
        // Asset filename format: {tool}-{platform}-{arch}[.exe]
        return tool + "-" + platform + "-" + archName + extension;
    }
    
    private Path findInPath(String binName) {
        // Check current directory
        Path current = Paths.get(binName);
        if (Files.exists(current) && Files.isExecutable(current)) {
            return current.toAbsolutePath();
        }
        
        // Check PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String separator = System.getProperty("path.separator");
            for (String dir : pathEnv.split(separator)) {
                Path candidate = Paths.get(dir, binName);
                if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath();
                }
            }
        }
        
        return null;
    }
    
    private void downloadTool(String tool, String version, Path target) throws IOException {
        String base = RELEASE_BASE.get(tool);
        String baseProxy = RELEASE_BASE_PROXY.get(tool);
        String asset = getAssetName(tool);
        
        if (base == null) {
            throw new IOException("Unknown tool: " + tool);
        }
        
        // Build URL list (primary + mirror)
        List<String> urls = new ArrayList<>();
        urls.add(base + "/download/" + version + "/" + asset);
        if (baseProxy != null) {
            urls.add(baseProxy + "/download/" + version + "/" + asset);
        }
        
        // Create parent directories
        Files.createDirectories(target.getParent());
        
        IOException lastError = null;
        for (String url : urls) {
            try {
                downloadFile(url, target, tool + " " + version);
                
                // Make executable on Unix
                String os = System.getProperty("os.name").toLowerCase();
                if (!os.contains("win")) {
                    target.toFile().setExecutable(true);
                }
                
                return; // Success
            } catch (IOException e) {
                logger.fine("Download failed from " + url + ": " + e.getMessage());
                lastError = e;
            }
        }
        
        throw new IOException("Failed to download " + tool + " from all sources", lastError);
    }
    
    private void downloadFile(String url, Path target, String label) throws IOException {
        logger.info("Downloading " + label + " from " + url);
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            if (response.code() != 200) {
                throw new IOException("HTTP " + response.code());
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }
            
            long contentLength = body.contentLength();
            long downloaded = 0;
            long lastLog = System.currentTimeMillis();
            
            try (InputStream is = body.byteStream();
                 OutputStream os = Files.newOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                    downloaded += read;
                    
                    // Log progress every 2 seconds
                    long now = System.currentTimeMillis();
                    if (now - lastLog >= 2000) {
                        String progress = formatBytes(downloaded);
                        if (contentLength > 0) {
                            progress += "/" + formatBytes(contentLength);
                        }
                        logger.info("Downloading " + label + ": " + progress);
                        lastLog = now;
                    }
                }
            }
            
            logger.info("Download complete: " + formatBytes(downloaded));
        }
    }
    
    private String formatBytes(long bytes) {
        String[] units = {"B", "KB", "MB", "GB"};
        double value = bytes;
        int idx = 0;
        while (value >= 1024 && idx < units.length - 1) {
            value /= 1024;
            idx++;
        }
        return String.format("%.1f%s", value, units[idx]);
    }
    
    private Path getCacheDir() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        
        Path cacheDir;
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                cacheDir = Paths.get(localAppData, "cloudflyer", "bin");
            } else {
                cacheDir = Paths.get(userHome, ".cloudflyer", "bin");
            }
        } else if (os.contains("mac") || os.contains("darwin")) {
            cacheDir = Paths.get(userHome, "Library", "Caches", "cloudflyer", "bin");
        } else {
            String xdgCache = System.getenv("XDG_CACHE_HOME");
            if (xdgCache != null) {
                cacheDir = Paths.get(xdgCache, "cloudflyer", "bin");
            } else {
                cacheDir = Paths.get(userHome, ".cache", "cloudflyer", "bin");
            }
        }
        
        return cacheDir;
    }
    
    private Path getVersionFilePath() {
        return getCacheDir().resolve("version.json");
    }
    
    private Map<String, String> loadInstalledVersions() {
        Path versionFile = getVersionFilePath();
        if (!Files.exists(versionFile)) {
            return new HashMap<>();
        }
        
        try {
            String content = new String(Files.readAllBytes(versionFile));
            JsonObject data = gson.fromJson(content, JsonObject.class);
            if (data != null && data.has("tool_versions")) {
                JsonObject versions = data.getAsJsonObject("tool_versions");
                Map<String, String> result = new HashMap<>();
                for (String key : versions.keySet()) {
                    result.put(key, versions.get(key).getAsString());
                }
                return result;
            }
        } catch (Exception e) {
            logger.fine("Failed to load version file: " + e.getMessage());
        }
        
        return new HashMap<>();
    }
    
    private void saveInstalledVersions(Map<String, String> versions) {
        Path versionFile = getVersionFilePath();
        
        JsonObject data = new JsonObject();
        JsonObject toolVersions = new JsonObject();
        for (Map.Entry<String, String> entry : versions.entrySet()) {
            toolVersions.addProperty(entry.getKey(), entry.getValue());
        }
        data.add("tool_versions", toolVersions);
        data.addProperty("last_updated", System.currentTimeMillis() / 1000.0);
        
        try {
            Files.createDirectories(versionFile.getParent());
            Files.write(versionFile, gson.toJson(data).getBytes());
        } catch (IOException e) {
            logger.fine("Failed to save version file: " + e.getMessage());
        }
    }
    
    private void saveInstalledVersion(String tool, String version) {
        Map<String, String> versions = loadInstalledVersions();
        versions.put(tool, version);
        saveInstalledVersions(versions);
    }
}
