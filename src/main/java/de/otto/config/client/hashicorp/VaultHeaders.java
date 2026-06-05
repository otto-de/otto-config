package de.otto.config.client.hashicorp;

import lombok.experimental.UtilityClass;

@UtilityClass
public class VaultHeaders {
    public static final String VAULT_TOKEN = "X-Vault-Token";
    public static final String VAULT_AWS_IAM_SERVER_ID = "X-Vault-AWS-IAM-Server-ID";
}
