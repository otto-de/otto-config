package de.otto.config.client.hashicorp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.config.core.Context;
import de.otto.config.core.registry.ClientRegistry;
import de.otto.config.core.ConfigurationCache;
import software.amazon.awssdk.regions.Region;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class VaultAuthenticatorFactoryTest {

    private Context context;
    private ConfigurationCache<String> config;
    private ClientRegistry clientRegistry;
    private ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        context = mock(Context.class);
        config = mock(ConfigurationCache.class);
        clientRegistry = mock(ClientRegistry.class);
        objectMapper = new ObjectMapper();

        when(context.getConfiguration()).thenReturn(config);
        when(context.getClientRegistry()).thenReturn(clientRegistry);
        when(clientRegistry.get(ObjectMapper.class)).thenReturn(objectMapper);
    }

    @Test
    void shouldCreateAwsAuthenticatorWhenTypeIsAws() {
        // given
        when(config.getValue("otto.config.hashicorp.vault.auth.type", "approle")).thenReturn("aws");
        when(config.getValue("otto.config.hashicorp.vault.url")).thenReturn("https://vault.aws");
        when(config.getValue("otto.config.hashicorp.vault.auth.aws.region")).thenReturn("eu-west-1");
        when(config.getValue("otto.config.hashicorp.vault.auth.aws.role.name")).thenReturn("aws-role");
        when(config.getValue("otto.config.hashicorp.vault.auth.aws.role.arn")).thenReturn("arn:aws:iam::123456789012:role/aws-role");
        when(config.getValue("otto.config.hashicorp.vault.auth.aws.header.value")).thenReturn("header-value");

        // when
        VaultAuthenticator authenticator = VaultAuthenticatorFactory.create(context);

        // then
        assertThat(authenticator, instanceOf(VaultAwsAuthenticator.class));
        VaultAwsAuthenticator awsAuth = (VaultAwsAuthenticator) authenticator;
        assertThat(awsAuth.getUrl(), is("https://vault.aws"));
        assertThat(awsAuth.getRegion(), is(Region.EU_WEST_1));
        assertThat(awsAuth.getRole(), is("aws-role"));
        assertThat(awsAuth.getRoleArn(), is("arn:aws:iam::123456789012:role/aws-role"));
        assertThat(awsAuth.getHeaderValue(), is("header-value"));
        assertThat(awsAuth.getObjectMapper(), is(objectMapper));
    }

    @Test
    void shouldCreateAppRoleAuthenticatorWhenTypeIsAppRole() {
        // given
        when(config.getValue("otto.config.hashicorp.vault.auth.type", "approle")).thenReturn("approle");
        when(config.getValue("otto.config.hashicorp.vault.url")).thenReturn("https://vault.approle");
        when(config.getValue("otto.config.hashicorp.vault.auth.approle.role.id")).thenReturn("role-id");
        when(config.getValue("otto.config.hashicorp.vault.auth.approle.secret.id")).thenReturn("secret-id");

        // when
        VaultAuthenticator authenticator = VaultAuthenticatorFactory.create(context);

        // then
        assertThat(authenticator, instanceOf(VaultAppRoleAuthenticator.class));
        VaultAppRoleAuthenticator approleAuth = (VaultAppRoleAuthenticator) authenticator;
        assertThat(approleAuth.getUrl(), is("https://vault.approle"));
        assertThat(approleAuth.getRoleId(), is("role-id"));
        assertThat(approleAuth.getSecretId(), is("secret-id"));
    }

    @Test
    void shouldDefaultToAppRoleAuthenticatorWhenTypeIsMissing() {
        // given
        when(config.getValue("otto.config.hashicorp.vault.auth.type", "approle")).thenReturn("approle");
        when(config.getValue("otto.config.hashicorp.vault.url")).thenReturn("https://vault.default");
        when(config.getValue("otto.config.hashicorp.vault.auth.approle.role.id")).thenReturn("role-id-default");
        when(config.getValue("otto.config.hashicorp.vault.auth.approle.secret.id")).thenReturn("secret-id-default");

        // when
        VaultAuthenticator authenticator = VaultAuthenticatorFactory.create(context);

        // then
        assertThat(authenticator, instanceOf(VaultAppRoleAuthenticator.class));
        VaultAppRoleAuthenticator approleAuth = (VaultAppRoleAuthenticator) authenticator;
        assertThat(approleAuth.getUrl(), is("https://vault.default"));
        assertThat(approleAuth.getRoleId(), is("role-id-default"));
        assertThat(approleAuth.getSecretId(), is("secret-id-default"));
    }
}
