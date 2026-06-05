package de.otto.config.provider;

import java.util.List;
import java.util.Map;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.provider.Provider;
import de.otto.config.core.source.Source;
import de.otto.config.domain.Experiments;
import lombok.Builder;
import lombok.Singular;

public class ExperimentProvider extends Provider<Experiments.Groups> {

    @Builder
    public ExperimentProvider(Context context, @Singular List<Source<? extends Configuration<?>>> sources, Map<String, Experiments.Groups> properties) {
        super(context, properties, sources, List.of(Experiments.class), Experiments.Groups.class::cast, false);
    }
}
