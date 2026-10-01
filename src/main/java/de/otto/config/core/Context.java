package de.otto.config.core;

import java.time.Duration;
import java.util.List;

import de.otto.config.core.metrics.ConfigMetrics.RefreshType;
import de.otto.config.core.metrics.ConfigMetricsRegistry;
import de.otto.config.core.registry.ClientRegistry;
import de.otto.config.core.registry.PropertyRegistry;
import de.otto.config.core.registry.ProviderRegistry;
import de.otto.config.core.registry.SourceRegistry;
import de.otto.config.core.source.SourceChangeEventListener;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
public final class Context {
    private final @NonNull String appName;
    private final String profile;
    private final Boolean excludeSecrets;
    @Setter
    private volatile Configuration<String> configuration;
    private final ClientRegistry clientRegistry;
    private final SourceRegistry sourceRegistry;
    private final ProviderRegistry providerRegistry;
    private final PropertyRegistry propertyRegistry;
    private final List<SourceChangeEventListener> sourceChangeEventListeners;
    
    @Builder
    public Context(String appName,
                   String profile,
                   Boolean excludeSecrets,
                   Configuration<String> configuration,
                   ClientRegistry clientRegistry,
                   PropertyRegistry propertyRegistry) {
        this.appName = appName;
        this.profile = profile;
        this.excludeSecrets = excludeSecrets != null ? excludeSecrets : Boolean.FALSE;
        this.configuration = configuration;
        this.clientRegistry = clientRegistry != null ? clientRegistry : ClientRegistry.createDefault();
        // Metrics are process-wide: a Context may only switch them on, so a secondary Context without the property can't disable them
        if (configuration != null && configuration.getValueAsBoolean("otto.config.metrics.enabled", false)) {
            ConfigMetricsRegistry.setEnabled(true);
        }
        this.sourceRegistry = SourceRegistry.from(this);
        this.providerRegistry = ProviderRegistry.builder().build();
        this.propertyRegistry = propertyRegistry != null ? propertyRegistry : PropertyRegistry.builder().build();
        this.sourceChangeEventListeners = SourceChangeEventListener.from(this);
    }

    public void refresh() {
        long start = System.nanoTime();
        providerRegistry.refresh();
        propertyRegistry.refresh();
        ConfigMetricsRegistry.get().refresh(RefreshType.FULL, Duration.ofNanos(System.nanoTime() - start));
    }

    @SuppressWarnings("null")
    public void pollAndRefresh() {
        if (!sourceChangeEventListeners.isEmpty()) {
            long start = System.nanoTime();
            sourceChangeEventListeners.forEach(SourceChangeEventListener::pollAndRefresh);
            providerRegistry.refreshInPlace();
            propertyRegistry.refreshInPlace();
            ConfigMetricsRegistry.get().refresh(RefreshType.POLL, Duration.ofNanos(System.nanoTime() - start));
        }
    }

    public static Context from(String appName) {
        return from(appName, "default", new ConfigurationCache<String>());
    }

    public static Context from(String appName, String profile, Configuration<String> configuration) {
        return Context.builder()
                      .appName(appName)
                      .profile(profile)
                      .configuration(configuration)
                      .build();
    }
}
