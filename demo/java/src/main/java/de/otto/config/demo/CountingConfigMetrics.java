package de.otto.config.demo;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import de.otto.config.core.metrics.ConfigMetrics;

/**
 * Minimal example {@link ConfigMetrics} implementation: just in-memory, thread-safe counters,
 * no external dependency. Registered via {@code META-INF/services/de.otto.config.core.metrics.ConfigMetrics}
 * so {@link de.otto.config.core.metrics.ConfigMetricsRegistry} picks it up automatically.
 *
 * Real implementations would typically forward to a metrics system (Micrometer, Prometheus client, ...)
 * instead of keeping counters in memory; see the Spring demo for that approach.
 */
public class CountingConfigMetrics implements ConfigMetrics {

    // ServiceLoader instantiates this class for us, so we stash the instance here to let
    // Main print a summary. A real implementation would forward to its metrics backend instead.
    private static volatile CountingConfigMetrics instance;

    private final Map<String, LongAdder> sourceRequests = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> sourceLoads = new ConcurrentHashMap<>();
    private final LongAdder sourceLoadFailures = new LongAdder();
    private final LongAdder httpRequests = new LongAdder();
    private final Map<String, LongAdder> refreshes = new ConcurrentHashMap<>();
    private final LongAdder changeEvents = new LongAdder();

    public CountingConfigMetrics() {
        instance = this;
    }

    /** Returns the summary of the ServiceLoader-registered instance, or {@code null} if none was created yet. */
    public static String currentSummary() {
        CountingConfigMetrics current = instance;
        return current == null ? null : current.summary();
    }

    @Override
    public void sourceRequested(String source, CacheResult result) {
        sourceRequests.computeIfAbsent(source + "/" + result, key -> new LongAdder()).increment();
    }

    @Override
    public void sourceLoaded(String source, Duration duration, boolean empty) {
        sourceLoads.computeIfAbsent(source + (empty ? "/empty" : "/loaded"), key -> new LongAdder()).increment();
    }

    @Override
    public void sourceLoadFailed(String source, Duration duration, Throwable error) {
        sourceLoadFailures.increment();
    }

    @Override
    public void httpRequest(String client, String method, int status, Duration duration) {
        httpRequests.increment();
    }

    @Override
    public void refresh(RefreshType type, Duration duration) {
        refreshes.computeIfAbsent(type.name(), key -> new LongAdder()).increment();
    }

    @Override
    public void changeEventsReceived(int count) {
        changeEvents.add(count);
    }

    /** Renders a short, human-readable summary of all counters collected so far. */
    public String summary() {
        StringBuilder sb = new StringBuilder("ConfigMetrics summary:");
        sourceRequests.forEach((key, value) -> sb.append("\n  source.requests[").append(key).append("]=").append(value.sum()));
        sourceLoads.forEach((key, value) -> sb.append("\n  source.loads[").append(key).append("]=").append(value.sum()));
        sb.append("\n  source.loadFailures=").append(sourceLoadFailures.sum());
        sb.append("\n  http.requests=").append(httpRequests.sum());
        refreshes.forEach((key, value) -> sb.append("\n  refresh[").append(key).append("]=").append(value.sum()));
        sb.append("\n  change.events=").append(changeEvents.sum());
        return sb.toString();
    }
}
