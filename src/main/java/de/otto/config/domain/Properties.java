package de.otto.config.domain;

import lombok.Data;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;

import de.otto.config.core.Configuration;

import static java.util.Collections.emptyMap;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Properties implements Configuration<String> {
    public static final Properties empty = new Properties(emptyMap());
    public static final TypeReference<Properties> typeReference = new TypeReference<Properties>() {};

    private Map<String, String> properties;

    // package visibility to support jackson deserialization
    Properties() {
        this.properties = emptyMap();
    }

    public Properties(Map<String, String> properties) {
        this.properties = Collections.unmodifiableMap(properties);
    }

    // package visibility to support jackson deserialization
    void setProperties(Map<String, String> properties) {
        this.properties = Collections.unmodifiableMap(properties);
    }
}
