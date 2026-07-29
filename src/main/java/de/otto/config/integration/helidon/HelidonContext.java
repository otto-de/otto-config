package de.otto.config.integration.helidon;

import org.eclipse.microprofile.config.Config;

import de.otto.config.core.Context;
import de.otto.config.integration.helidon.config.ApplicationConfiguration;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class HelidonContext {
    
    public static Context createContext(Config config) {
        return createContext(config.getOptionalValue("app.name", String.class).orElse("unknown"),
                             false,
                             config);
    }

    public static Context createContext(String appName, Boolean excludeSecrets, Config config) {
        return Context.builder()
                      .appName(appName)
                      .profile(config.getOptionalValue("mp.config.profile", String.class).orElse("local"))
                      .excludeSecrets(excludeSecrets)
                      .configuration(ApplicationConfiguration.builder().config(config).build())
                      .build();
    }
}
