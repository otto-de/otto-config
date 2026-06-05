package de.otto.config.client.hashicorp;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.client.hashicorp.auth.VaultAuthenticator;
import de.otto.config.core.client.RestClient;
import de.otto.config.core.client.RestException;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VaultClient extends RestClient<VaultResponse> {
    private final @NonNull String url;
    private final @NonNull VaultAuthenticator vaultAuthenticator;
    
    @Builder
    public VaultClient(String url, VaultAuthenticator vaultAuthenticator, ObjectMapper objectMapper) {
        super(VaultResponse.class, objectMapper);
        this.url = url;
        this.vaultAuthenticator = vaultAuthenticator;
    }

    public VaultResponse read(String path) throws VaultException {
        return read(path, null);
    }

    public VaultResponse read(String path, Integer version) throws VaultException {
        try {
            return this.get(this.url + "/v1/" + path + (version != null ? "?version=" + version : ""), 
                            Map.of(VaultHeaders.VAULT_TOKEN,  this.vaultAuthenticator.getToken()));
        } catch (RestException e) {
            throw new VaultException("Failed to retrieve secrets: " + e.getMessage(), e);
        }
    }

    public VaultResponse readMetadata(String path) throws VaultException {
        try {
            return this.get(this.url + "/v1/" + path.replace("/data/", "/metadata/"), 
                            Map.of(VaultHeaders.VAULT_TOKEN, this.vaultAuthenticator.getToken()));
        } catch (RestException e) {
            throw new VaultException("Failed to retrieve metadata: " + e.getMessage(), e);
        }
    }
}
