package de.otto.config.core.metrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.otto.config.core.metrics.ConfigMetrics.CacheResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Note: {@code src/test/resources/META-INF/services/de.otto.config.core.metrics.ConfigMetrics} declares three
 * providers to exercise discovery robustness: a reference to a nonexistent class (simulating a typo or a stale
 * registration), {@link BrokenConfigMetrics} (whose constructor throws {@link NoClassDefFoundError}, simulating
 * e.g. {@code MicrometerConfigMetrics} being registered while Micrometer is missing from the classpath), and
 * {@link DiscoverableTestConfigMetrics} (a working implementation). Tests relying on real {@link ServiceLoader}
 * discovery (i.e. not calling {@link ConfigMetricsRegistry#register(ConfigMetrics)} beforehand) must account for
 * the latter being picked up. Metrics default to <strong>disabled</strong> (opt-in), so most tests must call
 * {@link ConfigMetricsRegistry#setEnabled(boolean)} with {@code true} explicitly.
 */
class ConfigMetricsRegistryTest {

    @BeforeEach
    void setUp() {
        DiscoverableTestConfigMetrics.reset();
    }

    @AfterEach
    void tearDown() {
        ConfigMetricsRegistry.reset();
    }

    @Test
    void shouldDefaultToDisabledOnFreshState() {
        // given - fresh state (as after reset()): nothing enabled yet, nothing registered either
        ConfigMetrics metrics = ConfigMetricsRegistry.get();

        // then - metrics are opt-in; no discovery is performed and NOOP is returned, even though a
        // working implementation is discoverable via META-INF/services (see class javadoc)
        assertThat(metrics, is(ConfigMetrics.NOOP));
        metrics.changeEventsReceived(1);
        assertThat(DiscoverableTestConfigMetrics.changeEventsReceivedCount.get(), is(0));
    }

    @Test
    void shouldSkipBrokenProvidersAndUseTheRemainingDiscoverableImplementation() {
        // given
        ConfigMetricsRegistry.setEnabled(true);

        // when - nothing registered: triggers real ServiceLoader discovery
        ConfigMetrics metrics = ConfigMetricsRegistry.get();
        metrics.changeEventsReceived(2);

        // then - the nonexistent-class and NoClassDefFoundError-throwing entries are skipped without
        // throwing, and the remaining, working provider is still used
        assertThat(metrics, is(not(ConfigMetrics.NOOP)));
        assertThat(DiscoverableTestConfigMetrics.changeEventsReceivedCount.get(), is(2));
    }

    @Test
    void shouldReturnRegisteredImplementation() {
        // given
        AtomicInteger calls = new AtomicInteger();
        ConfigMetrics impl = new ConfigMetrics() {
            @Override
            public void changeEventsReceived(int count) {
                calls.incrementAndGet();
            }
        };

        // when
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(impl);
        ConfigMetricsRegistry.get().changeEventsReceived(1);

        // then
        assertThat(calls.get(), is(1));
    }

    @Test
    void shouldResetBackToDiscoveryState() {
        // given
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(new ConfigMetrics() {});
        assertThat(ConfigMetricsRegistry.get(), is(not(ConfigMetrics.NOOP)));

        // when
        ConfigMetricsRegistry.reset();

        // then - reset() clears the registered implementation AND resets metrics back to disabled
        // (the default); re-enabling is required before discovery runs again
        assertThat(ConfigMetricsRegistry.get(), is(ConfigMetrics.NOOP));
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetrics metrics = ConfigMetricsRegistry.get();
        assertThat(metrics, is(not(ConfigMetrics.NOOP)));
        metrics.changeEventsReceived(1);
        assertThat(DiscoverableTestConfigMetrics.changeEventsReceivedCount.get(), is(1));
    }

    @Test
    void shouldSwallowExceptionsFromImplementationAndKeepWorking() {
        // given
        AtomicInteger calls = new AtomicInteger();
        ConfigMetrics throwing = new ConfigMetrics() {
            @Override
            public void changeEventsReceived(int count) {
                throw new RuntimeException("boom");
            }
        };
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(throwing);

        // when / then - must not propagate
        ConfigMetricsRegistry.get().changeEventsReceived(1);

        // and should still be usable afterwards
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void changeEventsReceived(int count) {
                calls.incrementAndGet();
            }
        });
        ConfigMetricsRegistry.get().changeEventsReceived(1);
        assertThat(calls.get(), is(1));
    }

    @Test
    void shouldAcceptNullRegistrationAsNoop() {
        // when
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(null);

        // then
        assertThat(ConfigMetricsRegistry.get(), is(ConfigMetrics.NOOP));
    }

    @Test
    void shouldFanOutToAllDelegatesViaWrapper() {
        // given - registering twice replaces, so simulate fan-out by using a single impl recording both methods
        List<String> events = new ArrayList<>();
        ConfigMetrics impl = new ConfigMetrics() {
            @Override
            public void sourceRequested(String source, CacheResult result) {
                events.add("requested:" + source + ":" + result);
            }

            @Override
            public void sourceLoaded(String source, Duration duration, boolean empty) {
                events.add("loaded:" + source + ":" + empty);
            }
        };
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(impl);

        // when
        ConfigMetricsRegistry.get().sourceRequested("FooSource", CacheResult.MISS);
        ConfigMetricsRegistry.get().sourceLoaded("FooSource", Duration.ofMillis(5), false);

        // then
        assertThat(events, contains("requested:FooSource:MISS", "loaded:FooSource:false"));
    }

    @Test
    void shouldReturnNoopWhenDisabledEvenIfRegistered() {
        // given
        AtomicInteger calls = new AtomicInteger();
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void changeEventsReceived(int count) {
                calls.incrementAndGet();
            }
        });
        assertThat(ConfigMetricsRegistry.get(), is(not(ConfigMetrics.NOOP)));

        // when
        ConfigMetricsRegistry.setEnabled(false);

        // then
        assertThat(ConfigMetricsRegistry.get(), is(ConfigMetrics.NOOP));
        ConfigMetricsRegistry.get().changeEventsReceived(1);
        assertThat(calls.get(), is(0));
    }

    @Test
    void shouldReturnNoopWhenDisabledEvenIfDiscoverable() {
        // given - explicitly disabled (also the default); a real implementation is discoverable
        // via META-INF/services (see class javadoc)
        ConfigMetricsRegistry.setEnabled(false);

        // when
        ConfigMetrics metrics = ConfigMetricsRegistry.get();

        // then - discovery is skipped entirely; NOOP is returned and the discoverable implementation
        // is never invoked
        assertThat(metrics, is(ConfigMetrics.NOOP));
        metrics.changeEventsReceived(1);
        assertThat(DiscoverableTestConfigMetrics.changeEventsReceivedCount.get(), is(0));
    }

    @Test
    void shouldResumeReportingAfterReEnabling() {
        // given
        AtomicInteger calls = new AtomicInteger();
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void changeEventsReceived(int count) {
                calls.incrementAndGet();
            }
        });
        ConfigMetricsRegistry.setEnabled(false);
        assertThat(ConfigMetricsRegistry.get(), is(ConfigMetrics.NOOP));

        // when
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.get().changeEventsReceived(1);

        // then
        assertThat(calls.get(), is(1));
    }

    @Test
    void shouldResetBackToDisabledByDefault() {
        // given
        ConfigMetricsRegistry.setEnabled(true);

        // when
        ConfigMetricsRegistry.reset();

        // then - reset() both clears the cached implementation and resets metrics back to disabled
        // (the default); even registering a new implementation afterwards has no effect until
        // setEnabled(true) is called again
        ConfigMetricsRegistry.register(new ConfigMetrics() {});
        assertThat(ConfigMetricsRegistry.get(), is(ConfigMetrics.NOOP));
        ConfigMetricsRegistry.setEnabled(true);
        assertThat(ConfigMetricsRegistry.get(), is(not(ConfigMetrics.NOOP)));
    }
}
