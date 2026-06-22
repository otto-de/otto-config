package de.otto.config.integration.spring.config;

import de.otto.config.core.Context;
import de.otto.config.core.property.Property;
import de.otto.config.core.property.PropertyVersion;
import de.otto.config.core.property.RefreshableProperty;
import de.otto.config.core.property.RefreshablePropertyVersion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Factory for creating Property and PropertyVersion instances programmatically.
 * Useful for @Bean method parameters where @PropertyValue annotation doesn't work.
 * 
 * Example usage:
 * <pre>
 * @Bean
 * public SomeFilter myFilter(PropertyFactory propertyFactory) {
 *     Property&lt;Boolean&gt; emergencyBlock = propertyFactory.createProperty("emergency_block", Boolean.class);
 *     return new SomeFilter(emergencyBlock);
 * }
 * </pre>
 */
@Component
@ConditionalOnClass(ApplicationContext.class)
public class PropertyFactory {

    private final Context context;

    public PropertyFactory(Context context) {
        this.context = context;
    }

    /**
     * Create a Property for the given key and type
     */
    public <T> Property<T> createProperty(String key, Class<T> type) {
        return RefreshableProperty.register(context, key, type);
    }

    /**
     * Create a PropertyVersion for the given key
     */
    public PropertyVersion createPropertyVersion(String key) {
        return RefreshablePropertyVersion.register(context, key);
    }
}
