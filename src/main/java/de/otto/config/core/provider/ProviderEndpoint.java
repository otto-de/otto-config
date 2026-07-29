package de.otto.config.core.provider;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.otto.config.core.Context;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class ProviderEndpoint<T extends Provider<?>> {
    private final Context context;
    private final String endpointName;
    private final Class<T> type;

    protected abstract T createProvider(String appName);

    protected Optional<T> getProvider(String appName) {
        return this.context.getProviderRegistry().getValues().stream()
                .filter(type::isInstance)
                .filter(p -> p.getContext().getAppName().equals(appName)
                        && p.getContext().getExcludeSecrets())
                .map(type::cast)
                .findFirst();
    }

    protected void registerProviders() {
        Set<String> appNames = getAppNames();
        for (String appName : appNames) {
            boolean alreadyRegistered = this.context.getProviderRegistry().getValues().stream()
                .anyMatch(p -> this.type.isInstance(p) 
                        && p.getContext().getAppName().equals(appName)
                        && p.getContext().getExcludeSecrets());
            if (!alreadyRegistered) {
                T provider = createProvider(appName);
                this.context.getProviderRegistry().register(provider);
            }
        }
    }

    @SuppressWarnings("null")
    protected Set<String> getAppNames() {
        String configApps = context.getConfiguration().getValue("otto.config.endpoint." + endpointName + ".apps", "");

        Set<String> appNames = Arrays.stream(configApps.split(","))
                                     .map(String::trim)
                                     .filter(s -> !s.isEmpty())
                                     .collect(Collectors.toSet());
        appNames.add(this.context.getAppName()); // Always include the current app

        return appNames;
    }
}
