package de.otto.config.integration.helidon.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.ConfigurationCache;
import de.otto.config.core.Context;
import de.otto.config.core.registry.ClientRegistry;
import de.otto.config.integration.helidon.spi.PropertySource;
import de.otto.config.provider.ConfigurationProvider;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.ssm.SsmClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

class BeanConfigurationTest {

    private String appName;
    PropertySource configSource;
    MockedStatic<ConfigProvider> configProviderMock;
    AppConfigDataClient appConfigDataClientMock;
    SecretsManagerClient secretsManagerClientMock;
    SsmClient ssmClientMock;
    Config configMock;

    @BeforeEach
    void setUp() {
        Mockito.clearAllCaches();

        appName = "test-app";

        appConfigDataClientMock = mock(AppConfigDataClient.class);
        StartConfigurationSessionResponse response = mock(StartConfigurationSessionResponse.class);
        when(appConfigDataClientMock.startConfigurationSession(any(StartConfigurationSessionRequest.class))).thenReturn(response);
        when(response.initialConfigurationToken()).thenReturn("token");
        ssmClientMock = mock(SsmClient.class);

        secretsManagerClientMock = mock(SecretsManagerClient.class);
        when(secretsManagerClientMock.getSecretValue(ArgumentMatchers.<GetSecretValueRequest>any())).thenReturn(null);

        configMock = mock(Config.class);
        mockConfigValue("app.name", appName);
        mockConfigValue("otto.config.aws.secrets.arn", "arn:aws:secretsmanager:region:123456789012:secret:my-secret");

        configSource = mock(PropertySource.class);
        ConfigurationCache<String> bootstrapConfiguration = new ConfigurationCache<>(Map.of(
                "app.name", appName,
                "profile", "local",
                "otto.config.aws.secrets.arn", "arn:aws:secretsmanager:region:123456789012:secret:my-secret"));
        ClientRegistry clientRegistry = ClientRegistry.builder().clients(Map.of(ObjectMapper.class, new ObjectMapper(),
                                                                                AppConfigDataClient.class, appConfigDataClientMock,
                                                                                SecretsManagerClient.class, secretsManagerClientMock,
                                                                                SsmClient.class, ssmClientMock)).build();
        Context context = Context.builder()
                .clientRegistry(clientRegistry)
                .configuration(bootstrapConfiguration)
                .build();

        when(configSource.getConfigurationProvider().getContext()).thenReturn(context);

        Iterable<ConfigSource> sources = Collections.singletonList(configSource);
        when(configMock.getConfigSources()).thenReturn(sources);

        configProviderMock = Mockito.mockStatic(ConfigProvider.class);
        configProviderMock.when(ConfigProvider::getConfig).thenReturn(configMock);
    }

    private void mockConfigValue(String key, String value) {
        ConfigValue configValue = mock(ConfigValue.class);
        when(configValue.getValue()).thenReturn(value);
        when(configMock.getConfigValue(key)).thenReturn(configValue);
        when(configMock.getValue(key, String.class)).thenReturn(value);
        if (value == null) {
            when(configMock.getOptionalValue(key, String.class)).thenReturn(Optional.empty());
        } else {
            when(configMock.getOptionalValue(key, String.class)).thenReturn(Optional.of(value));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"develop", "live"})
    @Disabled
    void shouldUseDefaultServicesWhenPofileIsEnvironmentSpecific(String profile) {
        // given
        mockConfigValue("mp.config.profile", profile);

        // when
        BeanConfiguration zealotConfiguration = new BeanConfiguration();

        // then
        assertThat(zealotConfiguration.configurationProvider(), is(notNullValue()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"local", "test"})
    @Disabled
    void shouldUseLocalServicesWhenProfileIsForLocalDevelopment(String profile) {
        // given
        mockConfigValue("mp.config.profile", profile);

        // when
        BeanConfiguration zealotConfiguration = new BeanConfiguration();
        ConfigurationProvider configurationProvider = zealotConfiguration.configurationProvider();

        // then
        assertThat(configurationProvider.getValue("myKey1"), is("myValue"));
        assertThat(configurationProvider.getValue("ftsn-415-test-toggle"), is(true));
    }
}