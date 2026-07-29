package de.otto.config.core.registry;

import de.otto.config.core.Refreshable;
import de.otto.config.core.property.Property;
import lombok.Builder;

@Builder
public class PropertyRegistry extends MapRegistry<String, Property<?>> implements Refreshable {

    @SuppressWarnings("null")
    @Override
    public void refresh() {
        this.values.values().stream()
                .filter(Refreshable.class::isInstance)
                .map(Refreshable.class::cast)
                .forEach(Refreshable::refresh);
    }

    @SuppressWarnings("null")
    @Override
    public void refreshInPlace() {
        this.values.values().stream()
                .filter(Refreshable.class::isInstance)
                .map(Refreshable.class::cast)
                .forEach(Refreshable::refreshInPlace);
    }
}
