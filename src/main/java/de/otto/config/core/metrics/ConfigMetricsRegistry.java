package de.otto.config.core.metrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Holds the active {@link ConfigMetrics}. Metrics are <strong>disabled by default</strong> (opt-in):
 * {@link #get()} returns {@link ConfigMetrics#NOOP} and {@link ServiceLoader} discovery is skipped unless
 * {@link #setEnabled(boolean)} has been called with {@code true} (e.g. driven by the
 * {@code otto.config.metrics.enabled} property, read by {@code Context}'s constructor). Once enabled,
 * implementations registered via {@link ServiceLoader} are discovered on first access; if none exist,
 * {@link ConfigMetrics#NOOP} is used. {@link #register(ConfigMetrics)} replaces the active implementation
 * programmatically, but still only takes effect once metrics are enabled — {@link #get()} returns
 * {@link ConfigMetrics#NOOP} while disabled regardless of what was registered or is discoverable.
 */
@Slf4j
public final class ConfigMetricsRegistry {
    private static volatile ConfigMetrics current;
    private static volatile boolean enabled = false;
    private static final int MAX_DISCOVERY_FAILURES = 32;

    private ConfigMetricsRegistry() {}

    public static ConfigMetrics get() {
        if (!enabled) {
            return ConfigMetrics.NOOP;
        }
        ConfigMetrics metrics = current;
        if (metrics == null) {
            synchronized (ConfigMetricsRegistry.class) {
                if (current == null) {
                    current = discover();
                }
                metrics = current;
            }
        }
        return metrics;
    }

    /**
     * Registers an implementation to be used by {@link #get()}. Takes effect only while metrics are
     * enabled (see {@link #setEnabled(boolean)}); while disabled, {@link #get()} still returns
     * {@link ConfigMetrics#NOOP} even after calling this method.
     */
    public static void register(ConfigMetrics metrics) {
        current = metrics == null ? ConfigMetrics.NOOP : new SafeConfigMetrics(List.of(metrics));
    }

    /**
     * Enables or disables metrics reporting entirely. Metrics are disabled by default (opt-in). While
     * disabled, {@link #get()} returns {@link ConfigMetrics#NOOP} without performing discovery, even if
     * an implementation was previously registered or is discoverable on the classpath. Enabling allows
     * discovery (or a previously/subsequently registered implementation) to take effect.
     */
    public static void setEnabled(boolean enabled) {
        ConfigMetricsRegistry.enabled = enabled;
    }

    /** Clears the active implementation and resets metrics back to disabled (the default) so the next
     * {@link #get()} re-runs discovery only after {@link #setEnabled(boolean)} is called again. */
    public static void reset() {
        current = null;
        enabled = false;
    }

    private static ConfigMetrics discover() {
        List<ConfigMetrics> found = new ArrayList<>();
        try {
            Iterator<ServiceLoader.Provider<ConfigMetrics>> providers = ServiceLoader.load(ConfigMetrics.class).stream().iterator();
            int failures = 0;
            while (failures < MAX_DISCOVERY_FAILURES) {
                // Note: hasNext() itself can throw (e.g. ServiceConfigurationError for a provider name
                // that doesn't resolve to a class on the classpath), not just next()/get(); it must be
                // guarded too, and the iterator can still be advanced afterwards to reach later entries.
                boolean hasNext;
                try {
                    hasNext = providers.hasNext();
                } catch (Throwable e) {
                    failures++;
                    log.warn("Skipping ConfigMetrics provider that failed to resolve: {}", e.toString());
                    continue;
                }
                if (!hasNext) {
                    break;
                }
                try {
                    found.add(providers.next().get());
                } catch (Throwable e) {
                    // e.g. a registered implementation whose runtime dependency (such as Micrometer) is
                    // missing from the classpath throws NoClassDefFoundError on instantiation; skip it and
                    // keep loading the remaining providers instead of breaking configuration loading.
                    failures++;
                    log.warn("Skipping ConfigMetrics provider that failed to load: {}", e.toString());
                }
            }
        } catch (Throwable e) {
            log.error("Failed to discover ConfigMetrics implementations, metrics are disabled: {}", e.getMessage(), e);
            return ConfigMetrics.NOOP;
        }
        if (found.isEmpty()) {
            log.debug("No ConfigMetrics implementations found; metrics are disabled");
            return ConfigMetrics.NOOP;
        }
        log.info("Otto Config metrics enabled: {}", found.stream().map(m -> m.getClass().getName()).toList());
        return new SafeConfigMetrics(found);
    }

    /** Fans out to all implementations and isolates failures so metrics never break configuration loading. */
    private record SafeConfigMetrics(List<ConfigMetrics> delegates) implements ConfigMetrics {
        @Override
        public void sourceRequested(String source, CacheResult result) {
            forEach(m -> m.sourceRequested(source, result));
        }

        @Override
        public void sourceLoaded(String source, Duration duration, boolean empty) {
            forEach(m -> m.sourceLoaded(source, duration, empty));
        }

        @Override
        public void sourceLoadFailed(String source, Duration duration, Throwable error) {
            forEach(m -> m.sourceLoadFailed(source, duration, error));
        }

        @Override
        public void httpRequest(String client, String method, int status, Duration duration) {
            forEach(m -> m.httpRequest(client, method, status, duration));
        }

        @Override
        public void refresh(RefreshType type, Duration duration) {
            forEach(m -> m.refresh(type, duration));
        }

        @Override
        public void changeEventsReceived(int count) {
            forEach(m -> m.changeEventsReceived(count));
        }

        private void forEach(Consumer<ConfigMetrics> call) {
            for (ConfigMetrics delegate : delegates) {
                try {
                    call.accept(delegate);
                } catch (Throwable e) {
                    log.warn("ConfigMetrics implementation {} failed: {}", delegate.getClass().getName(), e.toString());
                }
            }
        }
    }
}

