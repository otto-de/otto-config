package de.otto.config.integration.spring.env;

import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Registers otto-config with Spring Boot 3. Spring Boot 4 uses {@code SpringBoot4EnvironmentPostProcessor}.
 */
public class EnvironmentPostProcessor implements org.springframework.boot.env.EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        PropertySource.addTo(environment);
    }
}
