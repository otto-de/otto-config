package de.otto.config.demo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import de.otto.config.core.Configuration;
import de.otto.config.core.ConfigurationCache;
import de.otto.config.core.Context;
import de.otto.config.provider.ConfigurationProvider;
import de.otto.config.source.CoreSourceFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Plain Java demonstration of manual Otto Config integration.
 *
 * This example shows how to integrate Otto Config without any framework (Spring, Helidon, etc.):
 * 1. Create a Configuration<String> instance with your config values
 * 2. Create a Context using Context.from(appName, profile, configuration)
 * 3. Build ConfigurationProvider from the Context
 * 4. Expose configuration values from all sources via a {@link HttpServer} at {@code /config}
 *
 * The E2E test asserts on {@code GET /config}; the server stays up until the
 * JVM is signalled. Port is taken from the {@code SERVER_PORT} env var (defaults to 8080).
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws IOException {
        log.info("Starting plain Java Otto Config demo...");

        String profile = System.getenv().getOrDefault("OTTO_CONFIG_PROFILE", "default");

        // A framework would read these from application.properties; here we seed them explicitly.
        // Enable ALL sources so the E2E asserts against the same set the Spring / Helidon demos cover.
        Map<String, String> seed = new LinkedHashMap<>();
        seed.put("otto.config.sources.enabled",
                 "aws.appconfig.properties,aws.appconfig.toggles,aws.s3.toggles,aws.secrets,aws.ssm,hashicorp.vault");
        seed.put("otto.config.aws.secrets.arn", "otto-config");
        seed.put("otto.config.aws.s3.toggles.bucket.name", "otto-config-feature-toggles");
        seed.put("otto.config.aws.s3.toggles.folder.name", "feature-toggles/");
        seed.put("otto.config.aws.ssm.path.prefix", "/search/develop/otto-config");
        seed.put("otto.config.hashicorp.vault.url", "http://localhost:8200");
        seed.put("otto.config.hashicorp.vault.path", "cftsearch/data/service/otto-config/develop/auth");
        seed.put("otto.config.hashicorp.vault.prev.versions", "3");
        seed.put("otto.config.hashicorp.vault.auth.type", "approle");
        seed.put("otto.config.hashicorp.vault.auth.approle.role.id",
                 System.getenv().getOrDefault("VAULT_ROLE_ID", ""));
        seed.put("otto.config.hashicorp.vault.auth.approle.secret.id",
                 System.getenv().getOrDefault("VAULT_SECRET_ID", ""));
        seed.put("otto.config.aws.change.notifications.enabled", "false");

        Configuration<String> configuration = ConfigurationCache.<String>builder().properties(seed).build();

        Context context = Context.from("otto-config", profile, configuration);
        ConfigurationProvider configurationProvider = ConfigurationProvider.builder()
                                                                           .context(context)
                                                                           .source(CoreSourceFactory.createPropertiesSource(context))
                                                                           .source(CoreSourceFactory.createTogglesSource(context))
                                                                           .source(CoreSourceFactory.createS3TogglesSource(context))
                                                                           .source(CoreSourceFactory.createSecretsManagerSource(context))
                                                                           .source(CoreSourceFactory.createSsmSource(context))
                                                                           .build();

        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/config", exchange -> {
            String body = renderJson(configurationProvider);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.setExecutor(null);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        server.start();
        log.info("Plain Java demo listening on http://0.0.0.0:{}/config", port);
    }

    private static String renderJson(ConfigurationProvider provider) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("myKey1", provider.getValue("myKey1"));
        out.put("myKey2", provider.getValue("myKey2"));
        out.put("logging.enabled", provider.getValue("logging.enabled"));
        out.put("logging_enabled", provider.getValueAsBoolean("logging_enabled"));
        out.put("s3_toggle1", provider.getValueAsBoolean("s3_toggle1"));
        out.put("s3_toggle2", provider.getValueAsBoolean("s3_toggle2"));
        out.put("some_secret", provider.getValue("some_secret"));
        out.put("some_ssm_value", provider.getValue("some_ssm_value"));

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : out.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Boolean || v instanceof Number) {
                sb.append(v);
            } else {
                sb.append('"').append(escape(v.toString())).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

