package de.otto.config.client.hashicorp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.client.hashicorp.VaultException;
import de.otto.config.client.hashicorp.VaultResponse;
import de.otto.config.core.client.RestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class VaultAuthenticatorTest {

    private VaultAuthenticator authenticator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        authenticator = spy(new VaultAuthenticator("http://vault", VaultResponse.class, objectMapper) {
            @Override
            public void generateToken() throws VaultException {
                this.token = "generated-token";
                this.leaseDuration = 100;
                this.tokenExpiry = Instant.now().plusSeconds(leaseDuration);
            }

            @Override
            public VaultResponse post(String url, Map<String, String> headers) throws RestException {
                VaultResponse.Auth auth = mock(VaultResponse.Auth.class);
                when(auth.client_token()).thenReturn("renewed-token");
                when(auth.lease_duration()).thenReturn(200L);
                VaultResponse response = mock(VaultResponse.class);
                when(response.auth()).thenReturn(auth);
                return response;
            }
        });
    }

    @Test
    void shouldGenerateTokenIfTokenIsNull() throws Exception {
        // given
        authenticator.token = null;
        authenticator.tokenExpiry = null;

        // when
        String token = authenticator.getToken();

        // then
        assertThat(token, is("generated-token"));
        verify(authenticator, times(1)).generateToken();
    }

    @Test
    void shouldGenerateTokenIfTokenIsExpired() throws Exception {
        // given
        authenticator.token = "old-token";
        authenticator.tokenExpiry = Instant.now().minusSeconds(10);

        // when
        String token = authenticator.getToken();

        // then
        assertThat(token, is("generated-token"));
        verify(authenticator, times(1)).generateToken();
    }

    @Test
    void shouldRenewTokenIfExpiringSoon() throws Exception {
        // given
        authenticator.token = "old-token";
        authenticator.leaseDuration = 100;
        authenticator.tokenExpiry = Instant.now().plusSeconds(10);

        doNothing().when(authenticator).generateToken();

        // when
        String token = authenticator.getToken();

        // then
        assertThat(token, is("renewed-token"));
        verify(authenticator, times(1)).renewToken();
    }

    @Test
    void shouldRenewTokenSuccessfully() throws Exception {
        // given
        authenticator.token = "old-token";
        authenticator.leaseDuration = 100;
        authenticator.tokenExpiry = Instant.now().plusSeconds(10);

        // when
        authenticator.renewToken();

        // then
        assertThat(authenticator.token, is("renewed-token"));
        assertThat(authenticator.leaseDuration, is(200L));
        assertThat(authenticator.tokenExpiry, is(notNullValue()));
    }

    @Test
    void shouldGenerateTokenIfRenewFails() throws Exception {
        // given
        authenticator.token = "old-token";
        authenticator.leaseDuration = 100;
        authenticator.tokenExpiry = Instant.now().plusSeconds(10);

        doThrow(new RestException("fail")).when(authenticator).post(anyString(), anyMap());
        doNothing().when(authenticator).generateToken();

        // when
        authenticator.renewToken();

        // then
        verify(authenticator, times(1)).generateToken();
    }

    @Test
    void shouldNotRenewOrGenerateIfTokenIsValid() throws Exception {
        // given
        authenticator.token = "valid-token";
        authenticator.leaseDuration = 1000;
        authenticator.tokenExpiry = Instant.now().plusSeconds(900);

        // when
        String token = authenticator.getToken();

        // then
        assertThat(token, is("valid-token"));
        verify(authenticator, never()).generateToken();
        verify(authenticator, never()).renewToken();
    }
}