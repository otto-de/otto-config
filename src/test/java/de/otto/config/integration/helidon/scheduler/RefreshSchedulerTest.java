package de.otto.config.integration.helidon.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.otto.config.core.Context;

import static org.mockito.Mockito.*;

class RefreshSchedulerTest {
    private Context context = mock(Context.class);
    private RefreshScheduler schedulerConfiguration;

    @BeforeEach
    void setUp() {
        schedulerConfiguration = new RefreshScheduler(context, true);
    }

    @Test
    void shouldCallRefreshOnConfigurationSource() {
        schedulerConfiguration.refresh();
        verify(context, times(1)).refresh();
    }
}