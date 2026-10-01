package de.otto.config.core.source;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import de.otto.config.core.Configuration;
import de.otto.config.core.metrics.ConfigMetrics;
import de.otto.config.core.metrics.ConfigMetrics.CacheResult;
import de.otto.config.core.metrics.ConfigMetricsRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class SourceTest {

    static class TestConfig implements Configuration<TestConfig> {
        private final boolean empty;

        TestConfig(boolean empty) {
            this.empty = empty;
        }

        @Override
        public boolean isEmpty() {
            return empty;
        }
    }

    Source<TestConfig> source;
    TestConfig loadedConfig;
    TestConfig emptyConfig;
    List<String> recordedEvents;

    @AfterEach
    void tearDown() {
        ConfigMetricsRegistry.reset();
    }

    @BeforeEach
    void setUp() {
        recordedEvents = new ArrayList<>();
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void sourceRequested(String source, CacheResult result) {
                recordedEvents.add("requested:" + source + ":" + result);
            }

            @Override
            public void sourceLoaded(String source, Duration duration, boolean empty) {
                recordedEvents.add("loaded:" + source + ":" + empty);
            }

            @Override
            public void sourceLoadFailed(String source, Duration duration, Throwable error) {
                recordedEvents.add("failed:" + source + ":" + error.getClass().getSimpleName());
            }
        });

        loadedConfig = spy(new TestConfig(false));
        emptyConfig = spy(new TestConfig(true));
        source = Mockito.spy(new Source<TestConfig>() {
            @Override
            public TypeReference<TestConfig> getTypeReference() {
                return new TypeReference<TestConfig>() {};
            }

            @Override
            public TestConfig load() {
                return loadedConfig;
            }

            @Override
            public TestConfig getEmptyValue() {
                return emptyConfig;
            }
        });
    }

    @Test
    void shouldReturnEmptyValueIfLoadedIsNull() throws Exception {
        // given
        doReturn(null).when(source).load();

        // when
        TestConfig result = source.getOrLoad();

        // then
        assertThat(result, is(emptyConfig));
        verify(source, times(1)).getEmptyValue();
    }

    @Test
    void shouldReturnEmptyValueIfLoadedIsEmptyAndCacheIsNull() throws Exception {
        // given
        doReturn(new TestConfig(true)).when(source).load();

        // when
        TestConfig result = source.getOrLoad();

        // then
        assertThat(result, is(emptyConfig));
        verify(source, times(1)).getEmptyValue();
    }

    @Test
    void shouldNotOverwriteCacheIfLoadedIsEmptyAndCacheIsNotNull() throws Exception {
        // given
        source.getOrLoad();

        doReturn(new TestConfig(true)).when(source).load();

        // when
        TestConfig result = source.getOrLoad();

        // then
        assertThat(result, is(loadedConfig));
        verify(source, never()).getEmptyValue();
    }

    @Test
    void shouldHandleSourceExceptionAndReturnCache() throws Exception {
        // given
        doThrow(new SourceException("fail")).when(source).load();

        // when
        TestConfig result = source.getOrLoad();

        // then
        assertThat(result, is(emptyConfig));
        assertThat(recordedEvents, hasItem(containsString("failed:")));
        assertThat(recordedEvents, hasItem(containsString("SourceException")));
    }

    @Test
    void shouldRecordMissOnFirstLoadAndEmptyOnLoadedEmpty() throws Exception {
        // given
        doReturn(new TestConfig(true)).when(source).load();

        // when
        source.getOrLoad(false);

        // then
        assertThat(recordedEvents.get(0), is("requested::MISS"));
        assertThat(recordedEvents.get(1), is("loaded::true"));
    }

    @Test
    void shouldRecordSuccessOnLoadedNonEmpty() throws Exception {
        // when
        source.getOrLoad(false);

        // then
        assertThat(recordedEvents.get(0), is("requested::MISS"));
        assertThat(recordedEvents.get(1), is("loaded::false"));
    }

    @Test
    void shouldRecordHitWhenCacheIsAlreadyPopulatedAndNotForced() throws Exception {
        // given
        source.getOrLoad(false);
        recordedEvents.clear();

        // when
        source.getOrLoad(false);

        // then
        assertThat(recordedEvents, hasItem("requested::HIT"));
        verify(source, times(1)).load();
    }

    @Test
    void shouldRecordRefreshEvenWhenCacheIsPopulated() throws Exception {
        // given
        source.getOrLoad(false);
        recordedEvents.clear();

        // when
        source.getOrLoad(true);

        // then
        assertThat(recordedEvents.get(0), is("requested::REFRESH"));
        verify(source, times(2)).load();
    }

    @Test
    void shouldRecordFailureAndRethrowOnRuntimeException() throws Exception {
        // given
        doThrow(new IllegalStateException("boom")).when(source).load();

        // when / then
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> source.getOrLoad(false));
        assertThat(recordedEvents, hasItem(containsString("failed::IllegalStateException")));
    }
}
