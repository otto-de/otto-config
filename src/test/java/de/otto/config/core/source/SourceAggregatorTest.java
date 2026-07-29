package de.otto.config.core.source;

import de.otto.config.core.Configuration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

public class SourceAggregatorTest {

    @Test
    void shouldAggregateWithoutNormalization() {
        // given
        Source<Configuration<String>> source1 = mockSource(Map.of("a_b", "1"));
        Source<Configuration<String>> source2 = mockSource(Map.of("c/d", "2"));

        // when
        Map<String, String> result = SourceAggregator.aggregate(
                List.of(source1, source2),
                v -> (String) v,
                false
        );

        // then
        assertThat(result, aMapWithSize(2));
        assertThat(result, hasEntry("a_b", "1"));
        assertThat(result, hasEntry("c/d", "2"));
        assertThat(result.keySet(), not(hasItem("a.b")));
        assertThat(result.keySet(), not(hasItem("c.d")));
    }

    @Test
    void shouldAggregateWithNormalization() {
        // given
        Source<Configuration<String>> source = mockSource(Map.of("foo_bar/baz", "v"));

        // when
        Map<String, String> result = SourceAggregator.aggregate(
                List.of(source),
                v -> (String) v,
                true
        );

        // then
        assertThat(result, aMapWithSize(2));
        assertThat(result, hasEntry("foo_bar/baz", "v"));
        assertThat(result, hasEntry("foo.bar.baz", "v"));
    }

    @Test
    void shouldAggregateWithNormalizationExcludeSecrets() {
        // given
        Source<Configuration<String>> source = mockSource(Map.of("foo_bar/baz", "abcdefghijklmnop"));
        when(source.hasSecrets()).thenReturn(true);

        // when
        Map<String, String> result = SourceAggregator.aggregate(
                List.of(source),
                v -> (String) v,
                true,
                true,
                true
        );

        // then
        assertThat(result, aMapWithSize(0));
    }

    @Test
    void shouldNotDuplicateIfNormalizedKeyEqualsOriginal() {
        // given
        Source<Configuration<String>> source = mockSource(Map.of("foo.bar", "v"));

        // when
        Map<String, String> result = SourceAggregator.aggregate(
                List.of(source),
                v -> (String) v,
                true
        );

        // then
        assertThat(result, aMapWithSize(1));
        assertThat(result, hasEntry("foo.bar", "v"));
    }

    @Test
    void shouldTransformValuesUsingValueTransformer() {
        // given
        Source<Configuration<Integer>> source = mockSource(Map.of("num", 42));

        // when
        Map<String, String> result = SourceAggregator.aggregate(
                List.of(source),
                v -> "val:" + v,
                false
        );

        // then
        assertThat(result, aMapWithSize(1));
        assertThat(result, hasEntry("num", "val:42"));
    }

    @Test
    void shouldRemoveLeadingDotsWhenNormalizing() {
        // given
        Source<Configuration<String>> source = mockSource(Map.of("...foo_bar", "v"));

        // when
        Map<String, String> result = SourceAggregator.aggregate(
                List.of(source),
                v -> (String) v,
                true
        );

        // then
        assertThat(result, hasEntry("...foo_bar", "v"));
        assertThat(result, hasEntry("foo.bar", "v"));
    }

    @Test
    void shouldKeepFirstValueOnKeyOverlap() {
        // given
        Source<Configuration<String>> source1 = mockSource(Map.of("dup", "first"));
        Source<Configuration<String>> source2 = mockSource(Map.of("dup", "second"));

        // when
        Map<String, String> result = SourceAggregator.aggregate(
                List.of(source1, source2),
                v -> (String) v,
                false
        );

        // then
        assertThat(result, aMapWithSize(1));
        assertThat(result, hasEntry("dup", "first"));
    }

    @SuppressWarnings("unchecked")
    private <T> Source<Configuration<T>> mockSource(Map<String, T> properties) {
        Source<Configuration<T>> source = mock(Source.class);
        Configuration<T> config = mock(Configuration.class);
        when(config.getProperties()).thenReturn(properties);
        when(source.getOrLoad()).thenReturn(config);
        when(source.getOrLoad(anyBoolean())).thenReturn(config);
        return source;
    }
}
