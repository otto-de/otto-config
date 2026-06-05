package de.otto.config.source;

import de.otto.config.core.source.SourceChangeEvent;
import de.otto.config.domain.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

class CombinedPropertySourceTest {

    @Test
    void shouldMergePropertiesFromAllSources() throws Exception {
        // given
        PropertySource first = mock(PropertySource.class);
        when(first.getOrLoad(Mockito.anyBoolean())).thenReturn(new Properties(Map.of("key1", "value1", "key2", "first")));

        PropertySource second = mock(PropertySource.class);
        when(second.getOrLoad(Mockito.anyBoolean())).thenReturn(new Properties(Map.of("key2", "second", "key3", "value3")));

        CombinedPropertySource combined = CombinedPropertySource.builder()
                .sources(List.of(first, second))
                .build();

        // when
        Properties result = combined.load();

        // then – second source's values win for overlapping keys
        assertThat(result.getProperties(), aMapWithSize(3));
        assertThat(result.getProperties(), hasEntry("key1", "value1"));
        assertThat(result.getProperties(), hasEntry("key2", "second"));
        assertThat(result.getProperties(), hasEntry("key3", "value3"));
    }

    @Test
    void shouldReturnTrueFromOnChangedWhenAnySourceMatches() {
        // given
        SourceChangeEvent event = mock(SourceChangeEvent.class);

        PropertySource nonMatching = mock(PropertySource.class);
        when(nonMatching.onChanged(event)).thenReturn(false);

        PropertySource matching = mock(PropertySource.class);
        when(matching.onChanged(event)).thenReturn(true);

        CombinedPropertySource combined = CombinedPropertySource.builder()
                .sources(List.of(nonMatching, matching))
                .build();

        // when / then
        assertThat(combined.onChanged(event), is(true));
    }

    @Test
    void shouldReturnFalseFromOnChangedWhenNoSourceMatches() {
        // given
        SourceChangeEvent event = mock(SourceChangeEvent.class);

        PropertySource sourceA = mock(PropertySource.class);
        when(sourceA.onChanged(event)).thenReturn(false);

        PropertySource sourceB = mock(PropertySource.class);
        when(sourceB.onChanged(event)).thenReturn(false);

        CombinedPropertySource combined = CombinedPropertySource.builder()
                .sources(List.of(sourceA, sourceB))
                .build();

        // when / then
        assertThat(combined.onChanged(event), is(false));
    }

    @Test
    void shouldReturnEmptyPropertiesWhenAllSourcesAreEmpty() throws Exception {
        // given
        PropertySource source = mock(PropertySource.class);
        when(source.getOrLoad(Mockito.anyBoolean())).thenReturn(Properties.empty);

        CombinedPropertySource combined = CombinedPropertySource.builder()
                .sources(List.of(source))
                .build();

        // when
        Properties result = combined.load();

        // then
        assertThat(result.getProperties(), anEmptyMap());
    }
}
