package de.otto.config.client.hashicorp;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.client.hashicorp.auth.VaultAuthenticator;
import de.otto.config.core.client.RestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultClientTest {

    private static final String VAULT_URL = "http://vault";
    private VaultAuthenticator vaultAuthenticator;
    private ObjectMapper objectMapper;
    private VaultClient vaultClient;

    @BeforeEach
    void setUp() {
        vaultAuthenticator = mock(VaultAuthenticator.class);
        objectMapper = mock(ObjectMapper.class);
        vaultClient = spy(VaultClient.builder()
                .url(VAULT_URL)
                .vaultAuthenticator(vaultAuthenticator)
                .objectMapper(objectMapper)
                .build());
    }

    @Test
    void shouldReadWithoutVersion() throws Exception {
        // given
        String path = "secret/data/mysecret";
        VaultResponse expectedResponse = mock(VaultResponse.class);
        when(vaultAuthenticator.getToken()).thenReturn("token123");
        doReturn(expectedResponse).when(vaultClient).get(
                eq(VAULT_URL + "/v1/" + path),
                eq(Map.of("X-Vault-Token", "token123"))
        );

        // when
        VaultResponse response = vaultClient.read(path);

        // then
        assertThat(response, is(expectedResponse));
        verify(vaultClient).get(eq(VAULT_URL + "/v1/" + path), eq(Map.of("X-Vault-Token", "token123")));
    }

    @Test
    void shouldReadWithVersion() throws Exception {
        // given
        String path = "secret/data/mysecret";
        int version = 2;
        VaultResponse expectedResponse = mock(VaultResponse.class);
        when(vaultAuthenticator.getToken()).thenReturn("token456");
        doReturn(expectedResponse).when(vaultClient).get(
                eq(VAULT_URL + "/v1/" + path + "?version=" + version),
                eq(Map.of("X-Vault-Token", "token456"))
        );

        // when
        VaultResponse response = vaultClient.read(path, version);

        // then
        assertThat(response, is(expectedResponse));
        verify(vaultClient).get(eq(VAULT_URL + "/v1/" + path + "?version=" + version), eq(Map.of("X-Vault-Token", "token456")));
    }

    @Test
    void shouldThrowVaultExceptionOnReadFailure() throws Exception {
        // given
        String path = "secret/data/mysecret";
        when(vaultAuthenticator.getToken()).thenReturn("token789");
        doThrow(new RestException("rest error")).when(vaultClient).get(anyString(), anyMap());

        // when/then
        VaultException ex = assertThrows(VaultException.class, () -> vaultClient.read(path, null));
        assertThat(ex.getMessage(), containsString("Failed to retrieve secrets"));
        assertThat(ex.getCause(), instanceOf(RestException.class));
    }

    @Test
    void shouldReadMetadata() throws Exception {
        // given
        String path = "secret/data/mysecret";
        String expectedPath = VAULT_URL + "/v1/secret/metadata/mysecret";
        VaultResponse expectedResponse = mock(VaultResponse.class);
        when(vaultAuthenticator.getToken()).thenReturn("tokenMeta");
        doReturn(expectedResponse).when(vaultClient).get(
                eq(expectedPath),
                eq(Map.of("X-Vault-Token", "tokenMeta"))
        );

        // when
        VaultResponse response = vaultClient.readMetadata(path);

        // then
        assertThat(response, is(expectedResponse));
        verify(vaultClient).get(eq(expectedPath), eq(Map.of("X-Vault-Token", "tokenMeta")));
    }

    @Test
    void shouldThrowVaultExceptionOnReadMetadataFailure() throws Exception {
        // given
        String path = "secret/data/mysecret";
        when(vaultAuthenticator.getToken()).thenReturn("tokenMetaFail");
        doThrow(new RestException("metadata error")).when(vaultClient).get(anyString(), anyMap());

        // when/then
        VaultException ex = assertThrows(VaultException.class, () -> vaultClient.readMetadata(path));
        assertThat(ex.getMessage(), containsString("Failed to retrieve metadata"));
        assertThat(ex.getCause(), instanceOf(RestException.class));
    }
}