package de.otto.config.integration.helidon.config;

import java.lang.reflect.ParameterizedType;

import de.otto.config.core.Context;
import de.otto.config.core.property.Property;
import de.otto.config.core.property.PropertyValue;
import de.otto.config.core.property.RefreshableProperty;
import de.otto.config.core.property.RefreshablePropertyVersion;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@ApplicationScoped
public class PropertyValueConfiguration {

    @Inject
    Context context;

    @Produces
    public <T> Property<T> produceProperty(InjectionPoint ip) {
        PropertyValue annotation = ip.getAnnotated().getAnnotation(PropertyValue.class);
        if (annotation == null) {
            throw new IllegalStateException("@PropertyValue annotation is missing");
        }
        
        Class<T> type = getTypeFromInjectionPoint(ip);
        return (Property<T>) RefreshableProperty.register(context, annotation.value(), type);
    }

    @Produces
    public RefreshablePropertyVersion producePropertyVersion(InjectionPoint ip) {
        PropertyValue annotation = ip.getAnnotated().getAnnotation(PropertyValue.class);
        if (annotation == null) {
            throw new IllegalStateException("@PropertyValue annotation is missing");
        }

        return RefreshablePropertyVersion.register(context, annotation.value());
    }

    @PreDestroy
    public void cleanup() {
        context.getPropertyRegistry().clear();
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> getTypeFromInjectionPoint(InjectionPoint ip) {
        ParameterizedType type = (ParameterizedType) ip.getType();
        return (Class<T>) type.getActualTypeArguments()[0];

    }
}
