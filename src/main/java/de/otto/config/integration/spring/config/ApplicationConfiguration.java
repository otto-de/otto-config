package de.otto.config.integration.spring.config;

import org.springframework.core.env.Environment;

import de.otto.config.core.Configuration;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Builder
@RequiredArgsConstructor
public class ApplicationConfiguration implements Configuration<String> {
    private final @NonNull Environment environment;
    
    @Override
    public String getValue(String key) {
        return environment.getProperty(key);
    }

    @Override
    public String getValue(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }
}
