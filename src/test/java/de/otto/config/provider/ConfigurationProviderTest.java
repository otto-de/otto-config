package de.otto.config.provider;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import de.otto.config.core.ConfigurationCache;
import de.otto.config.core.Context;
import de.otto.config.core.source.Source;
import de.otto.config.domain.Properties;
import de.otto.config.domain.Toggles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Map;

public class ConfigurationProviderTest {
    private Source<Toggles> toggleSource;
    private Source<Properties> propertySource;
    private ConfigurationProvider configurationProvider;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        toggleSource = Mockito.mock(Source.class);
        when(toggleSource.getOrLoad(Mockito.anyBoolean())).thenReturn(new Toggles(Map.of(
            "toggle1", Map.of("enabled", true),
            "toggle2", Map.of("enabled", false),
            "featureX", Map.of("enabled", true)
        )));
        when(toggleSource.getTypeReference()).thenReturn(Toggles.typeReference);

        propertySource = Mockito.mock(Source.class);
        when(propertySource.getOrLoad(Mockito.anyBoolean())).thenReturn(new Properties(Map.of(
            "key1", "value1",
            "key2", "42",
            "/some/ssm/key", "ssm_value"
        )));
        when(propertySource.getTypeReference()).thenReturn(Properties.typeReference);

        ConfigurationCache<String> configuration = new ConfigurationCache<>(Map.of(
                    "otto.config.sources.enabled", "aws.appconfig.properties,aws.appconfig.toggles,aws.appconfig.experiments,aws.secrets,aws.ssm",
                    "otto.config.aws.secrets.arn", "arn:aws:secretsmanager:123"
            ));
        Context context = Context.builder().configuration(configuration).build();
        context.getSourceRegistry().getValues().clear();
        context.getSourceRegistry().register(toggleSource);
        context.getSourceRegistry().register(propertySource);
        configurationProvider = ConfigurationProvider.builder().context(context).build();
    }

    @Test
    public void testGetValue() {
        assertEquals("value1", configurationProvider.getValue("key1"));
    }

    @Test
    public void testGetValueWithOverrides() {
        Multimap<String, String> overrides = ArrayListMultimap.create();
        overrides.put("key1", "hello");
        assertEquals("hello", configurationProvider.withOverrides(overrides).getValue("key1"));
    }

    @Test
    public void testGetValueAsBoolean() {
        assertTrue(configurationProvider.getValueAsBoolean("featureX"));
    }

    @Test
    public void testGetValueAsBooleanWithOverrides() {
        Multimap<String, String> overrides = ArrayListMultimap.create();
        overrides.put("featureX", "false");
        assertEquals(false, configurationProvider.withOverrides(overrides).getValueAsBoolean("featureX"));
    }

    @Test
    public void testGetValueAsInt() {
        assertEquals(42, configurationProvider.getValueAsInt("key2"));
    }

    @Test
    public void testGetValueAsIntWithOverrides() {
        Multimap<String, String> overrides = ArrayListMultimap.create();
        overrides.put("key2", "45");
        assertEquals(45, configurationProvider.withOverrides(overrides).getValueAsInt("key2"));
    }

    @Test
    public void testGetValues() {
        // given
        
        // when
        Map<String, String> values = configurationProvider.getProperties();

        // then
        assertEquals(7, values.size());
        assertEquals("value1", values.get("key1"));
        assertEquals("42", values.get("key2"));
        assertEquals("true", values.get("toggle1"));
        assertEquals("false", values.get("toggle2"));
        assertEquals("true", values.get("featureX"));
        assertEquals("ssm_value", values.get("/some/ssm/key"));
        assertEquals("ssm_value", values.get("some.ssm.key"));
    }

    @Test
    public void testGetValueNames() {
        // given

        // when
        var propertyNames = configurationProvider.getPropertyNames();

        // then
        assertEquals(7, propertyNames.size());
        assertTrue(propertyNames.contains("key1"));
        assertTrue(propertyNames.contains("key2"));
        assertTrue(propertyNames.contains("toggle1"));
        assertTrue(propertyNames.contains("toggle2"));
        assertTrue(propertyNames.contains("featureX"));
        assertTrue(propertyNames.contains("/some/ssm/key"));
        assertTrue(propertyNames.contains("some.ssm.key"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testAddSourceUpdatesValues() {
        // given
        Source<Properties> extraSource = Mockito.mock(Source.class);
        when(extraSource.getOrLoad(Mockito.anyBoolean())).thenReturn(new Properties(Map.of(
            "extraKey", "extraValue"
        )));
        when(extraSource.getTypeReference()).thenReturn(Properties.typeReference);

        // when
        configurationProvider.addSource(extraSource);

        // then
        assertEquals("extraValue", configurationProvider.getValue("extraKey"));
    }
}