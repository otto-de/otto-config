package de.otto.config.demo;

import java.time.Duration;
import java.util.List;

import de.otto.config.core.metrics.ConfigMetrics;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

/**
 * {@link ConfigMetrics} adapter that bridges otto-config metrics into Helidon's own metrics registry
 * ({@link Metrics#globalRegistry()}), so they show up in Helidon's built-in {@code /metrics} endpoint
 * alongside the base/vendor metrics, instead of behind a separate custom endpoint.
 * <p>
 * Registered via {@code META-INF/services/de.otto.config.core.metrics.ConfigMetrics}. Uses the same metric
 * names and tags as the library's Micrometer adapter ({@code de.otto.config.integration.micrometer.MicrometerConfigMetrics}),
 * just built with Helidon's Micrometer-like metrics API ({@code io.helidon.metrics.api}).
 */
public class HelidonConfigMetrics implements ConfigMetrics {
    private static final String SOURCE_REQUESTS = "otto.config.source.requests";
    private static final String SOURCE_LOADS = "otto.config.source.loads";
    private static final String HTTP_REQUESTS = "otto.config.http.requests";
    private static final String REFRESH = "otto.config.refresh";
    private static final String CHANGE_EVENTS = "otto.config.change.events";

    private final MeterRegistry registry;

    public HelidonConfigMetrics() {
        this(Metrics.globalRegistry());
    }

    public HelidonConfigMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void sourceRequested(String source, CacheResult result) {
        registry.getOrCreate(Counter.builder(SOURCE_REQUESTS)
                                     .description("Number of times a source was requested, grouped by cache result")
                                     .tags(List.of(Tag.create("source", source),
                                                    Tag.create("result", result.name().toLowerCase()))))
                .increment();
    }

    @Override
    public void sourceLoaded(String source, Duration duration, boolean empty) {
        registry.getOrCreate(Timer.builder(SOURCE_LOADS)
                                   .description("Time taken to load a source, grouped by outcome")
                                   .tags(List.of(Tag.create("source", source),
                                                  Tag.create("outcome", empty ? "empty" : "success"),
                                                  Tag.create("exception", "none"))))
                .record(duration);
    }

    @Override
    public void sourceLoadFailed(String source, Duration duration, Throwable error) {
        registry.getOrCreate(Timer.builder(SOURCE_LOADS)
                                   .description("Time taken to load a source, grouped by outcome")
                                   .tags(List.of(Tag.create("source", source),
                                                  Tag.create("outcome", "failure"),
                                                  Tag.create("exception", error != null ? error.getClass().getSimpleName() : "none"))))
                .record(duration);
    }

    @Override
    public void httpRequest(String client, String method, int status, Duration duration) {
        registry.getOrCreate(Timer.builder(HTTP_REQUESTS)
                                   .description("Time taken by HTTP requests made by built-in REST clients")
                                   .tags(List.of(Tag.create("client", client),
                                                  Tag.create("method", method),
                                                  Tag.create("status", status == -1 ? "none" : String.valueOf(status)))))
                .record(duration);
    }

    @Override
    public void refresh(RefreshType type, Duration duration) {
        registry.getOrCreate(Timer.builder(REFRESH)
                                   .description("Time taken by a refresh cycle, grouped by type")
                                   .tags(List.of(Tag.create("type", type.name().toLowerCase()))))
                .record(duration);
    }

    @Override
    public void changeEventsReceived(int count) {
        registry.getOrCreate(Counter.builder(CHANGE_EVENTS)
                                     .description("Number of change-event messages received (e.g. from SQS)"))
                .increment(count);
    }
}
