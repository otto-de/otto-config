package de.otto.config.demo;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.otto.config.core.property.Property;
import de.otto.config.core.property.PropertyValue;
import de.otto.config.core.property.PropertyVersion;
import de.otto.config.domain.Experiments.Groups;
import de.otto.config.provider.ConfigurationProvider;
import de.otto.config.provider.ExperimentProvider;
import io.helidon.scheduling.Scheduling.FixedRate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class DemoService {

    private final ConfigurationProvider configurationProvider;
    private final ExperimentProvider experimentProvider;
    private final Config config;
    private final io.helidon.config.Config helidonConfig;
    private final boolean loggingEnabled;
    private final Property<Boolean> loggingEnabledProperty;
    private final Property<String> myKey1;
    private final Property<String> myUrl;
    private final List<String> authClientIds;
    private final PropertyVersion authClientIdVersion;

    @Inject
    public DemoService(ConfigurationProvider configurationProvider,
                       ExperimentProvider experimentProvider,
                       Config config,
                       io.helidon.config.Config helidonConfig,
                       @ConfigProperty(name = "logging.enabled") String loggingEnabled,
                       @PropertyValue("logging.enabled") Property<Boolean> loggingEnabledProperty,
                       @PropertyValue("myKey1") Property<String> myKey1,
                       @PropertyValue("my.url") Property<String> myUrl,
                       @ConfigProperty(name = "auth.client.id") List<String> authClientIds,
                       @PropertyValue("auth.client.id") PropertyVersion authClientIdVersions) {
        this.configurationProvider = configurationProvider;
        this.experimentProvider = experimentProvider;
        this.config = config;
        this.helidonConfig = helidonConfig;
        this.loggingEnabled = Boolean.parseBoolean(loggingEnabled);
        this.loggingEnabledProperty = loggingEnabledProperty;
        this.myKey1 = myKey1;
        this.myUrl = myUrl;
        this.authClientIds = authClientIds;
        this.authClientIdVersion = authClientIdVersions;
    }

    @FixedRate(value = "PT30S") // Run every 30 seconds
    @Inject
    public void load() {
        boolean value = configurationProvider.getValueAsBoolean("logging_enabled");
        log.info("Toggle value for logging_enabled: " + value);
        log.info("Config value for logging.enabled: " + this.config.getValue("logging.enabled", Boolean.class));
        log.info("HelidonConfig value for logging.enabled: " + this.helidonConfig.get("logging.enabled").asBoolean().get());
        log.info("@ConfigProperty value for logging.enabled: " + this.loggingEnabled);
        log.info("@PropertyValue value for logging.enabled: " + loggingEnabledProperty);

        String stringValue = configurationProvider.getValue("myKey1");
        log.info("Property value for myKey1: " + stringValue);
        log.info("Config value for myKey1: " + this.config.getValue("myKey1", String.class));
        log.info("HelidonConfig value for myKey1: " + this.helidonConfig.get("myKey1").asString().get());
        log.info("@PropertyValue value for myKey1: " + this.myKey1.getValue());
        log.info("Config value for my.url: " + this.config.getValue("my.url", String.class));
        log.info("HelidonConfig value for my.url: " + this.helidonConfig.get("my.url").asString().get());
        log.info("@PropertyValue value for my.url: " + this.myUrl.getValue());
        List<String> values = this.config.getValues("auth.client.id", String.class);
        log.info("Config value for auth.client.id as list: " + values);
        values = this.helidonConfig.get("auth.client.id").asList(String.class).get();
        log.info("HelidonConfig value for auth.client.id as list: " + values);
        log.info("@ConfigProperty value for auth.client.id: " + this.authClientIds);
        log.info("Injected PropertyVersion for auth.client.id: " + this.authClientIdVersion.getVersions());

        Map<String, String> properties = configurationProvider.getProperties();
        log.info("All properties: " + properties);

        Map<String, Groups> experiments = experimentProvider.getProperties();
        log.info("Experiments: " + experiments);

        log.info("Groups for search_experiment experiment: " + experiments.get("search_experiment"));
        log.info("Param search.algorithm for group_a group: " + experimentProvider.getProperties().get("search_experiment")
                                                                                                  .getGroups().get("group_a")
                                                                                                  .getConfigs().get("search.algorithm"));
    }
}
