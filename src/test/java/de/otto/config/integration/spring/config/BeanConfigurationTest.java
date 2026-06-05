package de.otto.config.integration.spring.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.otto.config.core.Context;
import de.otto.config.integration.spring.fixtures.MockBeans;
import de.otto.config.provider.ConfigurationProvider;
import de.otto.config.provider.ExperimentProvider;
import de.otto.config.service.ExperimentService;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

class BeanConfigurationTest {
    
    @Nested
    @SpringBootTest(classes = {MockBeans.class, BeanConfiguration.class}, properties = {"spring.profiles.active=test"})
    class WithSpringContextAndBeansPropertyEnabled {

        @Autowired
        private Context context;

        @Autowired
        private ConfigurationProvider configurationProvider;

        @Autowired
        private ExperimentProvider experimentProvider;

        @Autowired
        private ExperimentService experimentService;

        @Test
        void shouldCreateBeanWhenSpringContextIsAvailableAndBeansPropertyEnabled() {
            assertNotNull(context);
            assertNotNull(configurationProvider);
            assertNotNull(configurationProvider.getValue("ftsn-415-test-toggle"));
            assertEquals("myValue", configurationProvider.getValue("myKey1"));
            assertNotNull(experimentProvider);
            assertNotNull(experimentProvider.getProperties().get("search_experiment"));
            assertNotNull(experimentService);
            assertNotNull(experimentService.getExperiments().get("search_experiment"));
        }
    }

    @Nested
    @SpringBootTest(classes = NoBeansConfiguration.class)
    class WithoutSpringContext {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void shouldNotCreateBeansWhenSpringContextIsNotAvailable() {
            assertThrows(NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(ConfigurationProvider.class));
            assertThrows(NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(ExperimentService.class));
        }
    }

    @Configuration
    static class NoBeansConfiguration {
        // No beans defined here, simulating the absence of Spring context
    }
}
