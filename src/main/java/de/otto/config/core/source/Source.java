package de.otto.config.core.source;

import com.fasterxml.jackson.core.type.TypeReference;

import de.otto.config.core.Configuration;
import de.otto.config.core.Refreshable;
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
        try {
            if (forceReload || (cache == null || cache.isEmpty())) {
                T value = load();
                if (value != null && !value.isEmpty()) {
                    cache = value;
                }
            }
        } catch (SourceException e) {
            log.error("Error loading configuration from source", e);
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
