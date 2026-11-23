package de.otto.config.integration.spring.env;

import org.springframework.core.env.ConfigurableEnvironment;

import de.otto.config.core.Context;
import de.otto.config.integration.spring.SpringContext;
import de.otto.config.provider.ConfigurationProvider;
import lombok.Getter;

@Getter
public class PropertySource extends org.springframework.core.env.PropertySource<String> {
    public static final String NAME = "otto-config";
    private final Context context;
    private final ConfigurationProvider configurationProvider;

    public PropertySource(ConfigurableEnvironment environment) {
        super(NAME);

        this.context = SpringContext.createContext(environment);
        this.configurationProvider = ConfigurationProvider.builder()
                                                          .context(this.context)
                                                          .build();
    }

    /**
     * Adds the otto-config property source right after the system environment, unless the
     * environment already has it. Spring Boot 4 runs the post processors of both
     * {@code spring.factories} keys, so registering more than once must not create a second
     * configuration provider.
     */
    public static void addTo(ConfigurableEnvironment environment) {
        if (!environment.getPropertySources().contains(NAME)) {
            environment.getPropertySources().addAfter("systemEnvironment", new PropertySource(environment));
        }
    }

    @Override
    public Object getProperty(String name) {
        return this.configurationProvider.getValue(name);
    }
}
