package de.otto.config.integration.spring.env;

import org.springframework.core.env.ConfigurableEnvironment;

import de.otto.config.core.Context;
import de.otto.config.integration.spring.config.ApplicationConfiguration;
import de.otto.config.provider.ConfigurationProvider;
import lombok.Getter;

@Getter
public class PropertySource extends org.springframework.core.env.PropertySource<String> {
    public static final String NAME = "otto-config";
    private final Context context;
    private final ConfigurationProvider configurationProvider;

    public PropertySource(ConfigurableEnvironment environment) {
        super(NAME);

        this.context = createContext(environment);
        this.configurationProvider = ConfigurationProvider.builder()
                                                          .context(this.context)
                                                          .build();
    }

    @Override
    public Object getProperty(String name) {
        return this.configurationProvider.getValue(name);
    }

    private Context createContext(ConfigurableEnvironment environment) {
        return Context.from(environment.getProperty("spring.application.name", "unknown"),
                            environment.getActiveProfiles().length > 0 ? environment.getActiveProfiles()[0] : "local",
                            ApplicationConfiguration.builder().environment(environment).build());
    }
}
