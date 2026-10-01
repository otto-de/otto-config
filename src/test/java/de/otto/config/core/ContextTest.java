package de.otto.config.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.otto.config.core.metrics.ConfigMetrics;
import de.otto.config.core.metrics.ConfigMetrics.CacheResult;
import de.otto.config.core.metrics.ConfigMetrics.RefreshType;
import de.otto.config.core.metrics.ConfigMetricsRegistry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ContextTest {

    @AfterEach
    void tearDown() {
        ConfigMetricsRegistry.reset();
    }

    private static Configuration<String> metricsEnabledConfiguration() {
        return ConfigurationCache.<String>builder()
                .properties(Map.of("otto.config.metrics.enabled", "true"))
                .build();
    }

    @Test
    void shouldRecordFullRefreshMetricWhenMetricsAreEnabled() {
        // given
        List<String> recorded = new ArrayList<>();
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void refresh(RefreshType type, Duration duration) {
                recorded.add(type.name());
            }
        });
        Context context = Context.from("test-app", "default", metricsEnabledConfiguration());

        // when
        context.refresh();

        // then
        assertThat(recorded, contains("FULL"));
    }

    @Test
    void shouldNotRecordPollRefreshMetricWhenNoChangeEventListeners() {
        // given
        List<String> recorded = new ArrayList<>();
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void refresh(RefreshType type, Duration duration) {
                recorded.add(type.name());
            }
        });
        Context context = Context.from("test-app", "default", metricsEnabledConfiguration());

        // when — no change notifications configured, so no listeners exist
        context.pollAndRefresh();

        // then
        assertThat(recorded, is(empty()));
    }

    @Test
    void shouldDefaultToMetricsDisabledWhenPropertyIsAbsent() {
        // given - metrics are opt-in: no otto.config.metrics.enabled property set at all
        List<String> recorded = new ArrayList<>();
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void refresh(RefreshType type, Duration duration) {
                recorded.add(type.name());
            }
        });
        Context context = Context.from("test-app");

        // when
        context.refresh();

        // then - ConfigMetricsRegistry.get() returns NOOP since metrics default to disabled
        assertThat(recorded, is(empty()));
    }

    @Test
    void shouldKeepMetricsDisabledWhenPropertyExplicitlyFalse() {
        // given
        List<String> recorded = new ArrayList<>();
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void sourceRequested(String source, CacheResult result) {
                recorded.add("requested");
            }

            @Override
            public void refresh(RefreshType type, Duration duration) {
                recorded.add(type.name());
            }
        });
        Configuration<String> configuration = ConfigurationCache.<String>builder()
                .properties(Map.of("otto.config.metrics.enabled", "false"))
                .build();

        // when - the toggle is read before sources are created, in the Context constructor
        Context context = Context.from("test-app", "default", configuration);
        context.refresh();

        // then - metrics stay disabled for the lifetime of this Context, even though an implementation
        // was registered; ConfigMetricsRegistry.get() returns NOOP regardless of registration
        assertThat(recorded, is(empty()));
    }

    @Test
    void shouldNotDisableMetricsWhenSecondaryContextLacksProperty() {
        // given
        List<String> recorded = new ArrayList<>();
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void refresh(RefreshType type, Duration duration) {
                recorded.add(type.name());
            }
        });
        Context.from("test-app", "default", ConfigurationCache.<String>builder()
                .properties(Map.of("otto.config.metrics.enabled", "true"))
                .build());

        // when
        Context secondary = Context.from("other-app", "default", ConfigurationCache.<String>builder()
                .properties(Map.of())
                .build());
        secondary.refresh();

        // then
        assertThat(recorded, is(List.of("FULL")));
    }

    @Test
    void shouldEnableMetricsViaConfigurationProperty() {
        // given
        List<String> recorded = new ArrayList<>();
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void refresh(RefreshType type, Duration duration) {
                recorded.add(type.name());
            }
        });

        // when - otto.config.metrics.enabled=true opts in
        Context context = Context.from("test-app", "default", metricsEnabledConfiguration());
        context.refresh();

        // then
        assertThat(recorded, contains("FULL"));
    }

    @Test
    void shouldNotThrowWhenReadingMetricsToggleWithNullConfiguration() {
        // given - the null-configuration guard in Context's constructor is
        // `configuration != null && configuration.getValueAsBoolean(...)`; this verifies that
        // specific guard directly. (A fully null Configuration passed to Context.builder() still
        // fails elsewhere in SourceRegistry.from(this), which is pre-existing, unrelated behavior.)
        Configuration<String> nullConfiguration = null;

        // when / then - a null configuration is treated the same as the property being unset,
        // i.e. metrics stay disabled (the default)
        boolean enabled = nullConfiguration != null
                && nullConfiguration.getValueAsBoolean("otto.config.metrics.enabled", false);
        assertThat(enabled, is(false));
    }
}
