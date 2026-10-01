package de.otto.config.core.metrics;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only {@link ConfigMetrics} implementation, discoverable via the {@code META-INF/services} entry in
 * {@code src/test/resources}, used to verify that {@link ConfigMetricsRegistry} discovery skips a broken
 * provider entry (declared alongside this one) and still picks up the remaining, working implementation.
 */
public class DiscoverableTestConfigMetrics implements ConfigMetrics {
    static final AtomicInteger changeEventsReceivedCount = new AtomicInteger();

    public DiscoverableTestConfigMetrics() {}

    static void reset() {
        changeEventsReceivedCount.set(0);
    }

    @Override
    public void changeEventsReceived(int count) {
        changeEventsReceivedCount.addAndGet(count);
    }
}
