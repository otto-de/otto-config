package de.otto.config.demo;

import java.util.Map;

import de.otto.config.core.Context;
import de.otto.config.provider.ConfigurationProvider;
import de.otto.config.source.CoreSourceFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Plain Java demonstration of manual Otto Config integration.
 * 
 * This example shows how to integrate Otto Config without any framework (Spring, Helidon, etc.):
 * 1. Create a Configuration<String> instance with your config values
 * 2. Create a Context using Context.from(appName, profile, configuration)
 * 3. Build ConfigurationProvider from the Context
 * 4. Use the providers to access configuration values from various sources
 */
@Slf4j
public class Main {
    
    public static void main(String[] args) {
        log.info("Starting plain Java Otto Config demo...");
        
        Context context = Context.from("otto-config");
        ConfigurationProvider configurationProvider = ConfigurationProvider.builder()
                                                                           .context(context)
                                                                           .source(CoreSourceFactory.createPropertiesSource(context))
                                                                           .source(CoreSourceFactory.createTogglesSource(context))
                                                                           .build();

        boolean value = configurationProvider.getValueAsBoolean("logging_enabled");
        log.info("Toggle value for logging_enabled: " + value);

        String stringValue = configurationProvider.getValue("myKey1");
        log.info("Property value for myKey1: " + stringValue);

        Map<String, String> properties = configurationProvider.getProperties();
        log.info("All properties: " + properties);
        
        log.info("Demo completed.");
    }
}
