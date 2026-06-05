package de.otto.config.integration.helidon.config;

import de.otto.config.core.Configuration;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.eclipse.microprofile.config.Config;

@Builder
@RequiredArgsConstructor
public class ApplicationConfiguration implements Configuration<String> {
    private final @NonNull Config config;
    
    @Override
    public String getValue(String key) {
        return getValue(key, null);
    }

    @Override
    public String getValue(String key, String defaultValue) {
        return config.getOptionalValue(key, String.class).orElse(defaultValue);
    }
}
