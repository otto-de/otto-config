package de.otto.config.integration.spring.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.otto.config.core.property.Property;
import de.otto.config.core.property.PropertyValue;
import de.otto.config.integration.spring.fixtures.MockBeans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tests for @PropertyValue field injection via PropertyValueConfiguration.
 */
@SpringBootTest(classes = {
    MockBeans.class,
    BeanConfiguration.class,
    PropertyValueConfiguration.class,
    PropertyValueConfigurationTest.TestConfiguration.class
}, properties = {"spring.profiles.active=test"})
class PropertyValueConfigurationTest {

    @Autowired
    private TestComponent testComponent;

    @Test
    void shouldInjectBooleanPropertyIntoField() {
        assertNotNull(testComponent);
        assertNotNull(testComponent.getBooleanProperty());
        
        Property<Boolean> property = testComponent.getBooleanProperty();
        assertNotNull(property.getValue());
    }

    @Test
    void shouldInjectStringPropertyIntoField() {
        assertNotNull(testComponent);
        assertNotNull(testComponent.getStringProperty());
        
        Property<String> property = testComponent.getStringProperty();
        assertEquals("myValue", property.getValue());
    }

    // Test configuration
    @Configuration
    static class TestConfiguration {
        @Bean
        TestComponent testComponent() {
            return new TestComponent();
        }
    }

    // Test component class
    static class TestComponent {
        @PropertyValue("ftsn-415-test-toggle")
        private Property<Boolean> booleanProperty;

        @PropertyValue("myKey1")
        private Property<String> stringProperty;

        public Property<Boolean> getBooleanProperty() {
            return booleanProperty;
        }

        public Property<String> getStringProperty() {
            return stringProperty;
        }
    }
}
