package de.otto.config.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import de.otto.config.provider.ConfigurationProvider;
import lombok.RequiredArgsConstructor;

/**
 * Exposes a stable, machine-readable snapshot of the configuration values
 * the E2E test asserts on. Kept minimal on purpose: no framework-specific
 * behaviour, just what {@link ConfigurationProvider} resolves.
 */
@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigurationProvider configurationProvider;

    @GetMapping("/config")
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
