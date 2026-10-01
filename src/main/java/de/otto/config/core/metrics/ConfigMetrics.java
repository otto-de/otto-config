package de.otto.config.core.metrics;

import java.time.Duration;

/**
 * Pluggable metrics hook. Implement this interface and register it via
 * {@code META-INF/services/de.otto.config.core.metrics.ConfigMetrics} (or {@link ConfigMetricsRegistry#register})
 * to receive measurements from otto-config. All methods are no-ops by default, so implementations only
 * override what they need. Implementations must be thread-safe and fast; exceptions thrown by them are
 * caught and logged and never affect configuration loading.
 */
public interface ConfigMetrics {
    ConfigMetrics NOOP = new ConfigMetrics() {};

    enum CacheResult {
        /** Served from cache, no load. */
        HIT,
        /** Cache was empty, source had to be loaded. */
        MISS,
        /** Load forced by a refresh, regardless of cache state. */
        REFRESH
    }

    enum RefreshType {
        /** Scheduled full refresh of all sources ({@code otto.config.refresh.interval}). */
        FULL,
        /** Change-event poll ({@code otto.config.refresh.poll.interval}). */
        POLL
    }

    /** A source was asked for its values. {@code source} is the source's simple class name, e.g. {@code VaultSource}. */
    default void sourceRequested(String source, CacheResult result) {}

    /** A source load succeeded. {@code empty} results are not cached and will be loaded again on the next request. */
    default void sourceLoaded(String source, Duration duration, boolean empty) {}

    /** A source load failed; the previously cached value (if any) keeps being served. */
    default void sourceLoadFailed(String source, Duration duration, Throwable error) {}

    /**
     * An HTTP call made by a built-in REST client (e.g. Vault). {@code client} is the client's simple class name,
     * {@code status} is the HTTP status, or {@code -1} if no response was received.
     */
    default void httpRequest(String client, String method, int status, Duration duration) {}

    /** A refresh cycle completed. */
    default void refresh(RefreshType type, Duration duration) {}

    /** Change-event messages were received (e.g. from SQS). */
    default void changeEventsReceived(int count) {}
}
