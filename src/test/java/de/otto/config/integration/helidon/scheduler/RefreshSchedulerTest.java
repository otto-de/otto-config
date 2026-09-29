package de.otto.config.integration.helidon.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import de.otto.config.core.Context;

import static org.mockito.Mockito.*;

class RefreshSchedulerTest {
    private Context context = mock(Context.class);
    private RefreshScheduler schedulerConfiguration;

    @BeforeEach
    void setUp() {
        schedulerConfiguration = new RefreshScheduler(context, true, Duration.ofMinutes(5), Duration.ofSeconds(10));
    }

    @Test
    void shouldCallRefreshOnConfigurationSource() {
        schedulerConfiguration.refresh();
        verify(context, times(1)).refresh();
    }

    @Test
    void shouldNotRefreshWhenDisabled() {
        new RefreshScheduler(context, false, Duration.ofMinutes(5), Duration.ofSeconds(10)).refresh();
        verify(context, never()).refresh();
    }

    @Test
    void shouldScheduleTasksWithConfiguredIntervals() {
        RefreshScheduler scheduler = new RefreshScheduler(context, true, Duration.ofMillis(200), Duration.ofMillis(200));
        try {
            scheduler.onStartup(new Object());
            verify(context, timeout(TimeUnit.SECONDS.toMillis(5)).atLeastOnce()).refresh();
            verify(context, timeout(TimeUnit.SECONDS.toMillis(5)).atLeastOnce()).pollAndRefresh();
        } finally {
            scheduler.onShutdown();
        }
    }

    @Test
    void shouldNotScheduleTasksWhenDisabled() throws InterruptedException {
        RefreshScheduler scheduler = new RefreshScheduler(context, false, Duration.ofMillis(200), Duration.ofMillis(200));
        try {
            scheduler.onStartup(new Object());
            Thread.sleep(500);
            verify(context, never()).refresh();
            verify(context, never()).pollAndRefresh();
        } finally {
            scheduler.onShutdown();
        }
    }
}