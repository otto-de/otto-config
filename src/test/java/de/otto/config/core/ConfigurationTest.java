package de.otto.config.core;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigurationTest {

    private static class TestConfiguration implements Configuration<String> {
        private final Map<String, String> properties;

        TestConfiguration(Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }
    }

    private Map<String, String> props;
    private Configuration<String> config;

    @BeforeEach
    void setUp() {
        props = new HashMap<>();
        props.put("foo", "bar");
        props.put("intVal", "42");
        props.put("boolVal", "true");
        config = new TestConfiguration(props);
    }

    @Test
    void testIsEmpty() {
        assertThat(config.isEmpty(), is(false));
        Configuration<String> emptyConfig = new TestConfiguration(Collections.emptyMap());
        assertThat(emptyConfig.isEmpty(), is(true));
    }

    @Test
    void testGetValue() {
        assertThat(config.getValue("foo"), is("bar"));
        assertThat(config.getValue("unknown"), is(nullValue()));
    }

    @Test
    void testGetValueWithDefault() {
        assertThat(config.getValue("foo", "default"), is("bar"));
        assertThat(config.getValue("unknown", "default"), is("default"));
    }

    @Test
    void testGetValueAsString() {
        assertThat(config.getValueAsString("foo"), is("bar"));
        assertThat(config.getValueAsString("unknown"), is(nullValue()));
    }

    @Test
    void testGetValueAsStringWithDefault() {
        assertThat(config.getValueAsString("foo", "default"), is("bar"));
        assertThat(config.getValueAsString("unknown", "default"), is("default"));
    }

    @Test
    void testGetValueAsInt() {
        assertThat(config.getValueAsInt("intVal"), is(42));
        assertThat(config.getValueAsInt("unknown"), is(0));
    }

    @Test
    void testGetValueAsIntWithDefault() {
        assertThat(config.getValueAsInt("intVal", 7), is(42));
        assertThat(config.getValueAsInt("unknown", 7), is(7));
    }

    @Test
    void testGetValueAsBoolean() {
        assertThat(config.getValueAsBoolean("boolVal"), is(true));
        assertThat(config.getValueAsBoolean("unknown"), is(false));
    }

    @Test
    void testGetValueAsBooleanWithDefault() {
        assertThat(config.getValueAsBoolean("boolVal", false), is(true));
        assertThat(config.getValueAsBoolean("unknown", true), is(true));
    }

    @Test
    void testContainsKey() {
        assertThat(config.containsKey("foo"), is(true));
        assertThat(config.containsKey("unknown"), is(false));
    }

    @Test
    void testWithOverrides() {
        Multimap<String, String> overrides = ArrayListMultimap.create();
        overrides.put("foo", "overrideBar");
        Configuration<String> overridden = config.withOverrides(overrides);
        assertThat(overridden.getValue("foo"), is("overrideBar"));
        assertThat(overridden.getValue("intVal"), is("42"));
    }

    @Test
    void testConfigurationOverrideFallsBackToOriginal() {
        Multimap<String, String> overrides = ArrayListMultimap.create();
        Configuration<String> overridden = config.withOverrides(overrides);
        assertThat(overridden.getValue("foo"), is("bar"));
    }

    @Test
    void testGetValuesWithTypeString() {
        props.put("strings", "1,2,3");
        List<String> values = config.getValues("strings", String.class);
        assertThat(values, contains("1", "2", "3"));
    }

    @Test
    void testGetValuesWithTypeInteger() {
        props.put("numbers", "1,2,3");
        List<Integer> values = config.getValues("numbers", Integer.class);
        assertThat(values, contains(1, 2, 3));
    }

    @Test
    void testGetValuesWithTypeBoolean() {
        props.put("bools", "true,false,TRUE");
        List<Boolean> values = config.getValues("bools", Boolean.class);
        assertThat(values, contains(true, false, true));
    }

    @Test
    void testGetValuesWithTypeLong() {
        props.put("longs", "10000000000,20000000000");
        List<Long> values = config.getValues("longs", Long.class);
        assertThat(values, contains(10000000000L, 20000000000L));
    }

    @Test
    void testGetValuesWithTypeDouble() {
        props.put("doubles", "1.5,2.5,3.0");
        List<Double> values = config.getValues("doubles", Double.class);
        assertThat(values, contains(1.5, 2.5, 3.0));
    }

    @Test
    void testGetValuesWithSingleValue() {
        props.put("single", "42");
        List<Integer> values = config.getValues("single", Integer.class);
        assertThat(values, contains(42));
    }

    @Test
    void testGetValuesWithEmptyStringAndType() {
        props.put("empty", "");
        List<Integer> values = config.getValues("empty", Integer.class);
        assertThat(values, is(empty()));
    }

    @Test
    void testGetValuesWithNullValueAndType() {
        List<Integer> values = config.getValues("doesNotExist", Integer.class);
        assertThat(values, is(empty()));
    }

    @Test
    void testGetValuesWithUnsupportedTypeThrows() {
        props.put("foo", "bar");
        assertThrows(
            IllegalArgumentException.class,
            () -> config.getValues("foo", Map.class)
        );
    }
}
