package de.otto.config.core.source;

import com.fasterxml.jackson.core.type.TypeReference;
import de.otto.config.core.Configuration;
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

    @BeforeEach
    void setUp() {
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
    }
}
