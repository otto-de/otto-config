package de.otto.config.integration.spring.scheduler;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationContext;

import de.otto.config.core.Context;

import static org.junit.jupiter.api.Assertions.assertNotNull;


public class RefreshSchedulerTest {

    @Nested
    @SpringBootTest(classes = {RefreshScheduler.class})
    class WhenSchedulerEnabled {
        
        @MockitoBean
        private Context context;

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        public void shouldCreateBeanWhenEnabled() {
            assertNotNull(applicationContext.getBean(RefreshScheduler.class));
        }
    }
}