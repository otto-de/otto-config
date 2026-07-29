package de.otto.config.source;

import de.otto.config.core.source.SourceException;
import de.otto.config.domain.Properties;

import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class PropertySourceTest {

    // Minimal concrete subclass for testing
    static class TestPropertySource extends PropertySource {

        @Override
        public Properties load() throws SourceException {
            return Properties.empty;
        }
 
    }

    @Test
    void shouldReturnTypeReference() {
        // given
        PropertySource source = new TestPropertySource();

        // when
        TypeReference<Properties> ref = source.getTypeReference();

        // then
        assertThat(ref, is(Properties.typeReference));
    }

    @Test
    void shouldReturnEmptyValue() {
        // given
        PropertySource source = new TestPropertySource();

        // when
        Properties empty = source.getEmptyValue();

        // then
        assertThat(empty, is(Properties.empty));
    }

    @Test
    void shouldMergeAsListValuesWhenKeyExists() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> src = new HashMap<>();
        Map<String, String> dest = new HashMap<>();
        src.put("foo", "bar");
        dest.put("foo", "baz");

        // when
        source.mergeAsListValues(src, dest);

        // then
        assertThat(dest.get("foo"), is("baz,bar"));
    }

    @Test
    void shouldMergeAsListValuesWhenKeyDoesNotExist() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> src = new HashMap<>();
        Map<String, String> dest = new HashMap<>();
        src.put("foo", "bar");

        // when
        source.mergeAsListValues(src, dest);

        // then
        assertThat(dest.get("foo"), is("bar"));
    }

    @Test
    void shouldMergeMultipleKeys() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> src = new HashMap<>();
        Map<String, String> dest = new HashMap<>();
        src.put("a", "1");
        src.put("b", "2");
        dest.put("a", "x");
        dest.put("c", "3");

        // when
        source.mergeAsListValues(src, dest);

        // then
        assertThat(dest.get("a"), is("x,1"));
        assertThat(dest.get("b"), is("2"));
        assertThat(dest.get("c"), is("3"));
    }

    @SuppressWarnings("null")
    @Test
    void shouldMergeAsListValuesWithKeyFormatterWhenKeyExists() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> src = new HashMap<>();
        Map<String, String> dest = new HashMap<>();
        src.put("foo", "bar");
        dest.put("FOO", "baz");

        // when
        source.mergeAsListValues(src, dest, String::toUpperCase);

        // then
        assertThat(dest.get("FOO"), is("baz,bar"));
    }

    @SuppressWarnings("null")
    @Test
    void shouldMergeAsListValuesWithKeyFormatterWhenKeyDoesNotExist() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> src = new HashMap<>();
        Map<String, String> dest = new HashMap<>();
        src.put("foo", "bar");

        // when
        source.mergeAsListValues(src, dest, String::toUpperCase);

        // then
        assertThat(dest.get("FOO"), is("bar"));
    }

    @SuppressWarnings("null")
    @Test
    void shouldMergeMultipleKeysWithKeyFormatter() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> src = new HashMap<>();
        Map<String, String> dest = new HashMap<>();
        src.put("a", "1");
        src.put("b", "2");
        dest.put("A", "x");
        dest.put("C", "3");

        // when
        source.mergeAsListValues(src, dest, String::toUpperCase);

        // then
        assertThat(dest.get("A"), is("x,1"));
        assertThat(dest.get("B"), is("2"));
        assertThat(dest.get("C"), is("3"));
    }

    @SuppressWarnings("null")
    @Test
    void shouldHandleEmptySourceWithKeyFormatter() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> src = new HashMap<>();
        Map<String, String> dest = new HashMap<>();
        dest.put("A", "x");

        // when
        source.mergeAsListValues(src, dest, String::toUpperCase);

        // then
        assertThat(dest.get("A"), is("x"));
        assertThat(dest.size(), is(1));
    }

    @Test
    void shouldHandleEmptyDestinationWithKeyFormatter() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> src = new HashMap<>();
        Map<String, String> dest = new HashMap<>();
        src.put("foo", "bar");

        // when
        source.mergeAsListValues(src, dest, k -> k + "_suffix");

        // then
        assertThat(dest.get("foo_suffix"), is("bar"));
        assertThat(dest.size(), is(1));
    }

    @Test
    void shouldMergeEntriesAsListValuesWithSingleEntry() {
        // given
        PropertySource source = new TestPropertySource();
        Map.Entry<String, String> entry = Map.entry("foo", "bar");

        // when
        Map<String, String> result = source.mergeEntriesAsListValues(Stream.of(entry), Function.identity());

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get("foo"), is("bar"));
    }

    @Test
    void shouldMergeEntriesAsListValuesWithMultipleEntriesSameKey() {
        // given
        PropertySource source = new TestPropertySource();
        Map.Entry<String, String> entry1 = Map.entry("foo", "bar");
        Map.Entry<String, String> entry2 = Map.entry("foo", "baz");

        // when
        Map<String, String> result = source.mergeEntriesAsListValues(Stream.of(entry1, entry2), Function.identity());

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get("foo"), is("bar,baz"));
    }

    @Test
    void shouldMergeEntriesAsListValuesWithMultipleEntriesDifferentKeys() {
        // given
        PropertySource source = new TestPropertySource();
        Map.Entry<String, String> entry1 = Map.entry("foo", "bar");
        Map.Entry<String, String> entry2 = Map.entry("baz", "qux");

        // when
        Map<String, String> result = source.mergeEntriesAsListValues(Stream.of(entry1, entry2), Function.identity());

        // then
        assertThat(result.size(), is(2));
        assertThat(result.get("foo"), is("bar"));
        assertThat(result.get("baz"), is("qux"));
    }

    @Test
    void shouldMergeEntriesAsListValuesWithKeyFormatter() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> map = Map.of("foo_suffix1", "bar1", "foo_suffix2", "bar2");

        // when
        Map<String, String> result = source.mergeEntriesAsListValues(
                map.entrySet().stream(),
                key -> key.replaceAll("_(suffix1|suffix2)$", "")
        );

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get("foo"), is("bar1,bar2"));
    }

    @Test
    void shouldMergeEntriesAsListValuesWithKeyFormatterInOrder() {
        // given
        PropertySource source = new TestPropertySource();
        Map<String, String> map = new LinkedHashMap<>(Map.of("foo_suffix2", "bar2", "foo_suffix1", "bar1"));

        // when
        Map<String, String> result = source.mergeEntriesAsListValues(
                map.entrySet().stream(),
                key -> key.replaceAll("_(suffix1|suffix2)$", "")
        );

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get("foo"), is("bar1,bar2"));
    }

    @Test
    void shouldReturnEmptyMapWhenNoEntries() {
        // given
        PropertySource source = new TestPropertySource();

        // when
        Map<String, String> result = source.mergeEntriesAsListValues(Stream.<Map.Entry<String, String>>empty(), Function.identity());

        // then
        assertThat(result.isEmpty(), is(true));
    }
}
