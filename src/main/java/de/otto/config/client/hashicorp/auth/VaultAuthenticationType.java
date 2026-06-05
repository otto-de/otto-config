package de.otto.config.client.hashicorp.auth;

public enum VaultAuthenticationType {
    APPROLE,
    AWS;

    public static VaultAuthenticationType of(String value) {
        if (value == null) return APPROLE;
        try {
            return VaultAuthenticationType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return APPROLE;
        }
    }
}
