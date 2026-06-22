package de.otto.config.integration.spring.config;

import de.otto.config.core.Context;
import de.otto.config.core.property.Property;
import de.otto.config.core.property.PropertyValue;
import de.otto.config.core.property.PropertyVersion;
import de.otto.config.core.property.RefreshableProperty;
import de.otto.config.core.property.RefreshablePropertyVersion;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Enables @PropertyValue annotation support for method and constructor parameters.
 * This integrates with Spring's dependency resolution to handle @PropertyValue in @Bean methods and constructors.
 */
@Component
@ConditionalOnClass(ApplicationContext.class)
public class PropertyValueParameterResolver implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof DefaultListableBeanFactory) {
            DefaultListableBeanFactory defaultBeanFactory = (DefaultListableBeanFactory) beanFactory;
            
            // Replace the autowire candidate resolver with our custom one
            // Don't get Context yet - it will be resolved lazily when needed
            defaultBeanFactory.setAutowireCandidateResolver(
                new PropertyValueAutowireCandidateResolver(defaultBeanFactory)
            );
        }
    }

    /**
     * Custom autowire candidate resolver that handles @PropertyValue annotations
     */
    private static class PropertyValueAutowireCandidateResolver extends ContextAnnotationAutowireCandidateResolver {
        
        private final ConfigurableListableBeanFactory beanFactory;
        private Context context;

        public PropertyValueAutowireCandidateResolver(ConfigurableListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        private Context getContext() {
            if (this.context == null) {
                this.context = beanFactory.getBean(Context.class);
            }
            return this.context;
        }

        @Override
        public Object getSuggestedValue(DependencyDescriptor descriptor) {
            // First check for @PropertyValue annotation
            PropertyValue annotation = descriptor.getAnnotation(PropertyValue.class);
            if (annotation != null) {
                MethodParameter methodParameter = descriptor.getMethodParameter();
                if (methodParameter != null) {
                    Class<?> parameterType = methodParameter.getParameterType();

                    // Create and return the appropriate Property bean
                    try {
                        Context ctx = getContext();
                        if (PropertyVersion.class.isAssignableFrom(parameterType)) {
                            return RefreshablePropertyVersion.register(ctx, annotation.value());
                        } else if (Property.class.isAssignableFrom(parameterType)) {
                            Class<?> genericType = getGenericTypeFromParameter(methodParameter);
                            return RefreshableProperty.register(ctx, annotation.value(), genericType);
                        }
                    } catch (Exception e) {
                        // Context not available yet, return null and let normal resolution happen
                        return null;
                    }
                }
            }

            // Fall back to parent implementation
            return super.getSuggestedValue(descriptor);
        }

        @Override
        public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
            // Check if this is a @PropertyValue - if so, don't create lazy proxy
            PropertyValue annotation = descriptor.getAnnotation(PropertyValue.class);
            if (annotation != null) {
                return null;
            }
            return super.getLazyResolutionProxyIfNecessary(descriptor, beanName);
        }

        private Class<?> getGenericTypeFromParameter(MethodParameter parameter) {
            Type genericType = parameter.getGenericParameterType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length == 1 && typeArgs[0] instanceof Class) {
                    return (Class<?>) typeArgs[0];
                }
            }
            return String.class; // Default to String if we can't determine type
        }
    }
}
