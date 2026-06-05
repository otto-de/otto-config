package de.otto.config.integration.helidon.spi;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.eclipse.microprofile.config.spi.ConfigBuilder;
import java.util.*;

import static de.otto.config.fixture.MockAwsClients.withMockedAwsClients;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

class PropertySourceTest {

    private MockedStatic<ConfigProviderResolver> resolverMockedStatic;
    private ConfigProviderResolver resolverMock;
    private Config configMock;

    @BeforeEach
    void setUp() {
        Mockito.clearAllCaches();

        resolverMockedStatic = Mockito.mockStatic(ConfigProviderResolver.class);
        resolverMock = mock(ConfigProviderResolver.class);
        configMock = mock(Config.class);
        ConfigBuilder configBuilderMock = mock(ConfigBuilder.class);

        resolverMockedStatic.when(ConfigProviderResolver::instance).thenReturn(resolverMock);
        when(resolverMock.getBuilder()).thenReturn(configBuilderMock);
        when(configBuilderMock.addDefaultSources()).thenReturn(configBuilderMock);
        when(configBuilderMock.build()).thenReturn(configMock);
    }

    @AfterEach
    void tearDown() {
        resolverMockedStatic.close();
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

    @Test
    void shouldUseLocalPropertySourceWhenProfileIsLocal() {
        // given
        mockConfigValue("app.name", "myApp");
        mockConfigValue("mp.config.profile", "local");
        mockConfigValue("otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm");

        // when
        PropertySource source = new PropertySource();

        // then
        assertThat(source.getConfigurationProvider().getValue("myKey1"), is("myValue"));
    }

    @Test
    void shouldUseLocalPropertySourceWhenProfileIsTest() {
        // given
        mockConfigValue("app.name", "myApp");
        mockConfigValue("mp.config.profile", "test");
        mockConfigValue("otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm");

        // when
        PropertySource source = new PropertySource();

        // then
        assertThat(source.getConfigurationProvider().getValue("myKey1"), is("myValue"));
    }

    @Test
    void shouldUseLocalPropertySourceWhenProfileIsNull() {
        // given
        mockConfigValue("app.name", "myApp");
        mockConfigValue("mp.config.profile", null);
        mockConfigValue("otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm");

        // when
        PropertySource source = new PropertySource();

        // then
        assertThat(source.getValue("myKey1"), is("myValue"));
    }

    @Test
    void shouldUseLocalPropertySourceWhenPofileIsMissing() {
        // given
        mockConfigValue("app.name", "myApp");
        mockConfigValue("otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm");

        // when
        PropertySource source = new PropertySource();

        // then
        assertThat(source.getValue("myKey1"), is("myValue"));
    }

    @Test
    void shouldUseDefaultPropertySourceWhenPofileIsNotLocalOrTest() {
        withMockedAwsClients(() -> {
            // given
            mockConfigValue("app.name", "myApp");
            mockConfigValue("mp.config.profile", "live");
            mockConfigValue("otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm");

            // when
            PropertySource source = new PropertySource();

            // then
            assertThat(source.getConfigurationProvider().getProperties().size(), is(0));
        });
    }
}