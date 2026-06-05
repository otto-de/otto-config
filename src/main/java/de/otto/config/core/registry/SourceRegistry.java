package de.otto.config.core.registry;

import java.util.List;
import java.util.stream.Collectors;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceDiscovery;
import lombok.Builder;

public final class SourceRegistry extends ListRegistry<Source<? extends Configuration<?>>> {
    
    public static SourceRegistry from(Context context) {
        return SourceRegistry.builder()
                             .sources(SourceDiscovery.discover(context))
                             .build();
    }

    @Builder
    public SourceRegistry(List<Source<? extends Configuration<?>>> sources) {
        this.values.addAll(sources);
    }

    public final List<Source<? extends Configuration<?>>> filterByType(List<Class<? extends Configuration<?>>> types) {
        return values.stream()
                     .filter(source -> types.contains(source.getTypeReference().getType()))
                     .collect(Collectors.toList());
    }
}
