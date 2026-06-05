package de.otto.config.core.property;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.registry.PropertyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

public class RefreshablePropertyVersionTest {

    private Configuration<String> configuration;
    private final String key = "test.key";
    private List<String> initialVersions;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        configuration = mock(Configuration.class);
        initialVersions = Arrays.asList("v1", "v2");
    }

    @Test
    void shouldInitializeWithValuesFromConfiguration() {
        // given
        when(configuration.getValues(eq(key), eq(String.class))).thenReturn(initialVersions);

        // when
        RefreshablePropertyVersion version = RefreshablePropertyVersion.builder()
                .key(key)
                .configuration(configuration)
                .build();

        // then
        assertThat(version.getKey(), is(key));
        assertThat(version.getConfiguration(), is(configuration));
        assertThat(version.getVersions(), contains("v1", "v2"));
    }

    @Test
    void shouldRefreshValues() {
        // given
        when(configuration.getValues(eq(key), eq(String.class))).thenReturn(initialVersions);

        RefreshablePropertyVersion version = RefreshablePropertyVersion.builder()
                .key(key)
                .configuration(configuration)
                .build();

        List<String> newVersions = Arrays.asList("v3", "v4");
        when(configuration.getValues(eq(key), eq(String.class))).thenReturn(newVersions);

        // when
        version.refresh();

        // then
        assertThat(version.getVersions(), contains("v3", "v4"));
    }

    @Test
    void shouldClearAndAddAllOnRefreshIfValueNotNull() {
        // given
        when(configuration.getValues(eq(key), eq(String.class))).thenReturn(initialVersions);

        RefreshablePropertyVersion version = RefreshablePropertyVersion.builder()
                .key(key)
                .configuration(configuration)
                .build();

        List<String> newVersions = Collections.singletonList("v5");
        when(configuration.getValues(eq(key), eq(String.class))).thenReturn(newVersions);

        // when
        version.refresh();

        // then
        assertThat(version.getVersions(), contains("v5"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRegisterWithContext() {
        // given
        Context context = mock(Context.class);
        PropertyRegistry registry = mock(PropertyRegistry.class);
        Configuration<String> config = mock(Configuration.class);

        when(context.getPropertyRegistry()).thenReturn(registry);
        when(context.getConfiguration()).thenReturn(config);

        RefreshablePropertyVersion expected = mock(RefreshablePropertyVersion.class);

        when(registry.registerIfAbsent(eq(key), any()))
                .thenReturn(expected);

        // when
        RefreshablePropertyVersion result = RefreshablePropertyVersion.register(context, key);

        // then
        assertThat(result, is(expected));
        verify(registry).registerIfAbsent(eq(key), any());
    }
}