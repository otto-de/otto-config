package de.otto.config.integration.spring;

import org.springframework.core.env.ConfigurableEnvironment;

import de.otto.config.core.Context;
import de.otto.config.integration.spring.config.ApplicationConfiguration;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class SpringContext {
    
    public static Context createContext(ConfigurableEnvironment environment) {
        return createContext(environment.getProperty("spring.application.name", "unknown"), false, environment);
    }

    public static Context createContext(String appName, Boolean excludeSecrets, ConfigurableEnvironment environment) {
        return Context.builder()
                      .appName(appName)
                      .excludeSecrets(excludeSecrets)
                      .profile(environment.getActiveProfiles().length > 0 ? environment.getActiveProfiles()[0] : "local")
                      .configuration(ApplicationConfiguration.builder().environment(environment).build())
                      .build();
    }
}
