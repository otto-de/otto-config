package de.otto.config.client.hashicorp.auth;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class VaultAuthenticationTypeTest {

    @Test
    void shouldReturnApproleForNull() {
        assertThat(VaultAuthenticationType.of(null), is(VaultAuthenticationType.APPROLE));
    }

    @Test
    void shouldReturnApproleForUnknownValue() {
        assertThat(VaultAuthenticationType.of("unknown"), is(VaultAuthenticationType.APPROLE));
    }

    @Test
    void shouldReturnApproleForApproleCaseInsensitive() {
        assertThat(VaultAuthenticationType.of("approle"), is(VaultAuthenticationType.APPROLE));
        assertThat(VaultAuthenticationType.of("APPROLE"), is(VaultAuthenticationType.APPROLE));
        assertThat(VaultAuthenticationType.of("ApPrOlE"), is(VaultAuthenticationType.APPROLE));
    }

    @Test
    void shouldReturnAwsForAwsCaseInsensitive() {
        assertThat(VaultAuthenticationType.of("aws"), is(VaultAuthenticationType.AWS));
        assertThat(VaultAuthenticationType.of("AWS"), is(VaultAuthenticationType.AWS));
        assertThat(VaultAuthenticationType.of("aWs"), is(VaultAuthenticationType.AWS));
    }

    @Test
    void shouldTrimInput() {
        assertThat(VaultAuthenticationType.of("  aws  "), is(VaultAuthenticationType.AWS));
        assertThat(VaultAuthenticationType.of("  approle  "), is(VaultAuthenticationType.APPROLE));
    }
}
