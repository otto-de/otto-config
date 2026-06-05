package de.otto.config.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;

import de.otto.config.core.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
@Data
public class Toggles implements Configuration<Boolean> {
    public static final Toggles empty = new Toggles(emptyMap());
    public static final TypeReference<Toggles> typeReference = new TypeReference<Toggles>() {};

    private final Map<String, Boolean> properties = new HashMap<>();
 
    @JsonCreator
    public Toggles(Map<String, Map<String, Object>> values) {
        values.forEach((key, value) -> this.properties.put(key, (Boolean) value.get("enabled")));
    }
}
