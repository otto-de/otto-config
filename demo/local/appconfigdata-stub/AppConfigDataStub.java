/*
 * Tiny stand-in for the AWS AppConfigData data-plane API. Moto does not
 * implement `appconfigdata` at all, so this stub is enough to let Otto
 * Config's AppConfigSource pull hosted configuration content from local
 * JSON files.
 *
 * Runs as a JEP 330 single-file source launch:
 *
 *     java AppConfigDataStub.java
 *
 * No third-party dependencies — only the JDK's built-in HTTP server.
 *
 * Implements just enough of the API surface that AppConfigDataClient uses:
 *
 *     POST /configurationsessions
 *         body: {"ApplicationIdentifier": "...",
 *                "EnvironmentIdentifier": "...",
 *                "ConfigurationProfileIdentifier": "properties" | "toggles"}
 *         response: {"InitialConfigurationToken": "<opaque token>"}
 *
 *     GET  /configuration?configuration_token=<opaque token>
 *         response body: raw JSON content
 *         response headers:
 *             Content-Type: application/json
 *             Next-Poll-Configuration-Token: <opaque token>
 *             Next-Poll-Interval-In-Seconds: 30
 *
 * The profile identifier is encoded into the token so the GET can look up
 * the right file. File mtime is tracked per token so a subsequent GET
 * returns an empty body (which Otto Config treats as "no change") until
 * the underlying JSON on disk changes.
 */
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppConfigDataStub {

    private static final Map<String, String> PROFILE_TO_FILE = Map.of(
            "properties", "appconfig_properties.json",
            "toggles",    "appconfig_toggles.json"
    );

    private static final ConcurrentHashMap<String, Long> LAST_SEEN_MTIME = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        String host = envOrDefault("APPCONFIGDATA_STUB_HOST", "0.0.0.0");
        int port    = Integer.parseInt(envOrDefault("APPCONFIGDATA_STUB_PORT", "5001"));
        Path dataDir = Path.of(envOrDefault("APPCONFIGDATA_STUB_DATA_DIR", "/data"));

        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/configurationsessions", new StartSessionHandler());
        server.createContext("/configuration",         new GetConfigurationHandler(dataDir));
        server.setExecutor(null);
        server.start();
        System.out.printf("[appconfigdata-stub] listening on http://%s:%d, data dir = %s%n", host, port, dataDir);
    }

    static class StartSessionHandler implements HttpHandler {
        private static final Pattern PROFILE_FIELD =
                Pattern.compile("\"ConfigurationProfileIdentifier\"\\s*:\\s*\"([^\"]+)\"");

        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 405, "{}"); return; }
            String body;
            try (InputStream in = ex.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher m = PROFILE_FIELD.matcher(body);
            if (!m.find()) { respondJson(ex, 400, "{\"Message\":\"ConfigurationProfileIdentifier missing\"}"); return; }
            String profile = m.group(1);
            if (!PROFILE_TO_FILE.containsKey(profile)) {
                respondJson(ex, 404, "{\"Message\":\"unknown profile '" + profile + "'\"}");
                return;
            }
            String token = encodeToken(profile);
            respondJson(ex, 200, "{\"InitialConfigurationToken\":\"" + token + "\"}");
        }
    }

    static class GetConfigurationHandler implements HttpHandler {
        private final Path dataDir;
        GetConfigurationHandler(Path dataDir) { this.dataDir = dataDir; }

        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 405, "{}"); return; }
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String token = q.get("configuration_token");
            if (token == null || token.isBlank()) {
                respondJson(ex, 400, "{\"Message\":\"configuration_token required\"}");
                return;
            }
            String profile = decodeToken(token);
            if (profile == null) {
                respondJson(ex, 400, "{\"Message\":\"invalid token\"}");
                return;
            }
            String filename = PROFILE_TO_FILE.get(profile);
            if (filename == null) {
                respondJson(ex, 404, "{\"Message\":\"profile '" + profile + "' not found\"}");
                return;
            }
            Path file = dataDir.resolve(filename);
            if (!Files.isRegularFile(file)) {
                respondJson(ex, 404, "{\"Message\":\"file for profile '" + profile + "' not on disk\"}");
                return;
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            Long previous = LAST_SEEN_MTIME.get(token);
            String nextToken = encodeToken(profile);
            LAST_SEEN_MTIME.put(nextToken, mtime);
            ex.getResponseHeaders().add("Next-Poll-Configuration-Token", nextToken);
            ex.getResponseHeaders().add("Next-Poll-Interval-In-Seconds", "30");
            if (previous != null && previous == mtime) {
                // No change since last poll — Otto Config treats an empty body as "no update".
                respondBytes(ex, 200, new byte[0], "application/json");
                return;
            }
            byte[] content = Files.readAllBytes(file);
            // For an AWS.AppConfig.FeatureFlags profile the AppConfigData service
            // returns only the resolved flag values (e.g. {"my_flag":{"enabled":true}}),
            // not the full hosted document with 'flags' / 'values' / 'version' metadata.
            // Otto Config's Toggles JsonCreator expects that resolved shape, so we
            // strip the wrapper here for the toggles profile.
            if ("toggles".equals(profile)) {
                content = extractFeatureFlagValues(content);
            }
            respondBytes(ex, 200, content, "application/json");
        }
    }

    /**
     * Given the raw contents of an AppConfig FeatureFlags hosted configuration
     * (shape: <code>{"flags": {...}, "values": {...}, "version": "1"}</code>),
     * return just the bytes of the <code>values</code> object, matching what
     * the real AWS AppConfigData service returns. Falls back to the input
     * bytes if the wrapper isn't recognised.
     */
    static byte[] extractFeatureFlagValues(byte[] hostedJson) {
        String s = new String(hostedJson, StandardCharsets.UTF_8);
        // Find "values" : { ... balanced ... }
        Matcher key = Pattern.compile("\"values\"\\s*:\\s*\\{").matcher(s);
        if (!key.find()) return hostedJson;
        int start = key.end() - 1; // index of the opening '{'
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1).getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        return hostedJson;
    }

    // -------------------------------------------------------------------- helpers

    private static String encodeToken(String profile) {
        String payload = "{\"p\":\"" + profile + "\",\"n\":\"" + UUID.randomUUID() + "\"}";
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeToken(String token) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(token);
            String s = new String(raw, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"p\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
            return m.find() ? m.group(1) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) out.put(pair, "");
            else out.put(pair.substring(0, eq), java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        respondBytes(ex, status, body.getBytes(StandardCharsets.UTF_8), null);
    }
    private static void respondJson(HttpExchange ex, int status, String body) throws IOException {
        respondBytes(ex, status, body.getBytes(StandardCharsets.UTF_8), "application/json");
    }
    private static void respondBytes(HttpExchange ex, int status, byte[] body, String contentType) throws IOException {
        if (contentType != null) ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(status, body.length);
        if (body.length > 0) {
            ex.getResponseBody().write(body);
        }
        ex.getResponseBody().close();
        System.out.printf("[appconfigdata-stub] %s %s -> %d (%d bytes)%n",
                ex.getRequestMethod(), ex.getRequestURI(), status, body.length);
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
