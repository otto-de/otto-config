package de.otto.config.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import de.otto.config.provider.ConfigurationProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Exposes a stable, machine-readable snapshot of the configuration values
 * the E2E test asserts on. Mirrors the Spring demo's {@code ConfigController}.
 */
@Path("/config")
@ApplicationScoped
public class ConfigEndpoint {

    private final ConfigurationProvider configurationProvider;

    @Inject
    public ConfigEndpoint(ConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> config() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("myKey1", configurationProvider.getValue("myKey1"));
        out.put("myKey2", configurationProvider.getValue("myKey2"));
        out.put("logging.enabled", configurationProvider.getValue("logging.enabled"));
        out.put("logging_enabled", configurationProvider.getValueAsBoolean("logging_enabled"));
        out.put("s3_toggle1", configurationProvider.getValueAsBoolean("s3_toggle1"));
        out.put("s3_toggle2", configurationProvider.getValueAsBoolean("s3_toggle2"));
        out.put("some_secret", configurationProvider.getValue("some_secret"));
        out.put("some_ssm_value", configurationProvider.getValue("some_ssm_value"));
        return out;
    }
}
