package de.otto.config.core.property;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.registry.PropertyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class RefreshablePropertyTest {

    private Configuration<String> configuration;
    private static final String KEY = "test.key";
    private static final String INITIAL_VALUE = "initial";
    private static final String REFRESHED_VALUE = "refreshed";

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        configuration = mock(Configuration.class);
        when(configuration.getValueByType(KEY, String.class)).thenReturn(INITIAL_VALUE, REFRESHED_VALUE);
    }

    @Test
    void shouldInitializeWithRefreshedValue() {
        // given/when
        RefreshableProperty<String> property = RefreshableProperty.<String>builder()
                .key(KEY)
                .type(String.class)
                .configuration(configuration)
                .build();

        // then
        assertThat(property.getKey(), is(KEY));
        assertThat(property.getType(), is(String.class));
        assertThat(property.getConfiguration(), is(configuration));
        assertThat(property.getValue(), is(INITIAL_VALUE));
        verify(configuration).getValueByType(KEY, String.class);
    }

    @Test
    void shouldRefreshValue() {
        // given
        RefreshableProperty<String> property = RefreshableProperty.<String>builder()
                .key(KEY)
                .type(String.class)
                .configuration(configuration)
                .build();

        // when
        property.refresh();

        // then
        assertThat(property.getValue(), is(REFRESHED_VALUE));
        verify(configuration, times(2)).getValueByType(KEY, String.class);
    }

    @Test
    void shouldRegisterPropertyIfAbsent() {
        // given
        Context context = mock(Context.class);
        PropertyRegistry registry = mock(PropertyRegistry.class);

        when(context.getPropertyRegistry()).thenReturn(registry);
        when(context.getConfiguration()).thenReturn(configuration);

        RefreshableProperty<String> expectedProperty = RefreshableProperty.<String>builder()
                .key(KEY)
                .type(String.class)
                .configuration(configuration)
                .build();

        when(registry.registerIfAbsent(eq(KEY), any()))
                .thenReturn(expectedProperty);

        // when
        RefreshableProperty<String> result = RefreshableProperty.register(context, KEY, String.class);

        // then
        assertThat(result, is(expectedProperty));
        verify(registry).registerIfAbsent(eq(KEY), any());
    }
}
