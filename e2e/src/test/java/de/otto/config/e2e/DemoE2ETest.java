package de.otto.config.e2e;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests for all four otto-config demos (Spring, Helidon, plain
 * Java, and plain Go).
 *
 * The docker-compose stack under {@code demo/local/} (moto + vault +
 * appconfigdata-stub) is started once per test class. Each {@code @Test}
 * spawns one demo as a subprocess (rather than in-process): the AWS SDK v2
 * reads its endpoint-override configuration from real environment variables
 * ({@code AWS_ENDPOINT_URL}, {@code AWS_ENDPOINT_URL_APPCONFIGDATA}), which
 * cannot be simulated via {@code System.setProperty} once the JVM is up.
 *
 * Each demo exposes the same set of fixture values under {@code GET /config};
 * this test asserts on that stable payload.
 *
 * Requires {@code docker} + {@code docker compose} on PATH. Intentionally
 * not part of {@code check}; invoke with {@code ./gradlew :e2e:test -PrunE2E}.
 */
class DemoE2ETest {

    private static final Path REPO_ROOT = repoRoot();
    private static final Path COMPOSE_FILE = REPO_ROOT.resolve("demo/local/docker-compose.yml");
    private static final Path ENV_FILE = REPO_ROOT.resolve("demo/local/.env");

    private static Map<String, String> envFromFile;

    @BeforeAll
    static void beforeAll() throws Exception {
        // Fresh state so waitForEnvFile detects the *new* file rather than a stale one.
        Files.deleteIfExists(ENV_FILE);

        dockerCompose("up", "-d");
        waitForInfraReady();
        waitForEnvFile();

        envFromFile = parseEnvFile(ENV_FILE);
    }

    @AfterAll
    static void afterAll() {
        try {
            dockerCompose("down", "-v");
        } catch (Exception e) {
            System.err.println("docker compose down failed: " + e.getMessage());
        }
    }

    @Test
    void springDemoConfigEndpointReturnsSeededValues() throws Exception {
        String jarPath = requireSystemProperty("e2e.spring.jar");
        if (!Files.isRegularFile(Paths.get(jarPath))) {
            throw new IllegalStateException("Spring boot jar not found at " + jarPath);
        }
        int port = pickFreePort();
        List<String> cmd = List.of(
                javaBinary(), "-jar", jarPath,
                "--spring.profiles.active=moto",
                "--server.port=" + port);
        runAgainstDemo("spring", cmd, envFromFile, port);
    }

    @Test
    void helidonDemoConfigEndpointReturnsSeededValues() throws Exception {
        Path installDir = Paths.get(requireSystemProperty("e2e.helidon.install.dir"));
        Path launcher = installDir.resolve("bin/helidon");
        if (!Files.isRegularFile(launcher)) {
            throw new IllegalStateException("Helidon launcher not found at " + launcher);
        }
        int port = pickFreePort();

        Map<String, String> env = new LinkedHashMap<>(envFromFile);
        // Helidon MP reads mp.config.profile / server.port as system properties;
        // route them through JAVA_OPTS so the start script forwards them.
        env.put("JAVA_OPTS",
                "-Dmp.config.profile=moto "
                + "-Dserver.port=" + port + " "
                + "-Dlogback.configurationFile=logback-local.xml "
                + "-Dotto.config.aws.secrets.arn=otto-config "
                + "-Dapp.ssm.paths=/search/develop/otto-config/config/");

        List<String> cmd = List.of(launcher.toAbsolutePath().toString());
        runAgainstDemo("helidon", cmd, env, port);
    }

    @Test
    void plainJavaDemoConfigEndpointReturnsSeededValues() throws Exception {
        Path installDir = Paths.get(requireSystemProperty("e2e.java.install.dir"));
        Path launcher = installDir.resolve("bin/java");
        if (!Files.isRegularFile(launcher)) {
            throw new IllegalStateException("Plain Java launcher not found at " + launcher);
        }
        int port = pickFreePort();

        Map<String, String> env = new LinkedHashMap<>(envFromFile);
        env.put("SERVER_PORT", Integer.toString(port));
        env.put("OTTO_CONFIG_PROFILE", "moto");

        List<String> cmd = List.of(launcher.toAbsolutePath().toString());
        runAgainstDemo("java", cmd, env, port);
    }

    @Test
    void goDemoConfigEndpointReturnsSeededValues() throws Exception {
        Path binary = Paths.get(requireSystemProperty("e2e.go.binary"));
        if (!Files.isExecutable(binary)) {
            throw new IllegalStateException("Go demo binary not found or not executable at " + binary);
        }
        int port = pickFreePort();

        Map<String, String> env = new LinkedHashMap<>(envFromFile);
        env.put("SERVER_PORT", Integer.toString(port));
        env.put("OTTO_CONFIG_PROFILE", "moto");

        List<String> cmd = List.of(binary.toAbsolutePath().toString());
        runAgainstDemo("go", cmd, env, port);
    }

    // ------------------------------------------------------------------
    // Demo runner
    // ------------------------------------------------------------------

    private static void runAgainstDemo(String label, List<String> cmd, Map<String, String> env, int port)
            throws Exception {
        Process p = null;
        Thread logThread = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(REPO_ROOT.toFile())
                    .redirectErrorStream(true);
            pb.environment().putAll(env);
            System.out.println("[e2e] launching " + label + " demo on port " + port
                    + " with env keys " + env.keySet());
            p = pb.start();
            logThread = drainAsync(p.getInputStream(), "[" + label + "]");

            waitForAppReady("http://localhost:" + port + "/config", p, label);

            assertConfigPayload("http://localhost:" + port + "/config");
        } finally {
            if (p != null) {
                p.destroy();
                if (!p.waitFor(30, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    p.waitFor(15, TimeUnit.SECONDS);
                }
            }
            if (logThread != null) {
                try { logThread.join(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
    }

    private static void assertConfigPayload(String url) {
        await().atMost(Duration.ofSeconds(60))
               .pollInterval(Duration.ofSeconds(1))
               .untilAsserted(() -> {
                   Map<String, Object> body = fetchJson(url);

                   assertThat(body).containsEntry("myKey1", "myValue");
                   assertThat(body).containsEntry("myKey2", "myValue1;myValue2");
                   assertThat(body).containsEntry("logging.enabled", "true");
                   assertThat(body).containsEntry("logging_enabled", false);
                   assertThat(body).containsEntry("s3_toggle1", true);
                   assertThat(body).containsEntry("s3_toggle2", false);
                   assertThat(body).containsEntry("some_secret", "some very secret value");
                   assertThat(body).containsEntry("some_ssm_value", "hello-from-ssm");
               });
    }

    private static void waitForAppReady(String url, Process process, String label) {
        await().alias("waiting for " + label + " demo at " + url)
               .atMost(Duration.ofMinutes(3))
               .pollInterval(Duration.ofSeconds(2))
               .until(() -> {
                   if (!process.isAlive()) {
                       throw new IllegalStateException(label + " demo died before becoming ready");
                   }
                   try {
                       HttpURLConnection c = openGet(url);
                       int code = c.getResponseCode();
                       c.disconnect();
                       return code == 200;
                   } catch (IOException e) {
                       return false;
                   }
               });
    }

    // ------------------------------------------------------------------
    // Env / process helpers
    // ------------------------------------------------------------------

    private static String requireSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("System property '" + name + "' is not set. "
                    + "Ensure e2e/build.gradle wires the corresponding install/jar task.");
        }
        return value;
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        return Paths.get(home, "bin", "java").toString();
    }

    private static int pickFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static Thread drainAsync(InputStream in, String prefix) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println(prefix + " " + line);
                }
            } catch (IOException ignored) {
            }
        }, "demo-log-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    private static HttpURLConnection openGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(2000);
        c.setReadTimeout(5000);
        c.setRequestMethod("GET");
        return c;
    }

    /**
     * Minimal JSON-object parser for the small, well-known payload each demo's
     * {@code /config} endpoint returns. Values are strings ("..."), booleans
     * (true/false), or the literal null.
     */
    private static Map<String, Object> fetchJson(String url) throws IOException {
        HttpURLConnection c = openGet(url);
        try (InputStream in = c.getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return parseFlatJsonObject(body);
        } finally {
            c.disconnect();
        }
    }

    private static Map<String, Object> parseFlatJsonObject(String body) {
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new IllegalStateException("Expected JSON object, got: " + body);
        }
        String inner = body.substring(1, body.length() - 1).trim();
        Map<String, Object> out = new LinkedHashMap<>();
        if (inner.isEmpty()) return out;

        for (String entry : splitTopLevel(inner)) {
            int colon = findTopLevelColon(entry);
            String key = unquote(entry.substring(0, colon).trim());
            String rawValue = entry.substring(colon + 1).trim();
            out.put(key, parseValue(rawValue));
        }
        return out;
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        boolean inQuotes = false;
        boolean escape = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (ch == '\\' && inQuotes) { escape = true; continue; }
            if (ch == '"') { inQuotes = !inQuotes; continue; }
            if (ch == ',' && !inQuotes) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    private static int findTopLevelColon(String s) {
        boolean inQuotes = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (ch == '\\' && inQuotes) { escape = true; continue; }
            if (ch == '"') { inQuotes = !inQuotes; continue; }
            if (ch == ':' && !inQuotes) return i;
        }
        throw new IllegalStateException("No ':' found in JSON entry: " + s);
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private static Object parseValue(String raw) {
        if (raw.equals("null")) return null;
        if (raw.equals("true")) return true;
        if (raw.equals("false")) return false;
        if (raw.startsWith("\"") && raw.endsWith("\"")) return unquote(raw);
        return raw;
    }

    // ------------------------------------------------------------------
    // docker-compose helpers
    // ------------------------------------------------------------------

    private static void dockerCompose(String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("compose");
        cmd.add("-f");
        cmd.add(COMPOSE_FILE.toString());
        Collections.addAll(cmd, args);

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(REPO_ROOT.toFile())
                .redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("[docker compose] " + line);
            }
        }
        boolean finished = p.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            p.destroyForcibly();
            throw new IllegalStateException("docker compose " + String.join(" ", args) + " timed out");
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("docker compose " + String.join(" ", args)
                    + " exited with " + p.exitValue());
        }
    }

    private static void waitForInfraReady() {
        record Endpoint(String name, String url, int minCode, int maxCode) {}
        List<Endpoint> endpoints = List.of(
                new Endpoint("moto",               "http://localhost:5000/moto-api/",       200, 299),
                new Endpoint("vault",              "http://localhost:8200/v1/sys/health",   200, 599),
                new Endpoint("appconfigdata-stub", "http://localhost:5001/configuration",   200, 499));

        for (Endpoint e : endpoints) {
            await().alias("waiting for " + e.name() + " at " + e.url())
                   .atMost(Duration.ofMinutes(3))
                   .pollInterval(Duration.ofSeconds(1))
                   .until(() -> {
                       try {
                           HttpURLConnection c = openGet(e.url());
                           int code = c.getResponseCode();
                           c.disconnect();
                           return code >= e.minCode() && code <= e.maxCode();
                       } catch (IOException ex) {
                           return false;
                       }
                   });
        }
    }

    private static void waitForEnvFile() {
        try {
            await().alias("waiting for " + ENV_FILE)
                   .atMost(Duration.ofMinutes(3))
                   .pollInterval(Duration.ofSeconds(1))
                   .until(() -> {
                       if (!Files.exists(ENV_FILE) || Files.size(ENV_FILE) == 0) {
                           return false;
                       }
                       String content = new String(Files.readAllBytes(ENV_FILE), StandardCharsets.UTF_8);
                       return content.contains("VAULT_ROLE_ID=") && content.contains("VAULT_SECRET_ID=");
                   });
        } catch (RuntimeException e) {
            System.err.println("---- vault-init logs (dumped on env-file timeout) ----");
            dumpComposeLogsQuietly("vault-init");
            System.err.println("---- vault logs ----");
            dumpComposeLogsQuietly("vault");
            System.err.println("---- docker compose ps ----");
            runComposeQuietly("ps", "-a");
            throw e;
        }
    }

    private static void dumpComposeLogsQuietly(String service) {
        runComposeQuietly("logs", "--no-color", service);
    }

    private static void runComposeQuietly(String... args) {
        try {
            dockerCompose(args);
        } catch (Exception ignored) {
        }
    }

    private static Map<String, String> parseEnvFile(Path envFile) throws IOException {
        Pattern kv = Pattern.compile("^(?:export\\s+)?([A-Z_][A-Z0-9_]*)=(.*)$");
        List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            Matcher m = kv.matcher(trimmed);
            if (m.matches()) {
                out.put(m.group(1), stripQuotes(m.group(2)));
            }
        }
        return out;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Path repoRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Could not locate repo root from " + System.getProperty("user.dir"));
        }
        return p;
    }
}
