package de.otto.config.integration.helidon.endpoint;

import org.eclipse.microprofile.config.ConfigProvider;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;

public class ConfigurationEndpointExtension implements Extension {

    <T> void vetoIfDisabled(@Observes ProcessAnnotatedType<T> processAnnotationType) {
        if (processAnnotationType.getAnnotatedType().getJavaClass().equals(ConfigurationEndpoint.class)) {
            boolean enabled = ConfigProvider.getConfig()
                                            .getOptionalValue("otto.config.endpoint.configs.enabled", Boolean.class)
                                            .orElse(false);
            if (!enabled) {
                processAnnotationType.veto();
            }
        }
    }
}
