package de.otto.config.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import de.otto.config.domain.Experiments.Configs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static de.otto.config.domain.Experiments.Configs.of;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

class ConfigsTest {

    @Test
    public void shouldFlattenListOfConfigs() {
        // given
        Configs configs1 = of(Map.of("param1", List.of("value1"), "param2", List.of("value2")));
        Configs configs2 = of(Map.of("param3", List.of("value3"), "param4", List.of("value4")));

        List<Configs> configs = List.of(configs1, configs2);

        Configs expected = of(Map.of("param1", List.of("value1"), "param2", List.of("value2"),
                                                  "param3", List.of("value3"), "param4", List.of("value4")));

        // when
        Configs actual = Configs.flatten(configs);

        // then
        assertEquals(expected, actual);
    }

    @Test
    public void shouldKeepFirstWhenDuplicateKeys() {
        // given
        Configs configs1 = of(Map.of("duplicateKey", List.of("firstValue"), "param2", List.of("value2")));
        Configs configs2 = of(Map.of("duplicateKey", List.of("secondValue")));
        Configs configs3 = of(Map.of("duplicateKey", List.of("thirdValue")));

        List<Configs> configs = List.of(configs1, configs2, configs3);

        Configs expected = of(Map.of("duplicateKey", List.of("firstValue"), "param2", List.of("value2")));

        // when
        Configs actual = Configs.flatten(configs);

        // then
        assertEquals(expected, actual);
    }

    @Test
    public void shouldMergeCorrectly() {
        // given
        Configs configs1 = of(Map.of("param1", List.of("value1"), "param2", List.of("value2")));
        Configs configs2 = of(Map.of("param3", List.of("value3"), "param4", List.of("value4")));


        Configs expected = of(Map.of("param1", List.of("value1"), "param2", List.of("value2"),
                                                  "param3", List.of("value3"), "param4", List.of("value4")));

        // when
        Configs actual = configs1.mergeWith(configs2);

        // then
        assertEquals(expected, actual);
    }

    @Test
    public void shouldMergeDuplicateKeysCorrectly() {
        // given
        Configs configs1 = of(Map.of("duplicateKey", List.of("firstValue"), "param2", List.of("value2")));
        Configs configs2 = of(Map.of("duplicateKey", List.of("secondValue")));

        Configs expected = of(Map.of("duplicateKey", List.of("firstValue"), "param2", List.of("value2")));

        // when
        Configs actual = configs1.mergeWith(configs2);

        // then
        assertEquals(expected, actual);
    }

    @Test
    public void shouldCreateConfigsFromMultimap() {
        // given
        Multimap<String, String> multimap = ArrayListMultimap.create();
        multimap.put("key1", "value1");
        multimap.put("key1", "value2");
        multimap.put("key2", "value3");

        Configs expected = of(Map.of("key1", List.of("value1", "value2"), "key2", List.of("value3")));

        // when
        Configs actual = Configs.of(multimap);

        // then
        assertEquals(expected, actual);
    }

    @Test
    public void shouldHandleEmptyMultimap() {
        // given
        Multimap<String, String> multimap = ArrayListMultimap.create();

        Configs expected = of(Map.of());

        // when
        Configs actual = Configs.of(multimap);

        // then
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> debugConfigsProvider() {
        return Stream.of(
                Arguments.of(of(Map.of("key", List.of("value"))), false),
                Arguments.of(of(Map.of("debug", List.of("value"))), false),
                Arguments.of(of(Map.of("debug", List.of("on"))), true),
                Arguments.of(of(Map.of("debug", List.of("true"))), true),
                Arguments.of(of(Map.of("debug", List.of("tRuE"))), true),
                Arguments.of(of(Map.of("Debug", List.of("true"))), false)
        );
    }

    @ParameterizedTest
    @MethodSource("debugConfigsProvider")
    public void shouldSetDebugCorrectly(Configs configs, boolean expected) {
        // given
        // when
        boolean actual = configs.isDebug();

        // then
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> configsProvider() {
        HashMap<String, List<String>> nullValueMap = new HashMap<>();
        nullValueMap.put("key", null);

        return Stream.of(
                Arguments.of(of(Map.of("otherKey", List.of("value"))), emptyList()),
                Arguments.of(of(nullValueMap), emptyList()),
                Arguments.of(of(Map.of("key", List.of("value"))), List.of("value")),
                Arguments.of(of(Map.of("key", List.of("value1,value2"))), List.of("value1", "value2")),
                Arguments.of(of(Map.of("key", List.of("value1", "value2"))), List.of("value1", "value2"))
        );
    }

    @ParameterizedTest
    @MethodSource("configsProvider")
    public void shouldGetParamsCorrectly(Configs configs, List<String> expected) {
        // given
        // when
        List<String> actual = configs.getParam("key");

        // then
        assertIterableEquals(expected, actual);
    }
}

