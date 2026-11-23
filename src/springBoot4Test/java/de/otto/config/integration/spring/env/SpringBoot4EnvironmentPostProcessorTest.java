package de.otto.config.integration.spring.env;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

import de.otto.config.provider.ConfigurationProvider;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SpringBoot4EnvironmentPostProcessorTest {

    @Test
    void shouldBeRegisteredForSpringBoot4() {
        // when
        // Spring Boot's own post processors need constructor arguments and are skipped
        List<org.springframework.boot.EnvironmentPostProcessor> postProcessors = SpringFactoriesLoader.forDefaultResourceLocation()
                                                                                                    .load(org.springframework.boot.EnvironmentPostProcessor.class,
                                                                                                          null,
                                                                                                          (type, name, failure) -> {});

        // then
        assertThat(postProcessors, hasItem(instanceOf(SpringBoot4EnvironmentPostProcessor.class)));
    }

    @Test
    void shouldAddPropertySourceOnlyOnceWhenBothPostProcessorsRun() {
        // given
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("otto.config.sources.enabled", "aws.ssm")));
        new SpringBoot4EnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        Object propertySource = environment.getPropertySources().get(PropertySource.NAME);

        // when
        new EnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        // then
        assertThat(countOttoConfigPropertySources(environment), is(1L));
        assertThat(environment.getPropertySources().get(PropertySource.NAME), is(sameInstance(propertySource)));
        assertThat(environment.getProperty("my.ssm.property1"), is("ssm-value1"));
    }

    @Test
    void shouldProvideConfigurationToSpringBoot4Application() {
        // when
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.NONE)
                                                                                                         .profiles("test")
                                                                                                         .properties("otto.config.sources.enabled=aws.ssm")
                                                                                                         .run()) {
            ConfigurableEnvironment environment = context.getEnvironment();
            PropertySource propertySource = (PropertySource) environment.getPropertySources().get(PropertySource.NAME);

            // then
            assertThat(countOttoConfigPropertySources(environment), is(1L));
            assertThat(environment.getProperty("my.ssm.property1"), is("ssm-value1"));
            assertThat(context.getBean(ConfigurationProvider.class), is(sameInstance(propertySource.getConfigurationProvider())));
        }
    }

    private long countOttoConfigPropertySources(ConfigurableEnvironment environment) {
        return environment.getPropertySources()
                          .stream()
                          .filter(source -> source.getName().equals(PropertySource.NAME))
                          .count();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
