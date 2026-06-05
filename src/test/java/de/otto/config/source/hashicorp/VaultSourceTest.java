package de.otto.config.source.hashicorp;

import de.otto.config.client.hashicorp.VaultClient;
import de.otto.config.client.hashicorp.VaultException;
import de.otto.config.client.hashicorp.VaultResponse;
import de.otto.config.domain.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class VaultSourceTest {

    private VaultClient vaultClient;
    private String secretPath;

    @BeforeEach
    void setUp() {
        vaultClient = mock(VaultClient.class);
        secretPath = "secret/data/myapp";
    }

    @Test
    void shouldLoadPropertiesFromVault() throws Exception {
        // given
        Map<String, String> currentData = new HashMap<>();
        currentData.put("foo", "bar");
        currentData.put("baz", "qux");
        VaultResponse secretsResponse = new VaultResponse(new VaultResponse.Data(currentData, null, null), null);
        when(vaultClient.read(secretPath)).thenReturn(secretsResponse);
        Map<String, VaultResponse.Version> versions = new HashMap<>();
        versions.put("1", new VaultResponse.Version(null, "", false));
        versions.put("2", new VaultResponse.Version(null, "", false));
        versions.put("3", new VaultResponse.Version(null, "", false));
        VaultResponse versionsResponse = new VaultResponse(new VaultResponse.Data(null, null, versions), null);
        when(vaultClient.readMetadata(secretPath)).thenReturn(versionsResponse);
        when(vaultClient.read(secretPath, Integer.valueOf(1))).thenReturn(new VaultResponse(new VaultResponse.Data(Map.of("foo", "barn", "baz", "quack"), null, null), null));
        when(vaultClient.read(secretPath, Integer.valueOf(2))).thenReturn(new VaultResponse(new VaultResponse.Data(Map.of("foo", "bark", "baz", "quarks"), null, null), null));
        VaultSource vaultSource = VaultSource.builder()
                                             .vaultClient(vaultClient)
                                             .secretPath(secretPath)
                                             .previousVersions(3)
                                             .build();

        // when
        Properties properties = vaultSource.getOrLoad();

        // then
        assertThat(properties, is(notNullValue()));
        assertThat(properties.getValue("foo"), is("bar,bark,barn"));
        assertThat(properties.getValue("baz"), is("qux,quarks,quack"));
    }

    @Test
    void shouldReturnEmptyPropertiesWhenVaultThrowsException() throws Exception {
        // given
        VaultSource vaultSource = VaultSource.builder()
                                             .vaultClient(vaultClient)
                                             .secretPath(secretPath)
                                             .previousVersions(3)
                                             .build();
        when(vaultClient.read(secretPath)).thenThrow(VaultException.class);

        // when
        Properties properties = vaultSource.getOrLoad();

        // then
        assertThat(properties, is(Properties.empty));
    }

    @Test
    void shouldReturnEmptyPropertiesWhenVaultReturnsEmptyData() throws Exception {
        // given.thenReturn(null);
        VaultResponse vaultResponse = new VaultResponse(new VaultResponse.Data(emptyMap(), null, null), null);
        when(vaultClient.read(secretPath)).thenReturn(vaultResponse);

        VaultSource vaultSource = VaultSource.builder()
                                             .vaultClient(vaultClient)
                                             .secretPath(secretPath)
                                             .previousVersions(3)
                                             .build();

        // when
        Properties properties = vaultSource.getOrLoad();

        // then
        assertThat(properties, is(notNullValue()));
        assertThat(properties.getProperties().isEmpty(), is(true));
    }
}
