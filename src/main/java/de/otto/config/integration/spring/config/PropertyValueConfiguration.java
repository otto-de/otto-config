package de.otto.config.integration.spring.config;

import de.otto.config.core.Context;
import de.otto.config.core.property.Property;
import de.otto.config.core.property.PropertyValue;
import de.otto.config.core.property.PropertyVersion;
import de.otto.config.core.property.RefreshableProperty;
import de.otto.config.core.property.RefreshablePropertyVersion;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@Component
@ConditionalOnClass(ApplicationContext.class)
public class PropertyValueConfiguration implements BeanPostProcessor {

    private final Context context;

    public PropertyValueConfiguration(Context context) {
        this.context = context;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            PropertyValue annotation = field.getAnnotation(PropertyValue.class);
            if (annotation != null) {
                field.setAccessible(true);
                if (PropertyVersion.class.isAssignableFrom(field.getType())) {
                    injectPropertyVersionBean(bean, field, annotation.value());
                } else if (Property.class.isAssignableFrom(field.getType())) {
                    injectPropertyBean(bean, field, annotation.value());
                }
            }
        });
        return bean;
    }

    @PreDestroy
    public void cleanup() {
        context.getPropertyRegistry().clear();
    }

    private void injectPropertyBean(Object bean, Field field, String key) {
        Class<?> type = getTypeFrom(field);
        RefreshableProperty<?> property = RefreshableProperty.register(context, key, type);
        injectBean(bean, field, property);
    }

    private void injectPropertyVersionBean(Object bean, Field field, String key) {
        RefreshablePropertyVersion propertyVersion = RefreshablePropertyVersion.register(context, key);
        injectBean(bean, field, propertyVersion);
    }

    private void injectBean(Object bean, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(bean, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?> getTypeFrom(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length == 1 && typeArgs[0] instanceof Class) {
                return (Class<?>) typeArgs[0];
            }
        }
        return Object.class; // fallback
    }
}
