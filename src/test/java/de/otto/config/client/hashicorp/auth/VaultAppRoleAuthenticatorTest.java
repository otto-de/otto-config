package de.otto.config.client.hashicorp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

import de.otto.config.client.hashicorp.VaultException;
import de.otto.config.client.hashicorp.VaultResponse;
import de.otto.config.core.client.RestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.Map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class VaultAppRoleAuthenticatorTest {

    private VaultAppRoleAuthenticator authenticator;
    private ObjectMapper objectMapper;
    private final String url = "http://vault";
    private final String roleId = "role123";
    private final String secretId = "secret456";

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);
        authenticator = spy(VaultAppRoleAuthenticator.builder()
                .url(url)
                .roleId(roleId)
                .secretId(secretId)
                .objectMapper(objectMapper)
                .build());
    }

    @Test
    void shouldGenerateTokenAndSetFields() throws Exception {
        // given
        VaultResponse.Auth auth = mock(VaultResponse.Auth.class);
        when(auth.client_token()).thenReturn("token-abc");
        when(auth.lease_duration()).thenReturn(3600L);

        VaultResponse response = mock(VaultResponse.class);
        when(response.auth()).thenReturn(auth);

        doReturn(response).when(authenticator).post(
                eq(url + "/v1/auth/approle/login"),
                anyString(),
                anyMap()
        );

        // when
        Instant before = Instant.now();
        authenticator.generateToken();
        Instant after = Instant.now();

        // then
        assertThat(authenticator.token, is("token-abc"));
        assertThat(authenticator.leaseDuration, is(3600L));
        assertThat(authenticator.tokenExpiry, allOf(greaterThanOrEqualTo(before.plusSeconds(3600)), lessThanOrEqualTo(after.plusSeconds(3600))));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(authenticator).post(eq(url + "/v1/auth/approle/login"), bodyCaptor.capture(), eq(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())));
        String body = bodyCaptor.getValue();
        assertThat(body, containsString(roleId));
        assertThat(body, containsString(secretId));
    }

    @Test
    void shouldThrowVaultExceptionOnRestException() throws Exception {
        // given
        doThrow(new RestException("fail")).when(authenticator).post(anyString(), anyString(), anyMap());

        // when/then
        VaultException ex = assertThrows(VaultException.class, () -> authenticator.generateToken());
        assertThat(ex.getMessage(), containsString("Failed to generate token"));
        assertThat(ex.getCause(), instanceOf(RestException.class));
    }
}