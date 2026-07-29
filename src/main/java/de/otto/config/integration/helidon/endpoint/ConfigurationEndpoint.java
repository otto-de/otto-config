package de.otto.config.integration.helidon.endpoint;

import de.otto.config.core.Context;
import de.otto.config.core.provider.ProviderEndpoint;
import de.otto.config.integration.helidon.HelidonContext;
import de.otto.config.provider.ConfigurationProvider;

import org.eclipse.microprofile.config.Config;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@ApplicationScoped
public class ConfigurationEndpoint extends ProviderEndpoint<ConfigurationProvider> {
    private final Config config;

    @Inject
    public ConfigurationEndpoint(Context context, Config config) {
        super(context, "configs", ConfigurationProvider.class);
        this.config = config;
        this.registerProviders();
    }

    @GET
    @Path("/configs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfigValues() {
        return getProvider(getContext().getAppName()).map(provider -> Response.ok(provider.asMap()).build())
                                                     .orElse(Response.status(Response.Status.NOT_FOUND).entity("").build());
    }

    @GET
    @Path("/configs/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getConfigValue(@PathParam("key") String key) {
        return getConfigValueForApp(getContext().getAppName(), key);
    }

    @GET
    @Path("/{app}/configs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfigValuesForApp(@PathParam("app") String app) {
        return getProvider(app).map(provider -> Response.ok(provider.asMap()).build())
                               .orElse(Response.status(Response.Status.NOT_FOUND).entity("").build());
    }

    @GET
    @Path("/{app}/configs/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getConfigValueForApp(@PathParam("app") String app, @PathParam("key") String key) {
        return getProvider(app).map(provider -> Response.ok(provider.getValue(key)).build())
                               .orElse(Response.status(Response.Status.NOT_FOUND).entity("").build());
    }

    @Override
    protected ConfigurationProvider createProvider(String appName) {
        return ConfigurationProvider.builder()
                                    .context(HelidonContext.createContext(appName, true, this.config))
                                    .build();
    }
}
