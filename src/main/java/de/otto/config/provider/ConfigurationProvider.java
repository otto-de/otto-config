package de.otto.config.provider;

import java.util.List;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.provider.Provider;
import de.otto.config.core.source.Source;
import de.otto.config.domain.Properties;
import de.otto.config.domain.Toggles;
import lombok.Builder;
import lombok.Singular;

public class ConfigurationProvider extends Provider<String> {

    @Builder
    public ConfigurationProvider(Context context, @Singular List<Source<? extends Configuration<?>>> sources) {
        super(context, sources, List.of(Properties.class, Toggles.class), String::valueOf, true);
    }
}
