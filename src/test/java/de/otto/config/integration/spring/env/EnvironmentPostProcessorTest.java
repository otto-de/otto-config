package de.otto.config.integration.spring.env;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class EnvironmentPostProcessorTest {

    @Test
    void shouldAddPropertySourceOnlyOnce() {
        // given
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("otto.config.sources.enabled", "aws.ssm")));
        EnvironmentPostProcessor postProcessor = new EnvironmentPostProcessor();
        postProcessor.postProcessEnvironment(environment, new SpringApplication());
        Object propertySource = environment.getPropertySources().get(PropertySource.NAME);

        // when
        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        // then
        assertThat(countOttoConfigPropertySources(environment), is(1L));
        assertThat(environment.getPropertySources().get(PropertySource.NAME), is(sameInstance(propertySource)));
        assertThat(environment.getProperty("my.ssm.property1"), is("ssm-value1"));
    }

    private long countOttoConfigPropertySources(StandardEnvironment environment) {
        return environment.getPropertySources()
                          .stream()
                          .filter(source -> source.getName().equals(PropertySource.NAME))
                          .count();
    }
}
