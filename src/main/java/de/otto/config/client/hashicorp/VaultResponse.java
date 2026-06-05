package de.otto.config.client.hashicorp;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VaultResponse(Data data, Auth auth) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(Map<String, String> data, Map<String, Object> metadata, Map<String, Version> versions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Version(String created_time, String deletion_time, boolean destroyed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Auth(String client_token, long lease_duration) {
    }
}
