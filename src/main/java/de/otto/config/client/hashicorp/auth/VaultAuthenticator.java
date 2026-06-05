package de.otto.config.client.hashicorp.auth;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

import de.otto.config.client.hashicorp.VaultException;
import de.otto.config.client.hashicorp.VaultHeaders;
import de.otto.config.client.hashicorp.VaultResponse;
import de.otto.config.core.client.RestClient;
import de.otto.config.core.client.RestException;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class VaultAuthenticator extends RestClient<VaultResponse> {
    private final String RENEW_PATH = "/v1/auth/token/renew-self";

    @Getter
    final @NonNull String url;
    volatile String token;
    @Getter
    volatile long leaseDuration;
    @Getter
    volatile Instant tokenExpiry;

    public VaultAuthenticator(String url, Class<VaultResponse> type, ObjectMapper objectMapper) {
        super(type, objectMapper);
        this.url = url;
    }

    public String getToken() throws VaultException {
        refreshTokenIfNeeded();
        return this.token;
    }

    private synchronized void refreshTokenIfNeeded() throws VaultException {
        if (isTokenExpired()) {
            generateToken();
        } else if (isTokenExpiringSoon()) {
            renewToken();
        }
    }

    private boolean isTokenExpired() {
        return token == null || (tokenExpiry != null && Instant.now().isAfter(tokenExpiry));
    }
    
    private boolean isTokenExpiringSoon() {
        return tokenExpiry == null || Instant.now().isAfter(tokenExpiry.minusSeconds(leaseDuration / 2));
    }

    public abstract void generateToken() throws VaultException;

    public void renewToken() throws VaultException {
        try {
            VaultResponse response = this.post(this.url + RENEW_PATH,
                                               Map.of(VaultHeaders.VAULT_TOKEN, this.token, 
                                                      HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
            updateToken(response);
        } catch (RestException e) {
            log.error("Exception during token renewal, attempting to re-generate token", e);
            generateToken();
        }
    }

    protected void updateToken(VaultResponse response) {
        this.token = response.auth().client_token();
        this.leaseDuration = response.auth().lease_duration();
        this.tokenExpiry = Instant.now().plusSeconds(this.leaseDuration);
    }
}
