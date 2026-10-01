package de.otto.config.integration.micrometer;

import java.time.Duration;

import de.otto.config.core.metrics.ConfigMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * {@link ConfigMetrics} adapter backed by a Micrometer {@link MeterRegistry}.
 * <p>
 * Not auto-registered by otto-config. To enable it, either register the no-arg constructor via
 * {@code META-INF/services/de.otto.config.core.metrics.ConfigMetrics} in your own application, or call
 * {@code ConfigMetricsRegistry.register(new MicrometerConfigMetrics(registry))} once your {@link MeterRegistry}
 * bean is available.
 * <p>
 * The no-arg constructor binds to {@link Metrics#globalRegistry}, so it works via {@link java.util.ServiceLoader}
 * discovery even before Spring Boot has created its application-specific registry; Spring Boot binds its
 * auto-configured {@link MeterRegistry} to the global registry by default, so metrics recorded early are not lost.
 */
public class MicrometerConfigMetrics implements ConfigMetrics {
    private static final String SOURCE_REQUESTS = "otto.config.source.requests";
    private static final String SOURCE_LOADS = "otto.config.source.loads";
    private static final String HTTP_REQUESTS = "otto.config.http.requests";
    private static final String REFRESH = "otto.config.refresh";
    private static final String CHANGE_EVENTS = "otto.config.change.events";

    private final MeterRegistry registry;

    public MicrometerConfigMetrics() {
        this(Metrics.globalRegistry);
    }

    public MicrometerConfigMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void sourceRequested(String source, CacheResult result) {
        Counter.builder(SOURCE_REQUESTS)
               .description("Number of times a source was requested, grouped by cache result")
               .tag("source", source)
               .tag("result", result.name().toLowerCase())
               .register(registry)
               .increment();
    }

    @Override
    public void sourceLoaded(String source, Duration duration, boolean empty) {
        Timer.builder(SOURCE_LOADS)
             .description("Time taken to load a source, grouped by outcome")
             .tag("source", source)
             .tag("outcome", empty ? "empty" : "success")
             .tag("exception", "none")
             .register(registry)
             .record(duration);
    }

    @Override
    public void sourceLoadFailed(String source, Duration duration, Throwable error) {
        Timer.builder(SOURCE_LOADS)
             .description("Time taken to load a source, grouped by outcome")
             .tag("source", source)
             .tag("outcome", "failure")
             .tag("exception", error != null ? error.getClass().getSimpleName() : "none")
             .register(registry)
             .record(duration);
    }

    @Override
    public void httpRequest(String client, String method, int status, Duration duration) {
        Timer.builder(HTTP_REQUESTS)
             .description("Time taken by HTTP requests made by built-in REST clients")
             .tag("client", client)
             .tag("method", method)
             .tag("status", status == -1 ? "none" : String.valueOf(status))
             .register(registry)
             .record(duration);
    }

    @Override
    public void refresh(RefreshType type, Duration duration) {
        Timer.builder(REFRESH)
             .description("Time taken by a refresh cycle, grouped by type")
             .tag("type", type.name().toLowerCase())
             .register(registry)
             .record(duration);
    }

    @Override
    public void changeEventsReceived(int count) {
        Counter.builder(CHANGE_EVENTS)
               .description("Number of change-event messages received (e.g. from SQS)")
               .register(registry)
               .increment(count);
    }
}
