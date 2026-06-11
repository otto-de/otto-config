package de.otto.config.integration.spring.env;

import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

public class EnvironmentPostProcessor implements org.springframework.boot.env.EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addAfter("systemEnvironment", new PropertySource(environment));
    }
}
