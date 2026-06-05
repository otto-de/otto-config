package de.otto.config.core;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        return this.properties.getOrDefault(key, null);
    }

    @Override
    public T getValue(String key, T defaultValue) {
        return this.properties.getOrDefault(key, defaultValue);
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
