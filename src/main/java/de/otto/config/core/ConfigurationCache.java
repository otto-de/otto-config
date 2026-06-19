package de.otto.config.core;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.otto.config.core.property.PropertyNameNormalizer;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ConfigurationCache<T> implements Configuration<T> {
    private final ConcurrentHashMap<String, T> properties = new ConcurrentHashMap<>();

    @Builder
    public ConfigurationCache(Map<String, T> properties) {
        this.properties.putAll(properties);
    }

    @Override
    public T getValue(String key) {
        // Try exact match first
        T value = this.properties.get(key);
        if (value != null) {
            return value;
        }
        
        // Try variants for relaxed binding (supports all frameworks)
        for (String variant : PropertyNameNormalizer.generateVariants(key)) {
            value = this.properties.get(variant);
            if (value != null) {
                return value;
            }
        }
        
        return null;
    }

    @Override
    public T getValue(String key, T defaultValue) {
        T value = getValue(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public Map<String, T> getProperties() {
        return this.properties;
    }

    public void setProperties(Map<String, T> values) {
        this.properties.clear();
        this.properties.putAll(values);
    }

    public Set<String> getPropertyNames() {
        return getProperties().keySet();
    }
}
