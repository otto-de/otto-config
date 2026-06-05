package de.otto.config.client.hashicorp.auth;

import java.util.Map;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.client.hashicorp.VaultException;
import de.otto.config.client.hashicorp.VaultResponse;
import de.otto.config.core.client.RestException;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class VaultAppRoleAuthenticator extends VaultAuthenticator {
    private static final String LOGIN_PATH = "/v1/auth/approle/login";
    
    private final @NonNull String roleId;
    private final @NonNull String secretId;
    private final String body;

    @Builder
    public VaultAppRoleAuthenticator(String url, String roleId, String secretId, ObjectMapper objectMapper) {
        super(url, VaultResponse.class, objectMapper);
        this.roleId = roleId;
        this.secretId = secretId;
        this.body = String.format("{\"role_id\":\"%s\", \"secret_id\":\"%s\"}", this.roleId, this.secretId);
    }

    @Override
    public void generateToken() throws VaultException {
        try {
            VaultResponse response = this.post(this.url + LOGIN_PATH,
                                               this.body, 
                                               Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
            updateToken(response);
        } catch (RestException e) {
            throw new VaultException("Failed to generate token: " + e.getMessage(), e);
        }
    }  
}
