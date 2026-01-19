package site.zetx.cloudflyer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import okhttp3.*;
import site.zetx.cloudflyer.exceptions.*;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * HTTP client that automatically bypasses Cloudflare challenges.
 * 
 * <p>Provides an OkHttp-based interface with automatic challenge detection and solving.
 * Uses the CloudFlyer cloud API to solve Cloudflare challenges.</p>
 * 
 * <pre>{@code
 * CloudflareSolver solver = new CloudflareSolver("your-api-key");
 * Response response = solver.get("https://protected-site.com");
 * System.out.println(response.body());
 * }</pre>
 * 
 * @author CloudFlyer Team
 * @version 0.2.0
 */
public class CloudflareSolver implements AutoCloseable {
    
    private static final Logger logger = Logger.getLogger(CloudflareSolver.class.getName());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson gson = new Gson();
    
    private final String apiKey;
    private final String apiBase;
    private final boolean solve;
    private final boolean onChallenge;
    private final boolean usePolling;
    private final int pollingInterval;
    private final int timeout;
    private final String proxy;
    private final boolean useLinkSocks;
    private final boolean useMaskTunnel;
    
    private final OkHttpClient client;
    private final OkHttpClient apiClient;
    private final LinkSocksManager linkSocksManager;
    private final MaskTunnelManager maskTunnelManager;
    private OkHttpClient maskedClient;  // Client that uses MaskTunnel proxy
    
    private final Map<String, Map<String, String>> cookies = new ConcurrentHashMap<>();
    private String userAgent;
    private Map<String, String> challengeHeaders;
    private boolean challengeSolved = false;
    
    /**
     * Create a new CloudflareSolver with default options.
     * 
     * @param apiKey Your CloudFlyer API key
     */
    public CloudflareSolver(String apiKey) {
        this(builder(apiKey));
    }
    
    private CloudflareSolver(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiBase = builder.apiBase.replaceAll("/$", "");
        this.solve = builder.solve;
        this.onChallenge = builder.onChallenge;
        this.usePolling = builder.usePolling;
        this.pollingInterval = builder.pollingInterval;
        this.timeout = builder.timeout;
        this.proxy = builder.proxy;
        this.useLinkSocks = builder.useLinkSocks;
        this.useMaskTunnel = builder.useMaskTunnel;
        
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS);
        
        if (builder.proxy != null) {
            clientBuilder.proxy(parseProxy(builder.proxy));
            if (builder.proxyAuth != null) {
                clientBuilder.proxyAuthenticator(builder.proxyAuth);
            }
        }
        this.client = clientBuilder.build();
        
        // apiProxy defaults to proxy if not set
        String effectiveApiProxy = builder.apiProxy != null ? builder.apiProxy : builder.proxy;
        
        OkHttpClient.Builder apiClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS);
        
        if (effectiveApiProxy != null) {
            apiClientBuilder.proxy(parseProxy(effectiveApiProxy));
            okhttp3.Authenticator effectiveAuth = builder.apiProxyAuth != null ? 
                    builder.apiProxyAuth : builder.proxyAuth;
            if (effectiveAuth != null) {
                apiClientBuilder.proxyAuthenticator(effectiveAuth);
            }
        }
        this.apiClient = apiClientBuilder.build();
        
        // Initialize LinkSocks manager if enabled
        if (useLinkSocks) {
            this.linkSocksManager = new LinkSocksManager(apiBase, apiKey, apiClient, proxy);
        } else {
            this.linkSocksManager = null;
        }
        
        // Initialize MaskTunnel manager for TLS fingerprint simulation (only if enabled)
        if (useMaskTunnel) {
            int maskTunnelPort = 18000 + (int)(Math.random() * 1000);
            this.maskTunnelManager = new MaskTunnelManager(maskTunnelPort, proxy, apiClient);
        } else {
            this.maskTunnelManager = null;
        }
        this.maskedClient = null;  // Will be created when MaskTunnel starts
    }
    
    /**
     * Create a new builder for CloudflareSolver.
     * 
     * @param apiKey Your CloudFlyer API key
     * @return Builder instance
     */
    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }
    
    private Proxy parseProxy(String proxyUrl) {
        try {
            URI uri = new URI(proxyUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = uri.getScheme().equals("https") ? 443 : 8080;
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        } catch (URISyntaxException e) {
            return Proxy.NO_PROXY;
        }
    }
    
    private boolean detectChallenge(Response response) {
        if (response == null) {
            return false;
        }
        
        int code = response.statusCode();
        if (code != 403 && code != 503) {
            return false;
        }
        
        String server = response.header("Server");
        if (server == null || !server.toLowerCase().contains("cloudflare")) {
            return false;
        }
        
        String body = response.body();
        if (body == null) {
            return false;
        }
        
        return body.contains("cf-turnstile") || 
               body.contains("cf-challenge") || 
               body.contains("Just a moment");
    }
    
    /**
     * Close the solver and release resources.
     */
    @Override
    public void close() {
        if (linkSocksManager != null) {
            linkSocksManager.close();
        }
        if (maskTunnelManager != null) {
            maskTunnelManager.close();
        }
    }

    private static String normalizeTaskProxy(String proxy) {
        if (proxy == null) {
            return null;
        }
        String trimmed = proxy.trim().replace("：", ":");
        return trimmed.isEmpty() ? null : trimmed;
    }
    
    private void ensureLinkSocksConnected() throws CFSolverException {
        if (linkSocksManager != null) {
            try {
                linkSocksManager.connect();
            } catch (IOException e) {
                throw new CFSolverConnectionException("Failed to connect LinkSocks: " + e.getMessage(), e);
            }
        }
    }

    private void solveChallenge(String url) throws CFSolverException {
        logger.info("Starting challenge solve: " + url);
        
        // Ensure LinkSocks is connected if enabled
        ensureLinkSocksConnected();
        
        JsonObject task = new JsonObject();
        task.addProperty("type", "CloudflareTask");
        task.addProperty("websiteURL", url);
        
        // Use LinkSocks instead of taskProxy
        if (linkSocksManager != null) {
            JsonObject linksocks = new JsonObject();
            linksocks.addProperty("url", linkSocksManager.getWsUrl());
            linksocks.addProperty("token", linkSocksManager.getConnectorToken());
            task.add("linksocks", linksocks);
        }
        
        JsonObject payload = new JsonObject();
        payload.addProperty("apiKey", apiKey);
        payload.add("task", task);
        
        RequestBody body = RequestBody.create(gson.toJson(payload), JSON);
        Request request = new Request.Builder()
                .url(apiBase + "/api/createTask")
                .post(body)
                .build();
        
        try (okhttp3.Response response = apiClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonObject data = gson.fromJson(responseBody, JsonObject.class);
            
            if (data == null) {
                throw new CFSolverChallengeException("Challenge solve failed: empty response from API");
            }
            
            if (data.has("errorId") && data.get("errorId").getAsInt() != 0) {
                String errorDesc = data.has("errorDescription") ? 
                        data.get("errorDescription").getAsString() : "Unknown error";
                throw new CFSolverChallengeException("Challenge solve failed: " + errorDesc);
            }
            
            if (!data.has("taskId")) {
                throw new CFSolverChallengeException("Challenge solve failed: no taskId returned");
            }
            
            String taskId = data.get("taskId").getAsString();
            logger.fine("Task created: " + taskId);
            
            JsonObject result = waitForResult(taskId, 120);
            
            // Extract solution
            JsonObject workerResult = result.has("result") ? 
                    result.getAsJsonObject("result") : new JsonObject();
            JsonObject solution = workerResult.has("result") && workerResult.get("result").isJsonObject() ?
                    workerResult.getAsJsonObject("result") : workerResult;
            
            // Store cookies (support object or array formats)
            if (solution.has("cookies")) {
                storeCookies(solution.get("cookies"), url);
            }
            
            // Store user agent and headers
            if (solution.has("userAgent")) {
                userAgent = solution.get("userAgent").getAsString();
            }
            if (solution.has("headers") && solution.get("headers").isJsonObject()) {
                JsonObject headers = solution.getAsJsonObject("headers");
                if (userAgent == null && headers.has("User-Agent")) {
                    userAgent = headers.get("User-Agent").getAsString();
                }
                Map<String, String> tmpHeaders = new HashMap<>();
                for (String key : headers.keySet()) {
                    tmpHeaders.put(key, headers.get(key).getAsString());
                }
                challengeHeaders = tmpHeaders;
                storeSetCookieHeaders(challengeHeaders, url);
            }
            
            // Mark challenge as solved and start MaskTunnel to align TLS fingerprint
            challengeSolved = true;
            ensureMaskTunnelStarted();
            
                String host = new URL(url).getHost();
                Map<String, String> hostCookies = getCookiesForHost(host);
                boolean hasClearance = hostCookies.containsKey("cf_clearance");
                    logger.info("Challenge solved successfully, cookies=" + hostCookies.size()
                        + ", userAgent=" + (userAgent != null)
                        + ", cf_clearance=" + hasClearance
                        + ", cookieKeys=" + hostCookies.keySet()
                        + ", headerKeys=" + (challengeHeaders != null ? challengeHeaders.keySet() : "[]"));
            
        } catch (IOException e) {
            throw new CFSolverConnectionException("Failed to connect to API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Ensure MaskTunnel is started for TLS fingerprint simulation.
     */
    private void ensureMaskTunnelStarted() throws CFSolverException {
        if (!useMaskTunnel || maskTunnelManager == null) {
            return;  // MaskTunnel disabled
        }
        
        if (maskedClient != null) {
            return;  // Already started
        }
        
        try {
            maskTunnelManager.start();
            
            // Create a new OkHttpClient that uses MaskTunnel as proxy
            OkHttpClient.Builder maskedBuilder = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                    .proxy(parseProxy(maskTunnelManager.getProxyUrl()));
            
            // Trust all certificates since MaskTunnel does MITM
            try {
                javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
                };
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                maskedBuilder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAllCerts[0]);
                maskedBuilder.hostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                logger.warning("Failed to configure SSL trust: " + e.getMessage());
            }
            
            maskedClient = maskedBuilder.build();
            logger.info("MaskTunnel client ready for TLS fingerprint simulation");
            
        } catch (IOException e) {
            throw new CFSolverConnectionException("Failed to start MaskTunnel: " + e.getMessage(), e);
        }
    }

    private void storeCookies(com.google.gson.JsonElement cookiesElement, String url) throws MalformedURLException {
        if (cookiesElement == null || cookiesElement.isJsonNull()) {
            return;
        }
        String defaultDomain = new URL(url).getHost();

        if (cookiesElement.isJsonObject()) {
            JsonObject cookiesObj = cookiesElement.getAsJsonObject();
            Map<String, String> domainCookies = getOrCreateCookieBucket(defaultDomain);
            for (String key : cookiesObj.keySet()) {
                domainCookies.put(key, cookiesObj.get(key).getAsString());
            }
            return;
        }

        if (cookiesElement.isJsonArray()) {
            com.google.gson.JsonArray cookiesArray = cookiesElement.getAsJsonArray();
            for (int i = 0; i < cookiesArray.size(); i++) {
                if (!cookiesArray.get(i).isJsonObject()) {
                    continue;
                }
                JsonObject item = cookiesArray.get(i).getAsJsonObject();
                if (item.has("name") && item.has("value")) {
                    String cookieDomain = defaultDomain;
                    if (item.has("domain")) {
                        cookieDomain = item.get("domain").getAsString();
                    }
                    if (cookieDomain != null && cookieDomain.startsWith(".")) {
                        cookieDomain = cookieDomain.substring(1);
                    }
                    Map<String, String> domainCookies = getOrCreateCookieBucket(cookieDomain != null ? cookieDomain : defaultDomain);
                    domainCookies.put(item.get("name").getAsString(), item.get("value").getAsString());
                }
            }
        }
    }

    private void storeSetCookieHeaders(Map<String, String> headers, String url) throws MalformedURLException {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        String setCookieKey = null;
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase("Set-Cookie")) {
                setCookieKey = key;
                break;
            }
        }
        if (setCookieKey == null) {
            return;
        }
        String value = headers.get(setCookieKey);
        if (value == null || value.isEmpty()) {
            return;
        }
        HttpUrl httpUrl = HttpUrl.get(url);
        if (httpUrl == null) {
            return;
        }
        for (String cookieHeader : splitSetCookie(value)) {
            Cookie cookie = Cookie.parse(httpUrl, cookieHeader);
            if (cookie == null) {
                continue;
            }
            String domain = cookie.domain();
            if (domain != null && domain.startsWith(".")) {
                domain = domain.substring(1);
            }
            Map<String, String> domainCookies = getOrCreateCookieBucket(domain != null ? domain : httpUrl.host());
            domainCookies.put(cookie.name(), cookie.value());
        }
    }

    private java.util.List<String> splitSetCookie(String headerValue) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (headerValue.contains("\n")) {
            for (String line : headerValue.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    parts.add(trimmed);
                }
            }
            return parts;
        }
        Cookie test = Cookie.parse(HttpUrl.get("http://localhost"), headerValue);
        if (test != null) {
            parts.add(headerValue);
            return parts;
        }
        if (headerValue.contains(",")) {
            String[] tokens = headerValue.split(",");
            StringBuilder current = new StringBuilder();
            for (String token : tokens) {
                if (current.length() > 0) {
                    current.append(",");
                }
                current.append(token);
                String candidate = current.toString().trim();
                Cookie parsed = Cookie.parse(HttpUrl.get("http://localhost"), candidate);
                if (parsed != null) {
                    parts.add(candidate);
                    current.setLength(0);
                }
            }
            if (current.length() > 0) {
                parts.add(current.toString().trim());
            }
            return parts;
        }
        parts.add(headerValue);
        return parts;
    }

    private Map<String, String> getOrCreateCookieBucket(String domain) {
        if (domain == null || domain.isEmpty()) {
            return new ConcurrentHashMap<>();
        }
        cookies.putIfAbsent(domain, new ConcurrentHashMap<>());
        return cookies.get(domain);
    }

    private Map<String, String> getCookiesForHost(String host) {
        Map<String, String> result = new HashMap<>();
        if (host == null || host.isEmpty()) {
            return result;
        }
        Map<String, String> exact = cookies.get(host);
        if (exact != null) {
            result.putAll(exact);
        }
        for (Map.Entry<String, Map<String, String>> entry : cookies.entrySet()) {
            String domain = entry.getKey();
            if (domain == null || domain.isEmpty()) {
                continue;
            }
            if (host.equals(domain) || host.endsWith("." + domain)) {
                result.putAll(entry.getValue());
            }
        }
        return result;
    }
    
    private JsonObject waitForResult(String taskId, int timeout) throws CFSolverException {
        long start = System.currentTimeMillis();
        
        while ((System.currentTimeMillis() - start) / 1000 < timeout) {
            String endpoint = usePolling ? 
                    apiBase + "/api/getTaskResult" : 
                    apiBase + "/api/waitTaskResult";
            
            int requestTimeout = usePolling ? 30000 : 
                    (int) Math.min((timeout - (System.currentTimeMillis() - start) / 1000 + 10) * 1000, 310000);
            
            JsonObject payload = new JsonObject();
            payload.addProperty("apiKey", apiKey);
            payload.addProperty("taskId", taskId);
            
            RequestBody body = RequestBody.create(gson.toJson(payload), JSON);
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .build();
            
            OkHttpClient pollClient = apiClient.newBuilder()
                    .readTimeout(requestTimeout, TimeUnit.MILLISECONDS)
                    .build();
            
            try (okhttp3.Response response = pollClient.newCall(request).execute()) {
                if (response.code() != 200) {
                    if (usePolling) {
                        Thread.sleep(pollingInterval);
                    }
                    continue;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject result = gson.fromJson(responseBody, JsonObject.class);
                String status = result.has("status") ? result.get("status").getAsString() : "";
                
                if ("processing".equals(status)) {
                    if (usePolling) {
                        Thread.sleep(pollingInterval);
                    }
                    continue;
                }
                
                if ("timeout".equals(status)) {
                    continue;
                }
                
                boolean success;
                if (result.has("success")) {
                    success = result.get("success").getAsBoolean();
                } else {
                    success = ("completed".equals(status) || "ready".equals(status)) && 
                              (!result.has("error") || result.get("error").isJsonNull() || 
                               result.get("error").getAsString().isEmpty());
                }
                
                if (!success) {
                    String error = "Unknown error";
                    if (result.has("error") && !result.get("error").isJsonNull()) {
                        error = result.get("error").getAsString();
                    } else if (result.has("result") && result.get("result").isJsonObject()) {
                        JsonObject r = result.getAsJsonObject("result");
                        if (r.has("error")) {
                            error = r.get("error").getAsString();
                        }
                    }
                    throw new CFSolverChallengeException("Task failed: " + error);
                }
                
                return result;
                
            } catch (IOException e) {
                if (usePolling) {
                    try {
                        Thread.sleep(pollingInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CFSolverTimeoutException("Task interrupted");
            }
        }
        
        throw new CFSolverTimeoutException("Task timed out");
    }
    
    /**
     * Solve a Turnstile challenge and return the token.
     * 
     * @param url The website URL containing the Turnstile widget
     * @param sitekey The Turnstile sitekey
     * @return The solved Turnstile token
     * @throws CFSolverException if solving fails
     */
    public String solveTurnstile(String url, String sitekey) throws CFSolverException {
        logger.info("Starting Turnstile solve: " + url);
        
        // Ensure LinkSocks is connected if enabled
        ensureLinkSocksConnected();
        
        JsonObject task = new JsonObject();
        task.addProperty("type", "TurnstileTask");
        task.addProperty("websiteURL", url);
        task.addProperty("websiteKey", sitekey);
        
        // Use LinkSocks instead of taskProxy
        if (linkSocksManager != null) {
            JsonObject linksocks = new JsonObject();
            linksocks.addProperty("url", linkSocksManager.getWsUrl());
            linksocks.addProperty("token", linkSocksManager.getConnectorToken());
            task.add("linksocks", linksocks);
        }
        
        JsonObject payload = new JsonObject();
        payload.addProperty("apiKey", apiKey);
        payload.add("task", task);
        
        RequestBody body = RequestBody.create(gson.toJson(payload), JSON);
        Request request = new Request.Builder()
                .url(apiBase + "/api/createTask")
                .post(body)
                .build();
        
        try (okhttp3.Response response = apiClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonObject data = gson.fromJson(responseBody, JsonObject.class);
            
            if (data.has("errorId") && data.get("errorId").getAsInt() != 0) {
                String errorDesc = data.has("errorDescription") ? 
                        data.get("errorDescription").getAsString() : "Unknown error";
                throw new CFSolverChallengeException("Turnstile solve failed: " + errorDesc);
            }
            
            if (!data.has("taskId")) {
                throw new CFSolverChallengeException("Turnstile solve failed: no taskId returned");
            }
            
            String taskId = data.get("taskId").getAsString();
            JsonObject result = waitForResult(taskId, 120);
            
            JsonObject workerResult = result.has("result") ? 
                    result.getAsJsonObject("result") : new JsonObject();
            JsonObject solution = workerResult.has("result") && workerResult.get("result").isJsonObject() ?
                    workerResult.getAsJsonObject("result") : workerResult;
            
            if (!solution.has("token")) {
                throw new CFSolverChallengeException("Turnstile solve failed: no token returned");
            }
            
            logger.info("Turnstile solved successfully");
            return solution.get("token").getAsString();
            
        } catch (IOException e) {
            throw new CFSolverConnectionException("Failed to connect to API: " + e.getMessage(), e);
        }
    }
    
    private Request.Builder buildRequest(String url, Map<String, String> headers) throws MalformedURLException {
        Request.Builder builder = new Request.Builder().url(url);
        
        String domain = new URL(url).getHost();
        Map<String, String> domainCookies = getCookiesForHost(domain);
        if (!domainCookies.isEmpty()) {
            StringBuilder cookieStr = new StringBuilder();
            for (Map.Entry<String, String> entry : domainCookies.entrySet()) {
                if (cookieStr.length() > 0) {
                    cookieStr.append("; ");
                }
                cookieStr.append(entry.getKey()).append("=").append(entry.getValue());
            }
            builder.header("Cookie", cookieStr.toString());
        }
        
        if (userAgent != null) {
            builder.header("User-Agent", userAgent);
        }
        
        if (challengeHeaders != null && !challengeHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : challengeHeaders.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                String lower = key.toLowerCase();
                if (lower.equals("cookie") || lower.equals("user-agent")) {
                    continue;
                }
                builder.header(key, entry.getValue());
            }
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        
        return builder;
    }
    
    private Response executeRequest(Request request) throws CFSolverException {
        // Use maskedClient (through MaskTunnel) if challenge was solved, otherwise use regular client
        OkHttpClient effectiveClient = (challengeSolved && maskedClient != null) ? maskedClient : client;
        
        try (okhttp3.Response response = effectiveClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            
            Map<String, String> headers = new HashMap<>();
            for (String name : response.headers().names()) {
                headers.put(name, response.header(name));
            }
            
            return new Response(response.code(), body, headers);
        } catch (IOException e) {
            throw new CFSolverConnectionException("Request failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Make a GET request.
     * 
     * @param url The URL to request
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response get(String url) throws CFSolverException {
        return get(url, null);
    }
    
    /**
     * Make a GET request with custom headers.
     * 
     * @param url The URL to request
     * @param headers Custom headers
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response get(String url, Map<String, String> headers) throws CFSolverException {
        return request("GET", url, null, headers);
    }
    
    /**
     * Make a POST request.
     * 
     * @param url The URL to request
     * @param body Request body (will be JSON encoded if Map)
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response post(String url, Object body) throws CFSolverException {
        return post(url, body, null);
    }
    
    /**
     * Make a POST request with custom headers.
     * 
     * @param url The URL to request
     * @param body Request body (will be JSON encoded if Map)
     * @param headers Custom headers
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response post(String url, Object body, Map<String, String> headers) throws CFSolverException {
        return request("POST", url, body, headers);
    }
    
    /**
     * Make a PUT request.
     * 
     * @param url The URL to request
     * @param body Request body
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response put(String url, Object body) throws CFSolverException {
        return put(url, body, null);
    }
    
    /**
     * Make a PUT request with custom headers.
     * 
     * @param url The URL to request
     * @param body Request body
     * @param headers Custom headers
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response put(String url, Object body, Map<String, String> headers) throws CFSolverException {
        return request("PUT", url, body, headers);
    }
    
    /**
     * Make a DELETE request.
     * 
     * @param url The URL to request
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response delete(String url) throws CFSolverException {
        return delete(url, null);
    }
    
    /**
     * Make a DELETE request with custom headers.
     * 
     * @param url The URL to request
     * @param headers Custom headers
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response delete(String url, Map<String, String> headers) throws CFSolverException {
        return request("DELETE", url, null, headers);
    }
    
    /**
     * Make a PATCH request.
     * 
     * @param url The URL to request
     * @param body Request body
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response patch(String url, Object body) throws CFSolverException {
        return patch(url, body, null);
    }
    
    /**
     * Make a PATCH request with custom headers.
     * 
     * @param url The URL to request
     * @param body Request body
     * @param headers Custom headers
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response patch(String url, Object body, Map<String, String> headers) throws CFSolverException {
        return request("PATCH", url, body, headers);
    }
    
    /**
     * Make an HTTP request with automatic challenge bypass.
     * 
     * @param method HTTP method
     * @param url The URL to request
     * @param body Request body (can be null)
     * @param headers Custom headers (can be null)
     * @return Response object
     * @throws CFSolverException if request fails
     */
    public Response request(String method, String url, Object body, Map<String, String> headers) 
            throws CFSolverException {
        try {
            if (!solve) {
                return executeSimpleRequest(method, url, body, headers);
            }
            
            if (!onChallenge) {
                // Pre-solve mode: solve challenge first, then make request
                try {
                    solveChallenge(url);
                } catch (Exception e) {
                    logger.warning("Pre-solve failed: " + e.getMessage());
                }
                return executeSimpleRequest(method, url, body, headers);
            }
            
            Response response = executeSimpleRequest(method, url, body, headers);
            
            if (onChallenge && detectChallenge(response)) {
                logger.info("Cloudflare challenge detected, solving...");
                // Solve challenge to get cookies/UA, then retry request
                solveChallenge(url);
                response = executeSimpleRequest(method, url, body, headers);
                if (detectChallenge(response)) {
                    logger.info("Challenge persists after retry, starting MaskTunnel...");
                    ensureMaskTunnelStarted();
                    response = executeSimpleRequest(method, url, body, headers);
                }
            }
            
            return response;
            
        } catch (MalformedURLException e) {
            throw new CFSolverAPIException("Invalid URL: " + url, e);
        }
    }
    
    private Response executeSimpleRequest(String method, String url, Object body, Map<String, String> headers) 
            throws CFSolverException, MalformedURLException {
        Request.Builder builder = buildRequest(url, headers);
        
        RequestBody requestBody = null;
        if (body != null) {
            String jsonBody = body instanceof String ? (String) body : gson.toJson(body);
            requestBody = RequestBody.create(jsonBody, JSON);
        }
        
        switch (method.toUpperCase()) {
            case "GET":
                builder.get();
                break;
            case "POST":
                builder.post(requestBody != null ? requestBody : RequestBody.create("", null));
                break;
            case "PUT":
                builder.put(requestBody != null ? requestBody : RequestBody.create("", null));
                break;
            case "DELETE":
                if (requestBody != null) {
                    builder.delete(requestBody);
                } else {
                    builder.delete();
                }
                break;
            case "PATCH":
                builder.patch(requestBody != null ? requestBody : RequestBody.create("", null));
                break;
            case "HEAD":
                builder.head();
                break;
            default:
                builder.method(method, requestBody);
        }
        
        return executeRequest(builder.build());
    }
    
    /**
     * Builder for CloudflareSolver.
     */
    public static class Builder {
        private final String apiKey;
        private String apiBase = "https://solver.zetx.site";
        private boolean solve = true;
        private boolean onChallenge = true;
        private boolean usePolling = false;
        private int pollingInterval = 2000;
        private int timeout = 30000;
        private String proxy;
        private okhttp3.Authenticator proxyAuth;
        private String apiProxy;
        private okhttp3.Authenticator apiProxyAuth;
        private boolean useLinkSocks = true;  // Default to true for client network environment
        private boolean useMaskTunnel = false;  // Default to false, enable for TLS fingerprint simulation
        
        private Builder(String apiKey) {
            this.apiKey = apiKey;
        }
        
        /**
         * Set the CloudFlyer API base URL.
         * 
         * @param apiBase API base URL
         * @return this builder
         */
        public Builder apiBase(String apiBase) {
            this.apiBase = apiBase;
            return this;
        }
        
        /**
         * Enable or disable automatic challenge solving.
         * 
         * @param solve true to enable
         * @return this builder
         */
        public Builder solve(boolean solve) {
            this.solve = solve;
            return this;
        }
        
        /**
         * Set whether to solve only when challenge is detected.
         * 
         * @param onChallenge true to solve on-demand, false to pre-solve
         * @return this builder
         */
        public Builder onChallenge(boolean onChallenge) {
            this.onChallenge = onChallenge;
            return this;
        }
        
        /**
         * Set whether to use interval polling instead of long-polling.
         * 
         * @param usePolling true to use interval polling
         * @return this builder
         */
        public Builder usePolling(boolean usePolling) {
            this.usePolling = usePolling;
            return this;
        }
        
        /**
         * Set the interval between polling attempts when usePolling is true.
         * 
         * @param pollingInterval interval in milliseconds (default: 2000)
         * @return this builder
         */
        public Builder pollingInterval(int pollingInterval) {
            this.pollingInterval = pollingInterval;
            return this;
        }
        
        /**
         * Set the request timeout in milliseconds.
         * 
         * @param timeout timeout in milliseconds
         * @return this builder
         */
        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }
        
        /**
         * Set the HTTP proxy for user requests.
         * 
         * @param proxy proxy URL (e.g., "http://host:port")
         * @return this builder
         */
        public Builder proxy(String proxy) {
            this.proxy = proxy;
            return this;
        }
        
        /**
         * Set the HTTP proxy with authentication for user requests.
         * 
         * @param proxy proxy URL
         * @param username proxy username
         * @param password proxy password
         * @return this builder
         */
        public Builder proxy(String proxy, String username, String password) {
            this.proxy = proxy;
            this.proxyAuth = (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            return this;
        }
        
        /**
         * Set the HTTP proxy for API requests.
         * 
         * @param apiProxy proxy URL
         * @return this builder
         */
        public Builder apiProxy(String apiProxy) {
            this.apiProxy = apiProxy;
            return this;
        }
        
        /**
         * Set the HTTP proxy with authentication for API requests.
         * 
         * @param apiProxy proxy URL
         * @param username proxy username
         * @param password proxy password
         * @return this builder
         */
        public Builder apiProxy(String apiProxy, String username, String password) {
            this.apiProxy = apiProxy;
            this.apiProxyAuth = (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            return this;
        }

        /**
         * Enable or disable LinkSocks for client network environment.
         * When enabled, the solver will download and run LinkSocks as a provider,
         * allowing the API to make requests through the client's network.
         * 
         * @param useLinkSocks true to enable LinkSocks (default: true)
         * @return this builder
         */
        public Builder useLinkSocks(boolean useLinkSocks) {
            this.useLinkSocks = useLinkSocks;
            return this;
        }
        
        /**
         * Enable or disable MaskTunnel for TLS fingerprint simulation.
         * When enabled, requests after challenge solving will go through MaskTunnel
         * to simulate browser TLS fingerprints (JA3/JA4).
         * 
         * @param useMaskTunnel true to enable MaskTunnel (default: false)
         * @return this builder
         */
        public Builder useMaskTunnel(boolean useMaskTunnel) {
            this.useMaskTunnel = useMaskTunnel;
            return this;
        }
        
        /**
         * Build the CloudflareSolver instance.
         * 
         * @return CloudflareSolver instance
         */
        public CloudflareSolver build() {
            return new CloudflareSolver(this);
        }
    }
}
