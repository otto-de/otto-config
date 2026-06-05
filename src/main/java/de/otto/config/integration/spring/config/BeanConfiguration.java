package de.otto.config.integration.spring.config;

import de.otto.config.core.Context;
import de.otto.config.integration.spring.env.PropertySource;
import de.otto.config.provider.ConfigurationProvider;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

@Slf4j
@Configuration
@ConditionalOnClass(ApplicationContext.class)
public class BeanConfiguration {

    @Bean
    @ConditionalOnMissingBean(Context.class)
    public Context context(PropertySource propertySource) {
        return propertySource.getContext();
    }

    @Bean
    @ConditionalOnMissingBean(ConfigurationProvider.class)
    public ConfigurationProvider configurationProvider(PropertySource propertySource) {
        return propertySource.getConfigurationProvider();
    }

    @Bean
    @ConditionalOnMissingBean(PropertySource.class)
    public PropertySource propertySource(ConfigurableEnvironment environment) {
        return environment.getPropertySources()
                          .stream()
                          .filter(ps -> ps.getName().equals("zealot"))
                          .findFirst()
                          .map(ps -> (PropertySource) ps)
                          .orElseGet(() -> new PropertySource("zealot", environment));
    }
}
