package de.otto.config.core.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import de.otto.config.core.Configuration;
import de.otto.config.core.ConfigurationCache;
import de.otto.config.core.Context;
import de.otto.config.core.Refreshable;
import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceAggregator;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Delegate;

@Getter
public abstract class Provider<T> implements Refreshable {
    protected final @NonNull Context context;

    private final Function<Object, T> valueTransformer;
    private final List<Class<? extends Configuration<?>>> filterTypes;
    private final boolean normalizeKeys;

    @Delegate
    protected final ConfigurationCache<T> configuration;
   
    public Provider(Context context, List<Class<? extends Configuration<?>>> filterTypes, Function<Object, T> valueTransformer, boolean normalizeKeys) {
        this(context, null, null, filterTypes, valueTransformer, normalizeKeys);
    }

    public Provider(Context context, List<Source<? extends Configuration<?>>> sources, List<Class<? extends Configuration<?>>> filterTypes, Function<Object, T> valueTransformer, boolean normalizeKeys) {
        this(context, null, sources, filterTypes, valueTransformer, normalizeKeys);
    }

    public Provider(Context context, Map<String, T> properties, List<Source<? extends Configuration<?>>> sources, List<Class<? extends Configuration<?>>> filterTypes, Function<Object, T> valueTransformer, boolean normalizeKeys) {
        this.context = context;
        this.context.getProviderRegistry().register(this);
        this.valueTransformer = valueTransformer;
        this.filterTypes = filterTypes;
        this.normalizeKeys = normalizeKeys;
        if (properties != null) {
            this.configuration = new ConfigurationCache<>(properties);
        } else {
            this.configuration = new ConfigurationCache<>();
            this.refresh();
        }
        if (sources != null && !sources.isEmpty()) {
            sources.forEach(this.context.getSourceRegistry()::register);
            this.refresh();
        }
    }

    public void addSource(Source<? extends Configuration<?>> source) {
        this.context.getSourceRegistry().register(source);
        this.refresh();
    }

    @Override
    public void refresh() {
        this.configuration.setProperties(SourceAggregator.aggregate(this.context.getSourceRegistry().filterByType(this.filterTypes), 
                                         this.valueTransformer, 
                                         this.normalizeKeys));
    }

    public void refreshInPlace() {
        this.configuration.setProperties(SourceAggregator.aggregate(this.context.getSourceRegistry().filterByType(this.filterTypes), 
                                         this.valueTransformer, 
                                         this.normalizeKeys,
                                         false));
    }
}
