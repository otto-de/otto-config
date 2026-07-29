package de.otto.config.source.hashicorp;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.otto.config.client.hashicorp.VaultClient;
import de.otto.config.client.hashicorp.VaultException;
import de.otto.config.client.hashicorp.VaultResponse;
import de.otto.config.core.source.SourceException;
import de.otto.config.domain.Properties;
import de.otto.config.source.PropertySource;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class VaultSource extends PropertySource {
    private final @NonNull VaultClient vaultClient;
    private final @NonNull String secretPath;
    private final int previousVersions;

    @Override
    public boolean hasSecrets() {
        return true;
    }

    @Override
    public Properties load() throws SourceException {
        try {
            VaultResponse response = this.vaultClient.read(this.secretPath);

            if (response.data() != null && !response.data().data().isEmpty()) {
                Map<String, String> secrets = appendVersions(response.data().data(), this.secretPath);
                return new Properties(secrets);
            }
            return getEmptyValue();
        } catch (VaultException e) {
            throw new SourceException("Unable to get secrets from Vault", e);
        }
    }

    private Map<String, String> appendVersions(Map<String, String> secrets, String path) throws VaultException {
        Map<String, String> secretsWithVersions = new HashMap<>(secrets);

        List<Integer> versions = getVersions(path);
        for (Integer version : versions) {
            Map<String, String> previousSecrets = this.vaultClient.read(path, version).data().data();
            this.mergeAsListValues(previousSecrets, secretsWithVersions);
        }

        return secretsWithVersions;
    }

    private List<Integer> getVersions(String secretPath) throws VaultException {
        VaultResponse response = this.vaultClient.readMetadata(secretPath);

        if (response.data() == null && response.data().versions() == null) {
            log.warn("No versions found.");
            return Collections.emptyList();
        }

        return response.data().versions().entrySet()
                                         .stream()
                                         .filter(entry -> entry.getValue().deletion_time().isEmpty())
                                         .map(entry -> Integer.valueOf(entry.getKey()))
                                         .sorted(Comparator.reverseOrder())
                                         .skip(1) // Skip the first (most recent) version
                                         .limit(previousVersions)
                                         .toList();
    }
}
