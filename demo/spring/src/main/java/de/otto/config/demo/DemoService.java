package de.otto.config.demo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.otto.config.core.property.Property;
import de.otto.config.core.property.PropertyValue;
import de.otto.config.core.property.PropertyVersion;
import de.otto.config.provider.ConfigurationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class DemoService {

    private final ConfigurationProvider configurationProvider;
    private final Environment environment;

    @Value("${logging.enabled}")
    private boolean loggingEnabled;

    @PropertyValue("logging.enabled")
    private Property<Boolean> loggingEnabledProperty;

    @PropertyValue("s3_toggle1")
    private Property<Boolean> s3Toggle1;

    @PropertyValue("myKey1")
    private Property<String> myKey1;

    @PropertyValue("my.url")
    private Property<String> myUrl;

    @Value("${auth.client.id}")
    private List<String> authClientIds;

    @PropertyValue("auth.client.id")
    private PropertyVersion authClientIdVersion;

    @Scheduled(fixedDelay = 30000) // Run every 30 seconds
    public void load() {
        boolean value = configurationProvider.getValueAsBoolean("logging_enabled");
        log.info("Toggle value for logging_enabled: " + value);
        log.info("S3 feature toggle 's3_toggle1' resolved to " + (Boolean.TRUE.equals(s3Toggle1.getValue()) ? "ENABLED (serving new code path)" : "DISABLED (serving default code path)"));
        log.info("S3 feature toggle 's3_toggle2' resolved to " + (configurationProvider.getValueAsBoolean("s3_toggle2") ? "ENABLED (serving new code path)" : "DISABLED (serving default code path)"));
        log.info("Environment value for logging.enabled: " + this.environment.getProperty("logging.enabled", Boolean.class));
        log.info("@Value value for logging.enabled: " + this.loggingEnabled);
        log.info("@PropertyValue value for logging.enabled: " + loggingEnabledProperty);

        String stringValue = configurationProvider.getValue("myKey1");
        log.info("Property value for myKey1: " + stringValue);
        log.info("Environment value for myKey1: " + this.environment.getProperty("myKey1"));
        log.info("@Value value for myKey1: " + this.myKey1);
        log.info("@PropertyValue value for my.url: " + this.myUrl);
        log.info("Environment value for my.url: " + this.environment.getProperty("my.url"));
        List<String> values = Arrays.asList(this.environment.getProperty("auth.client.id", String[].class));
        log.info("Environment value for auth.client.id as list: " + values);
        log.info("@Value value for auth.client.id: " + this.authClientIds);
        log.info("Injected PropertyVersion for auth.client.id: " + this.authClientIdVersion.getVersions());

        Map<String, String> properties = configurationProvider.getProperties();
        log.info("All properties: " + properties);
    }
}
