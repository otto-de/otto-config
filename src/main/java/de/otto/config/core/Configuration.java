package de.otto.config.core;

import static java.util.Collections.emptyMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Multimap;

public interface Configuration<T> {

    public default boolean isEmpty() {
        return getProperties() == null || getProperties().isEmpty();
    }

    public default T getValue(String key) {
        return getProperties().get(key);
    }

    public default T getValue(String key, T defaultValue) {
        return getProperties().getOrDefault(key, defaultValue);
    }

    @SuppressWarnings("null")
    default <S> List<S> getValues(String key, Class<S> type) {
        String value = getValueAsString(key);
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }

        if (!value.contains(",")) {
            return List.of(convertValue(value, type));
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty()) // Filter empty strings from "a,,b"
                .map(s -> convertValue(s, type))
                .collect(Collectors.toList());
    }

    default <S> S getValueByType(String key, Class<S> type) {
        String value = getValueAsString(key);
        return convertValue(value, type);
    }

    @SuppressWarnings("unchecked")
    private <S> S convertValue(String value, Class<S> type) {
        if (value == null) {
            return null;
        }
        
        if (type == String.class) {
            return (S) value;
        }
        if (type == Integer.class || type == int.class) {
            return (S) Integer.valueOf(value);
        }
        if (type == Long.class || type == long.class) {
            return (S) Long.valueOf(value);
        }
        if (type == Double.class || type == double.class) {
            return (S) Double.valueOf(value);
        }
        if (type == Float.class || type == float.class) {
            return (S) Float.valueOf(value);
        }
        if (type == Boolean.class || type == boolean.class) {
            return (S) Boolean.valueOf(value);
        }
        
        throw new IllegalArgumentException("Unsupported type: " + type.getName() + " for value: " + value);
    }

    public default String getValueAsString(String key) {
        T value = getValue(key);
        return value != null ? value.toString() : null;
    }

    public default String getValueAsString(String key, String defaultValue) {
        String value = getValueAsString(key);
        return value != null ? value : defaultValue;
    }

    public default int getValueAsInt(String key) {
        return Integer.parseInt(getValueAsString(key, "0"));
    }

    public default int getValueAsInt(String key, int defaultValue) {
        return Integer.parseInt(getValueAsString(key, String.valueOf(defaultValue)));
    }

    public default boolean getValueAsBoolean(String key) {
        return getValueAsBoolean(key, false);
    }

    public default boolean getValueAsBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getValueAsString(key, String.valueOf(defaultValue)));
    }

    public default boolean containsKey(String key) {
        return getValue(key) != null;
    }

    public default Map<String, T> getProperties() {
        return emptyMap();
    }

    public default Configuration<T> withOverrides(Multimap<String, String> overrides) {
        return new ConfigurationOverride<>(this, overrides);
    }

    public static class ConfigurationOverride<T> implements Configuration<T> {
        private final Configuration<T> configuration;
        private final Multimap<String, String> overrides;

        public ConfigurationOverride(Configuration<T> configuration, Multimap<String, String> overrides) {
            this.configuration = configuration;
            this.overrides = overrides;
        }

        @Override
        @SuppressWarnings({ "unchecked", "null" })
        public T getValue(String key) {
            return overrides.containsKey(key) ? (T) overrides.get(key).iterator().next()
                                              : configuration.getValue(key);
        }
    }
}
