package de.otto.config.core.registry;

import de.otto.config.core.Refreshable;
import de.otto.config.core.provider.Provider;
import lombok.Builder;

@Builder
public class ProviderRegistry extends ListRegistry<Provider<?>> implements Refreshable {

    @SuppressWarnings("null")
    @Override
    public void refresh() {
        this.values.forEach(Provider::refresh);
    }

    @SuppressWarnings("null")
    @Override
    public void refreshInPlace() {
        this.values.forEach(Provider::refreshInPlace);
    }
}
