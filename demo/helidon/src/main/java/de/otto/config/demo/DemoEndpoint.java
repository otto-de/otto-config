package de.otto.config.demo;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.otto.config.core.property.PropertyValue;
import de.otto.config.core.property.RefreshablePropertyVersion;
import de.otto.config.provider.ConfigurationProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("/demo")
@ApplicationScoped
public class DemoEndpoint{

    private final ConfigurationProvider configurationProvider;
    private final Config config;
    private final String loggingEnabled;
    private final String myKey1;
    private final RefreshablePropertyVersion authClientIdVersion;

    @Inject
    public DemoEndpoint(ConfigurationProvider configurationProvider, 
                        Config config, 
                        @ConfigProperty(name = "logging.enabled") String loggingEnabled,
                        @ConfigProperty(name = "myKey1") String myKey1,
                        @PropertyValue("auth.client.id") RefreshablePropertyVersion authClientIdVersions) {
        this.configurationProvider = configurationProvider;
        this.config = config;
        this.loggingEnabled = loggingEnabled;
        this.myKey1 = myKey1;
        this.authClientIdVersion = authClientIdVersions;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
        String value = configurationProvider.getValue("myKey1");
        StringBuilder sb = new StringBuilder();
        sb.append("DemoEndpoint loaded with logging.enabled from Config: ")
          .append(this.config.getConfigValue("logging.enabled")).append("\n");
        sb.append("DemoEndpoint loaded with logging.enabled from @ConfigProperty: ")
          .append(this.loggingEnabled).append("\n");
        sb.append("DemoEndpoint loaded with myKey1 from Config: ")
          .append(this.config.getConfigValue("myKey1")).append("\n");
        sb.append("DemoEndpoint loaded with myKey1 from @ConfigProperty: ")
          .append(this.myKey1).append("\n");
        sb.append("DemoEndpoint loaded with myKey1 from configurationProvider: ").append(value);
        sb.append("DemoEndpoint loaded with auth.client.id from @PropertyValue: ").append(this.authClientIdVersion);
        return sb.toString();
    }
}
