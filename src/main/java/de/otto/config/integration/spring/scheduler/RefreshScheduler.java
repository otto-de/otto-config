package de.otto.config.integration.spring.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

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
        log.info("Starting Zealot scheduler");
    }

    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void refresh() {
        log.debug("Refreshing Zealot configurations");
        this.context.refresh();
    }

    @Scheduled(initialDelay = 10, fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void pollAndRefresh() {
        log.debug("Polling and refreshing Zealot configurations");
        this.context.pollAndRefresh();
    }
}
