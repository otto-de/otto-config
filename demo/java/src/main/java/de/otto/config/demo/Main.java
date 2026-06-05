package de.otto.config.demo;

import java.util.Map;

import de.otto.config.core.Context;
import de.otto.config.domain.Experiments.Groups;
import de.otto.config.provider.ConfigurationProvider;
import de.otto.config.provider.ExperimentProvider;
import de.otto.config.source.CoreSourceFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Plain Java demonstration of manual Zealot integration.
 * 
 * This example shows how to integrate Zealot without any framework (Spring, Helidon, etc.):
 * 1. Create a Configuration<String> instance with your config values
 * 2. Create a Context using Context.from(appName, profile, configuration)
 * 3. Build ConfigurationProvider and ExperimentProvider from the Context
 * 4. Use the providers to access configuration values from various sources
 */
@Slf4j
public class Main {
    
    public static void main(String[] args) {
        log.info("Starting plain Java Zealot demo...");
        
        Context context = Context.from("zealot");
        ConfigurationProvider configurationProvider = ConfigurationProvider.builder()
                                                                           .context(context)
                                                                           .source(CoreSourceFactory.createPropertiesSource(context))
                                                                           .source(CoreSourceFactory.createTogglesSource(context))
                                                                           .build();
        ExperimentProvider experimentProvider = ExperimentProvider.builder()
                                                                  .context(context)
                                                                  .source(CoreSourceFactory.createExperimentsSource(context))
                                                                  .build();

        boolean value = configurationProvider.getValueAsBoolean("logging_enabled");
        log.info("Toggle value for logging_enabled: " + value);

        String stringValue = configurationProvider.getValue("myKey1");
        log.info("Property value for myKey1: " + stringValue);

        Map<String, String> properties = configurationProvider.getProperties();
        log.info("All properties: " + properties);

         Map<String, Groups> experiments = experimentProvider.getProperties();
        log.info("Experiments: " + experiments);

        log.info("Groups for search_experiment experiment: " + experiments.get("search_experiment"));
        log.info("Param search.algorithm for group_a group: " + experimentProvider.getProperties().get("search_experiment")
                                                                                                  .getGroups().get("group_a"));  
        log.info("Demo completed.");
    }
}
