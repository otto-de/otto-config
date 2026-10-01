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
        String requestUrl = this.url + "/v1/" + path + (version != null ? "?version=" + version : "");
        try {
            log.debug("Reading secret from Vault: url='{}', path='{}', version={}", requestUrl, path, version);
            VaultResponse response = this.get(requestUrl, Map.of(VaultHeaders.VAULT_TOKEN,  this.vaultAuthenticator.getToken()));
            log.debug("Successfully read secret from Vault: url='{}', path='{}', version={}", requestUrl, path, version);
            return response;
        } catch (RestException e) {
            log.error("Failed to read secret from Vault: url='{}', path='{}', version={}: {}", requestUrl, path, version, e.getMessage());
            throw new VaultException("Failed to retrieve secrets: " + e.getMessage(), e);
        }
    }

    public VaultResponse readMetadata(String path) throws VaultException {
        String metadataPath = path.replace("/data/", "/metadata/");
        String requestUrl = this.url + "/v1/" + metadataPath;
        try {
            log.debug("Reading secret metadata from Vault: url='{}', path='{}'", requestUrl, metadataPath);
            VaultResponse response = this.get(requestUrl, Map.of(VaultHeaders.VAULT_TOKEN, this.vaultAuthenticator.getToken()));
            log.debug("Successfully read secret metadata from Vault: url='{}', path='{}'", requestUrl, metadataPath);
            return response;
        } catch (RestException e) {
            log.error("Failed to read secret metadata from Vault: url='{}', path='{}': {}", requestUrl, metadataPath, e.getMessage());
            throw new VaultException("Failed to retrieve metadata: " + e.getMessage(), e);
        }
    }
}
