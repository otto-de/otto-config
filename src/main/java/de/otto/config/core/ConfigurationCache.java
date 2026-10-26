package de.otto.config.core;

import java.util.Map;
import java.util.Set;

import de.otto.config.core.property.PropertyNameNormalizer;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ConfigurationCache<T> implements Configuration<T> {
    // Immutable snapshot, swapped atomically so readers never observe a partially refreshed state
    private volatile Map<String, T> properties = Map.of();

    @Builder
    public ConfigurationCache(Map<String, T> properties) {
        this.properties = Map.copyOf(properties);
    }

    @Override
    public T getValue(String key) {
        Map<String, T> snapshot = this.properties;
        // Try exact match first
        T value = snapshot.get(key);
        if (value != null) {
            return value;
        }
        
        // Try variants for relaxed binding (supports all frameworks)
        for (String variant : PropertyNameNormalizer.generateVariants(key)) {
            value = snapshot.get(variant);
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
        this.properties = Map.copyOf(values);
    }

    public Set<String> getPropertyNames() {
        return getProperties().keySet();
    }
}
