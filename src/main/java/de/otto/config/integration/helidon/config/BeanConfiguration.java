package de.otto.config.integration.helidon.config;

import de.otto.config.core.Context;
import de.otto.config.integration.helidon.spi.PropertySource;
import de.otto.config.provider.ConfigurationProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.stream.StreamSupport;

@Slf4j
@ApplicationScoped
public class BeanConfiguration {
    private final PropertySource propertySource;

    @Inject
    public BeanConfiguration() {
        this.propertySource = getPropertySource();
    }

    @Produces
    public Context context(Config config) {
        this.propertySource.getContext().setConfiguration(ApplicationConfiguration.builder().config(config).build());
        return this.propertySource.getContext();
    }

    @Produces
    public ConfigurationProvider configurationProvider() {
        return this.propertySource.getConfigurationProvider();
    }

    private PropertySource getPropertySource() {
        return StreamSupport.stream(ConfigProvider.getConfig().getConfigSources().spliterator(), false)
                            .filter(PropertySource.class::isInstance)
                            .map(PropertySource.class::cast)
                            .findFirst()
                            .orElseGet(PropertySource::new);
    }
}
