package de.otto.search;

import de.otto.config.demo.DemoApplication;
import de.otto.config.integration.spring.config.BeanConfiguration;
import de.otto.config.provider.ConfigurationProvider;
import de.otto.config.service.ExperimentService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = DemoApplication.class, properties = "spring.profiles.active=test")
@AutoConfigureWebTestClient
@EnableAutoConfiguration
@ActiveProfiles({"integration-test", "test"})
@Import(BeanConfiguration.class)
@Disabled
public class DemoIntegrationTest {

    @Autowired
    private ExperimentService experimentService;
    @Autowired
    private ConfigurationProvider configurationProvider;


    @Test
    public void shouldReturnExperiments(){
        // given
        // when
        Set<String> allExperimentNames = experimentService.getAllExperimentNames();
        // then
        assertThat(allExperimentNames.size()).isNotEqualTo(0);
    }

    @Test
    public void shouldReturnExceptionIfPropertyIsNotPresent() {
        // given
        assertThrows(NoSuchElementException.class, () -> {
            // when
            configurationProvider.getValue("thisProperty");
        });
    }

    @Test
    public void shouldReturnValueIfPropertyIsPresent() {
        // given
        // when
        String actualValue = configurationProvider.getValue("myKey2");
        // then
        assertThat(actualValue).isEqualTo("myValue1;myValue2");
    }

    @Test
    public void shouldReturnFalseIfToggleIsNotPresent() {
        // given
        // when
        boolean actualToggle = configurationProvider.getValueAsBoolean("thisToggle");
        // then
        assertThat(actualToggle).isFalse();
    }

    @Test
    public void shouldReturnTrueIfToggleIsPresent () {
        // given
        // when
        boolean actualToggle = configurationProvider.getValueAsBoolean("ftsn-415-test-toggle");
        // then
        assertThat(actualToggle).isTrue();
    }
}
