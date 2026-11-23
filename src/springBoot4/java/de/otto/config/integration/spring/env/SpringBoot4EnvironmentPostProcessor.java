package de.otto.config.integration.spring.env;

import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Registers otto-config with Spring Boot 4, which moved {@code EnvironmentPostProcessor} to
 * {@code org.springframework.boot} and deprecated the old interface for removal. Spring Boot 3
 * never loads this class: it does not read the {@code spring.factories} key it is registered under.
 */
public class SpringBoot4EnvironmentPostProcessor implements org.springframework.boot.EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        PropertySource.addTo(environment);
    }
}
