package de.otto.config.core.source;

import java.time.Duration;

import com.fasterxml.jackson.core.type.TypeReference;

import de.otto.config.core.Configuration;
import de.otto.config.core.Refreshable;
import de.otto.config.core.metrics.ConfigMetrics;
import de.otto.config.core.metrics.ConfigMetrics.CacheResult;
import de.otto.config.core.metrics.ConfigMetricsRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Source<T extends Configuration<?>> implements Refreshable {
    protected volatile T cache;

    public abstract TypeReference<T> getTypeReference();
    public abstract T load() throws SourceException;
    public abstract T getEmptyValue();

    public boolean hasSecrets() {
        return false;
    }

    public T getOrLoad() {
        return getOrLoad(isPullRefreshEnabled());
    }

    public T getOrLoad(boolean forceReload) {
        String name = getClass().getSimpleName();
        ConfigMetrics metrics = ConfigMetricsRegistry.get();
        if (!(forceReload || (cache == null || cache.isEmpty()))) {
            metrics.sourceRequested(name, CacheResult.HIT);
            return cache != null ? cache : getEmptyValue();
        }
        CacheResult result = forceReload ? CacheResult.REFRESH : CacheResult.MISS;
        metrics.sourceRequested(name, result);
        long start = System.nanoTime();
        try {
            T value = load();
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            metrics.sourceLoaded(name, duration, value == null || value.isEmpty());
            if (value != null && !value.isEmpty()) {
                cache = value;
            }
        } catch (SourceException e) {
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            metrics.sourceLoadFailed(name, duration, e);
            log.error("Error loading configuration from source {}: {}", name, e.getMessage(), e);
        } catch (RuntimeException e) {
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            metrics.sourceLoadFailed(name, duration, e);
            throw e;
        }
        return cache != null ? cache : getEmptyValue();
    }

    public void refresh() {
        getOrLoad(true);
    }

    public boolean onChanged(SourceChangeEvent event) {
        // No-op by default; override in sources that support event-based refresh
        return false;
    }

    public boolean isPullRefreshEnabled() {
        // By default sources support pull-based refresh; override if not supported
        return true;
    }
}
