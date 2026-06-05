package de.otto.config.client.hashicorp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.Context;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VaultAuthenticatorFactory {
    
    public static VaultAuthenticator create(Context context) {
        String type = context.getConfiguration().getValue("otto.config.hashicorp.vault.auth.type", "approle");

        if (VaultAuthenticationType.of(type) == VaultAuthenticationType.AWS) {
            return VaultAwsAuthenticator.builder()
                                        .url(context.getConfiguration().getValue("otto.config.hashicorp.vault.url"))
                                        .region(context.getConfiguration().getValue("otto.config.hashicorp.vault.auth.aws.region"))
                                        .role(context.getConfiguration().getValue("otto.config.hashicorp.vault.auth.aws.role.name"))
                                        .roleArn(context.getConfiguration().getValue("otto.config.hashicorp.vault.auth.aws.role.arn"))
                                        .headerValue(context.getConfiguration().getValue("otto.config.hashicorp.vault.auth.aws.header.value"))
                                        .objectMapper(context.getClientRegistry().get(ObjectMapper.class))
                                        .build();
        }

        return VaultAppRoleAuthenticator.builder()
                                        .url(context.getConfiguration().getValue("otto.config.hashicorp.vault.url"))
                                        .roleId(context.getConfiguration().getValue("otto.config.hashicorp.vault.auth.approle.role.id"))
                                        .secretId(context.getConfiguration().getValue("otto.config.hashicorp.vault.auth.approle.secret.id"))
                                        .objectMapper(context.getClientRegistry().get(ObjectMapper.class))
                                        .build();
    }
}
