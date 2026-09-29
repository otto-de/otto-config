package de.otto.config.integration.spring.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.otto.config.core.Context;

@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnClass(ApplicationContext.class)
@ConditionalOnProperty(name = "otto.config.refresh.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RefreshScheduler implements InitializingBean {
    private final Context context;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Starting Otto Config scheduler");
    }

    @Scheduled(initialDelayString = "${otto.config.refresh.interval:PT5M}",
               fixedDelayString = "${otto.config.refresh.interval:PT5M}")
    public void refresh() {
        log.debug("Refreshing Otto Config configurations");
        this.context.refresh();
    }

    @Scheduled(initialDelayString = "${otto.config.refresh.poll.interval:PT10S}",
               fixedDelayString = "${otto.config.refresh.poll.interval:PT10S}")
    public void pollAndRefresh() {
        log.debug("Polling and refreshing Otto Config configurations");
        this.context.pollAndRefresh();
    }
}
