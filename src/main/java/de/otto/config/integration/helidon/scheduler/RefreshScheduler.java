package de.otto.config.integration.helidon.scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.otto.config.core.Context;
import io.helidon.scheduling.FixedRate;
import io.helidon.scheduling.Task;

@Slf4j
@ApplicationScoped
public class RefreshScheduler {
    private final Context context;
    private final boolean enabled;
    private final Duration refreshInterval;
    private final Duration pollInterval;
    private final List<Task> tasks = new ArrayList<>();

    @Inject
    public RefreshScheduler(Context context,
                            @ConfigProperty(name = "otto.config.refresh.enabled", defaultValue = "true") boolean enabled,
                            @ConfigProperty(name = "otto.config.refresh.interval", defaultValue = "PT5M") Duration refreshInterval,
                            @ConfigProperty(name = "otto.config.refresh.poll.interval", defaultValue = "PT10S") Duration pollInterval) {
        this.context = context;
        this.enabled = enabled;
        this.refreshInterval = refreshInterval;
        this.pollInterval = pollInterval;
    }

    void onStartup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        if (!enabled) {
            log.info("Otto Config scheduler is disabled");
            return;
        }

        log.info("Starting Otto Config scheduler with refresh interval {} and poll interval {}", this.refreshInterval, this.pollInterval);
        this.tasks.add(FixedRate.builder()
                                 .delayBy(this.refreshInterval)
                                 .interval(this.refreshInterval)
                                 .task(invocation -> refresh())
                                 .build());
        this.tasks.add(FixedRate.builder()
                                 .delayBy(this.pollInterval)
                                 .interval(this.pollInterval)
                                 .task(invocation -> pollAndRefresh())
                                 .build());
    }

    @PreDestroy
    void onShutdown() {
        this.tasks.forEach(Task::close);
        this.tasks.clear();
    }

    public void refresh() {
        if (enabled) {
            log.debug("Refreshing Otto Config configurations");
            this.context.refresh();
        }
    }

    public void pollAndRefresh() {
        if (enabled) {
            log.debug("Polling and refreshing Otto Config configurations");
            this.context.pollAndRefresh();
        }
    }
}
