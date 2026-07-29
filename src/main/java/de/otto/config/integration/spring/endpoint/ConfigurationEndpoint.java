package de.otto.config.integration.spring.endpoint;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.otto.config.core.Context;
import de.otto.config.core.provider.ProviderEndpoint;
import de.otto.config.integration.spring.SpringContext;
import de.otto.config.provider.ConfigurationProvider;

@RestController
@RequestMapping("/")
@ConditionalOnProperty(name = "otto.config.endpoint.configs.enabled", havingValue = "true", matchIfMissing = false)
public class ConfigurationEndpoint extends ProviderEndpoint<ConfigurationProvider> {
    private final ConfigurableEnvironment environment;

    public ConfigurationEndpoint(Context context, ConfigurableEnvironment environment) {
        super(context, "configs", ConfigurationProvider.class);
        this.environment = environment;
        this.registerProviders();
    }

    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> getConfigValues() {
        return getProvider(getContext().getAppName()).map(provider -> ResponseEntity.ok(provider.asMap()))
                                                     .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/configs/{key}")
    public ResponseEntity<String> getConfigValue(@PathVariable String key) {
        return getConfigValueForApp(getContext().getAppName(), key);
    }

    @GetMapping("/{app}/configs")
    public ResponseEntity<Map<String, Object>> getConfigValuesForApp(@PathVariable String app) {
        return getProvider(app).map(provider -> ResponseEntity.ok(provider.asMap()))
                               .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{app}/configs/{key}")
    public ResponseEntity<String> getConfigValueForApp(@PathVariable String app, @PathVariable String key) {
        return getProvider(app).map(provider -> ResponseEntity.ok(provider.getValue(key)))
                               .orElse(ResponseEntity.notFound().build());
    }

    @Override
    protected ConfigurationProvider createProvider(String appName) {
        return ConfigurationProvider.builder()
                                    .context(SpringContext.createContext(appName, true, this.environment))
                                    .build();
    }
}
