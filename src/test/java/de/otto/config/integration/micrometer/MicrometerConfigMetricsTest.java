package de.otto.config.integration.micrometer;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.otto.config.core.metrics.ConfigMetrics.CacheResult;
import de.otto.config.core.metrics.ConfigMetrics.RefreshType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class MicrometerConfigMetricsTest {

    private SimpleMeterRegistry registry;
    private MicrometerConfigMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerConfigMetrics(registry);
    }

    @Test
    void shouldRecordSourceRequestedAsCounterWithTags() {
        // when
        metrics.sourceRequested("VaultSource", CacheResult.MISS);

        // then
        double count = registry.counter("otto.config.source.requests", "source", "VaultSource", "result", "miss").count();
        assertThat(count, is(1.0));
    }

    @Test
    void shouldRecordSourceLoadedAsTimerWithSuccessOutcome() {
        // when
        metrics.sourceLoaded("VaultSource", Duration.ofMillis(10), false);

        // then
        long count = registry.timer("otto.config.source.loads", "source", "VaultSource", "outcome", "success", "exception", "none").count();
        assertThat(count, is(1L));
    }

    @Test
    void shouldRecordSourceLoadedEmptyAsTimerWithEmptyOutcome() {
        // when
        metrics.sourceLoaded("VaultSource", Duration.ofMillis(10), true);

        // then
        long count = registry.timer("otto.config.source.loads", "source", "VaultSource", "outcome", "empty", "exception", "none").count();
        assertThat(count, is(1L));
    }

    @Test
    void shouldRecordSourceLoadFailedWithExceptionTag() {
        // when
        metrics.sourceLoadFailed("VaultSource", Duration.ofMillis(10), new IllegalStateException("boom"));

        // then
        long count = registry.timer("otto.config.source.loads", "source", "VaultSource", "outcome", "failure", "exception", "IllegalStateException").count();
        assertThat(count, is(1L));
    }

    @Test
    void shouldRecordHttpRequestWithStatusAndNoneWhenMissing() {
        // when
        metrics.httpRequest("VaultClient", "GET", 200, Duration.ofMillis(15));
        metrics.httpRequest("VaultClient", "GET", -1, Duration.ofMillis(20));

        // then
        long successCount = registry.timer("otto.config.http.requests", "client", "VaultClient", "method", "GET", "status", "200").count();
        long failureCount = registry.timer("otto.config.http.requests", "client", "VaultClient", "method", "GET", "status", "none").count();
        assertThat(successCount, is(1L));
        assertThat(failureCount, is(1L));
    }

    @Test
    void shouldRecordRefreshWithTypeTag() {
        // when
        metrics.refresh(RefreshType.FULL, Duration.ofMillis(30));
        metrics.refresh(RefreshType.POLL, Duration.ofMillis(5));

        // then
        long fullCount = registry.timer("otto.config.refresh", "type", "full").count();
        long pollCount = registry.timer("otto.config.refresh", "type", "poll").count();
        assertThat(fullCount, is(1L));
        assertThat(pollCount, is(1L));
    }

    @Test
    void shouldRecordChangeEventsReceivedAsCounter() {
        // when
        metrics.changeEventsReceived(3);
        metrics.changeEventsReceived(2);

        // then
        double count = registry.counter("otto.config.change.events").count();
        assertThat(count, is(5.0));
    }

    @Test
    void shouldUseGlobalRegistryWhenConstructedWithoutArgs() {
        // given - Metrics.globalRegistry is an empty composite by default; attach a backing registry so recordings are observable
        SimpleMeterRegistry globalBacking = new SimpleMeterRegistry();
        io.micrometer.core.instrument.Metrics.addRegistry(globalBacking);
        try {
            // when
            MicrometerConfigMetrics noArg = new MicrometerConfigMetrics();
            noArg.changeEventsReceived(1);

            // then
            double count = globalBacking.counter("otto.config.change.events").count();
            assertThat(count, is(1.0));
        } finally {
            io.micrometer.core.instrument.Metrics.removeRegistry(globalBacking);
        }
    }
}
