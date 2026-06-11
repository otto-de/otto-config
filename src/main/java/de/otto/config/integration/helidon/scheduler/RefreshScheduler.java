package de.otto.config.integration.helidon.scheduler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.otto.config.core.Context;
import io.helidon.scheduling.Scheduling.FixedRate;

@Slf4j
@ApplicationScoped
public class RefreshScheduler {
    private final Context context;
    private final boolean enabled;

    @Inject
    public RefreshScheduler(Context context, @ConfigProperty(name = "otto.config.refresh.enabled", defaultValue = "true") boolean enabled) {
        this.context = context;
        this.enabled = enabled;
        if (enabled) {
            log.info("Starting Otto Config scheduler");
        } else {
            log.info("Otto Config scheduler is disabled");
        }
    }

    @FixedRate(value = "PT5M")
    public void refresh() {
        if (enabled) {
            log.debug("Refreshing Otto Config configurations");
            this.context.refresh();
        }
    }

    @FixedRate(value = "PT10S")
    public void pollAndRefresh() {
        if (enabled) {
            log.debug("Polling and refreshing Otto Config configurations");
            this.context.pollAndRefresh();
        }
    }
}
