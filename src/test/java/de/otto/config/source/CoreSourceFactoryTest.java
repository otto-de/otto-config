package de.otto.config.source;

import de.otto.config.core.ConfigurationCache;
import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceDiscovery;
import de.otto.config.domain.Properties;
import de.otto.config.domain.Toggles;
import de.otto.config.source.aws.AppConfigSource;
import de.otto.config.source.aws.SecretsManagerSource;
import de.otto.config.source.aws.SsmSource;
import de.otto.config.source.file.FileSource;
import de.otto.config.source.hashicorp.VaultSource;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static de.otto.config.fixture.MockAwsClients.withMockedAwsClients;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CoreSourceFactoryTest {

    @Test
    void shouldIncludeAppConfigSourceWhenEnabled() {
        withMockedAwsClients(() -> {
            // given
            ConfigurationCache<String> configuration = new ConfigurationCache<>(Map.of(
                    "otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles",
                    "otto.config.aws.secrets.arn", "arn:aws:secretsmanager:123"));
            Context context = Context.from("otto-config", "develop", configuration);

            // when
            List<Source<? extends Configuration<?>>> sources = SourceDiscovery.discover(context);

            // then
            assertThat(sources, hasSize(2));
            assertThat(sources.get(0), instanceOf(AppConfigSource.class));
        });
    }

    @Test
    void shouldIncludeSecretsManagerSourceWhenEnabled() {
        withMockedAwsClients(() -> {
            // given
            ConfigurationCache<String> configuration = new ConfigurationCache<>(Map.of(
                    "otto.config.sources.enabled", "aws.secrets",
                    "otto.config.aws.secrets.arn", "arn:aws:secretsmanager:123"
            ));
            Context context = Context.from("otto-config", "develop", configuration);

            // when
            List<Source<? extends Configuration<?>>> sources = SourceDiscovery.discover(context);

            // then
            assertThat(sources, hasSize(1));
            assertThat(sources.get(0), instanceOf(CombinedPropertySource.class));
            CombinedPropertySource combinedPropertySource = (CombinedPropertySource) sources.get(0);
            for (PropertySource source : combinedPropertySource.getSources()) {
                assertThat(source, instanceOf(SecretsManagerSource.class));
            }
        });
    }

    @Test
    void shouldIncludeSsmSourceWhenEnabled() {
        withMockedAwsClients(() -> {
            // given
            ConfigurationCache<String> configuration = new ConfigurationCache<>(Map.of(
                    "otto.config.sources.enabled", "aws.ssm",
                    "otto.config.aws.secrets.arn", "arn:aws:secretsmanager:123"
            ));
            Context context = Context.from("otto-config", "develop", configuration);

            // when
            List<Source<? extends Configuration<?>>> sources = SourceDiscovery.discover(context);

            // then
            assertThat(sources, hasSize(1));
            assertThat(sources.get(0), instanceOf(CombinedPropertySource.class));
            CombinedPropertySource combinedPropertySource = (CombinedPropertySource) sources.get(0);
            for (PropertySource source : combinedPropertySource.getSources()) {
                assertThat(source, instanceOf(SsmSource.class));
            }
        });
    }

    @Test
    void shouldIncludeVaultSourceWhenEnabled() {
        withMockedAwsClients(() -> {
            // given
            ConfigurationCache<String> configuration = new ConfigurationCache<>(Map.of(
                    "otto.config.sources.enabled", "hashicorp.vault",
                    "otto.config.hashicorp.vault.url", "http://localhost:8200",
                    "otto.config.hashicorp.vault.token", "my-token",
                    "otto.config.hashicorp.vault.path", "my-secret"
            ));
            Context context = Context.from("otto-config", "develop", configuration);

            // when
            List<Source<? extends Configuration<?>>> sources = SourceDiscovery.discover(context);

            // then
            assertThat(sources, hasSize(1));
            assertThat(sources.get(0), instanceOf(CombinedPropertySource.class));
            CombinedPropertySource combinedPropertySource = (CombinedPropertySource) sources.get(0);
            for (PropertySource source : combinedPropertySource.getSources()) {
                assertThat(source, instanceOf(VaultSource.class));
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldCreateFileSourceWhenLocalProfileUsed() {
        // given
        ConfigurationCache<String> configuration = new ConfigurationCache<>(Map.of(
                "otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles",
                "otto.config.aws.secrets.arn", "arn:aws:secretsmanager:123"
        ));
        Context context = Context.from("otto-config", "local", configuration);

        // when
        List<Source<? extends Configuration<?>>> sources = SourceDiscovery.discover(context);

        // then
        assertThat(sources, hasSize(2));
        assertThat(sources.get(0), instanceOf(FileSource.class));

        FileSource<Toggles> toggleFileSource = (FileSource<Toggles>) sources.get(0);
        Toggles toggles = toggleFileSource.getOrLoad();
        assertThat(toggles.getProperties(), aMapWithSize(2));
        assertThat(toggles.getProperties(), hasEntry("another_toggle", false));
        assertThat(toggles.getProperties(), hasEntry("ftsn-415-test-toggle", true));


        FileSource<Properties> propertyFileSource = (FileSource<Properties>) sources.get(1);
        Properties properties = propertyFileSource.getOrLoad();
        assertThat(properties.getProperties(), aMapWithSize(10));
        assertThat(properties.getProperties(), hasEntry("myKey1", "myValue"));
        assertThat(properties.getProperties(), hasEntry("myKey2", "myValue1;myValue2"));
        assertThat(properties.getProperties(), hasEntry("myKey3", "myValue"));
        assertThat(properties.getProperties(), hasEntry("myKey4", "myValue"));
        assertThat(properties.getProperties(), hasEntry("myKey5", "myValue"));
        assertThat(properties.getProperties(), hasEntry("my.ssm.property1", "ssm-value1"));
        assertThat(properties.getProperties(), hasEntry("my.ssm.property2", "ssm-value2"));
        assertEquals("secret-value1", properties.getProperties().get("my.secret1"));
        assertEquals("secret-value2", properties.getProperties().get("my.secret2"));
    }

    @Test
    void shouldIncludeAllSourcesWhenAllEnabled() {
        withMockedAwsClients(() -> {
            // given
            ConfigurationCache<String> configuration = new ConfigurationCache<>(Map.of(
                    "otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm,hashicorp.vault",
                    "otto.config.aws.secrets.arn", "arn:aws:secretsmanager:123",
                    "otto.config.hashicorp.vault.url", "http://localhost:8200",
                    "otto.config.hashicorp.vault.token", "my-token",
                    "otto.config.hashicorp.vault.path", "my-secret"
            ));
            Context context = Context.from("otto-config", "develop", configuration);

            // when
            List<Source<? extends Configuration<?>>> sources = SourceDiscovery.discover(context);

            // then
            assertThat(sources, hasSize(5));
            
            // Check AppConfig sources
            long appConfigSourceCount = sources.stream()
                    .filter(source -> source instanceof AppConfigSource)
                    .count();
            assertThat(appConfigSourceCount, is(2L)); // properties, toggles
            
            // Check combined property sources
            List<CombinedPropertySource> combinedSources = sources.stream()
                    .filter(source -> source instanceof CombinedPropertySource)
                    .map(source -> (CombinedPropertySource) source)
                    .toList();
            assertThat(combinedSources, hasSize(3)); // secrets, ssm, vault
            
            // Verify each combined source contains the expected PropertySource type
            boolean hasSecretsManagerSource = combinedSources.stream()
                    .anyMatch(combined -> combined.getSources().stream()
                            .anyMatch(source -> source instanceof SecretsManagerSource));
            assertThat(hasSecretsManagerSource, is(true));
            
            boolean hasSsmSource = combinedSources.stream()
                    .anyMatch(combined -> combined.getSources().stream()
                            .anyMatch(source -> source instanceof SsmSource));
            assertThat(hasSsmSource, is(true));
            
            boolean hasVaultSource = combinedSources.stream()
                    .anyMatch(combined -> combined.getSources().stream()
                            .anyMatch(source -> source instanceof VaultSource));
            assertThat(hasVaultSource, is(true));
        });
    }

    @Test
    void shouldIncludeNoSourcesWhenAllDisabled() {
        withMockedAwsClients(() -> {
            // given
            ConfigurationCache<String> configuration = new ConfigurationCache<>(Map.of(
                    "otto.config.sources.enabled", "",
                    "otto.config.aws.secrets.arn", "arn:aws:secretsmanager:123"
            ));
            Context context = Context.from("otto-config", "develop", configuration);

            // when
            List<Source<? extends Configuration<?>>> sources = SourceDiscovery.discover(context);

            // then
            assertThat(sources, hasSize(0));
        });
    }

    @Test
    void shouldReturnTrueWhenProfileIsNullAndFileIsAvailable() {
        // when
        boolean isLocal = CoreSourceFactory.isLocalProfile(null);

        // then
        assertThat(isLocal, is(true));
    }

    @Test
    void shouldReturnTrueWhenProfileIsLocalAndFileIsAvailable() {
        // when
        boolean isLocal = CoreSourceFactory.isLocalProfile("local");

        // then
        assertThat(isLocal, is(true));
    }

    @Test
    void shouldReturnTrueWhenProfileIsTestAndFileIsAvailable() {
        // when
        boolean isLocal = CoreSourceFactory.isLocalProfile("test");

        // then
        assertThat(isLocal, is(true));
    }

    @Test
    void shouldReturnTrueWhenProfileIsIntegrationTestAndFileIsAvailable() {
        // when
        boolean isLocal = CoreSourceFactory.isLocalProfile("integration-test");

        // then
        assertThat(isLocal, is(true));
    }

    @Test
    void shouldReturnTrueWhenProfileIsLocalWithDifferentCasingAndFileIsAvailable() {
        // when
        boolean isLocal = CoreSourceFactory.isLocalProfile("LOCAL");

        // then
        assertThat(isLocal, is(true));
    }

    @Test
    void shouldReturnFalseWhenProfileIsNotLocalAndFileIsAvailable() {
        // when
        boolean isLocal = CoreSourceFactory.isLocalProfile("develop");

        // then
        assertThat(isLocal, is(false));
    }

    @Test
    void shouldReturnFalseWhenProfileIsLocalButFileIsNotAvailable() throws Exception {
        // given
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader mockClassLoader = new ClassLoader(originalClassLoader) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                if ("properties.json".equals(name)) {
                    return null;
                }
                return super.getResourceAsStream(name);
            }
        };

        try {
            Thread.currentThread().setContextClassLoader(mockClassLoader);

            // when
            boolean isLocal = CoreSourceFactory.isLocalProfile("local");

            // then
            assertThat(isLocal, is(false));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void shouldReturnFalseWhenProfileIsNullButFileIsNotAvailable() throws Exception {
        // given
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader mockClassLoader = new ClassLoader(originalClassLoader) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                if ("properties.json".equals(name)) {
                    return null;
                }
                return super.getResourceAsStream(name);
            }
        };

        try {
            Thread.currentThread().setContextClassLoader(mockClassLoader);

            // when
            boolean isLocal = CoreSourceFactory.isLocalProfile(null);

            // then
            assertThat(isLocal, is(false));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }


    @Test
    void shouldReturnTrueWhenLocalFileSourceIsAvailable() {
        // when
        boolean isAvailable = CoreSourceFactory.isLocalFileSourceAvailable();

        // then
        assertThat(isAvailable, is(true));
    }

    @Test
    void shouldReturnFalseWhenLocalFileSourceIsNotAvailable() throws Exception {
        // given
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader mockClassLoader = new ClassLoader(originalClassLoader) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                if ("properties.json".equals(name)) {
                    return null;
                }
                return super.getResourceAsStream(name);
            }
        };

        try {
            Thread.currentThread().setContextClassLoader(mockClassLoader);

            // when
            boolean isAvailable = CoreSourceFactory.isLocalFileSourceAvailable();

            // then
            assertThat(isAvailable, is(false));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
