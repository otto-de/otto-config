package de.otto.config.integration.helidon.spi;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

import de.otto.config.core.Context;
import de.otto.config.integration.helidon.HelidonContext;
import de.otto.config.provider.ConfigurationProvider;

@Slf4j
@Getter
public class PropertySource implements ConfigSource {
    public static final String NAME = "otto-config";
    private static final int ORDINAL = 500; //MpEnvironmentVariablesSource has 300 and MpSystemPropertiesSource has 400
    private final Context context;
    private final ConfigurationProvider configurationProvider;

    public PropertySource() {
        this.context = HelidonContext.createContext(ConfigProviderResolver.instance()
                                                                          .getBuilder()
                                                                          .addDefaultSources()
                                                                          .build());
        this.configurationProvider = ConfigurationProvider.builder()
                                                          .context(this.context)
                                                          .build();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public Map<String, String> getProperties() {
        return configurationProvider.getProperties();
    }

    @Override
    public Set<String> getPropertyNames() {
        return configurationProvider.getPropertyNames();
    }

    @Override
    public String getValue(String propertyName) {
        return configurationProvider.getValue(propertyName);
    }
}
