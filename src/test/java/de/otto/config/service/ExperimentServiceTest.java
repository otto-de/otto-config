package de.otto.config.service;

import de.otto.config.domain.Experiments.Configs;
import de.otto.config.domain.Experiments.Groups;
import de.otto.config.provider.ExperimentProvider;
import de.otto.config.source.aws.AppConfigSource;
import de.otto.config.core.Context;
import de.otto.config.core.registry.ProviderRegistry;
import de.otto.config.core.source.SourceException;
import de.otto.config.domain.Experiments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.otto.config.domain.Experiments.Configs.emptyConfigs;
import static de.otto.config.domain.Experiments.Configs.of;
import static de.otto.config.domain.Experiments.Groups.empyGroups;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExperimentServiceTest {

    private AppConfigSource<Experiments> experimentSource;
    private ProviderRegistry providerRegistry;
    private Context context;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        experimentSource = mock(AppConfigSource.class);
        providerRegistry = mock(ProviderRegistry.class);
        context = mock(Context.class);
        when(context.getProviderRegistry()).thenReturn(providerRegistry);
    }

    @Test
    void shouldReturnConfigsFromActiveExperimentsByHeader() throws SourceException {
        // given
        Multimap<String, String> httpHeaders = ArrayListMultimap.create();
        httpHeaders.put("X-Wurst", "Blub1");
        httpHeaders.put("X-ClusterId", "Blub2");
        httpHeaders.put("x-onexv3-exp-e3109", "e3109C");
        httpHeaders.put("x-onexv3-exp-e666", "e666A");

        Configs e3109CConfigs = of(Map.of("param1", List.of("value1"), "param2", List.of("value2")));
        Configs e666AConfigs = of(Map.of("param3", List.of("value3"), "param4", List.of("value4")));

        Map<String, Configs> e3109 = Map.of("e3109C", e3109CConfigs);
        Map<String, Configs> e666 = Map.of("e666A", e666AConfigs);

        Groups e3109Groups = new Groups(e3109);
        Groups e666Groups = new Groups(e666);

        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("E3109", e3109Groups,
                                                          "E666", e666Groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();
        Configs expected = of(Map.of("param1", List.of("value1"), "param2", List.of("value2"),
                                                  "param3", List.of("value3"), "param4", List.of("value4")));

        // when
        Configs actual = experimentService.getParamsFromActiveExperiments(httpHeaders);

        // then
        assertEquals(expected, actual);
    }

    @Test
    void shouldApplyExperiments() throws SourceException {
        // given
        Configs configs = of(Map.of("aKey", List.of("aValue")));
        Map<String, Configs> configsMap = Map.of("testPurpose", configs);
        Groups groups = new Groups(configsMap);
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("eXXX", groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        Multimap<String, String> header = ArrayListMultimap.create();
        header.put("x-onexv3-exp-eXXX", "testPurpose");

        // when
        Configs actual = experimentService.applyActiveExperiments(emptyConfigs,
                                                                  experimentService.getParamsFromActiveExperiments(
                                                                          header));

        // then
        assertTrue(actual.get("aKey").contains("aValue"));
    }

    @Test
    void shouldOverwriteRequestParamsExperiments() throws SourceException {
        // given
        Configs configs = of(Map.of("aKey", List.of("aValue")));
        Map<String, Configs> configsMap = Map.of("testPurpose", configs);
        Groups groups = new Groups(configsMap);
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("eXXX", groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        Map<String, String> headerConfigs = new HashMap<>();
        headerConfigs.put("x-onexv3-exp-eXXX", "testPurpose");

        Multimap<String, String> params = ArrayListMultimap.create();
        params.put("aKey", "sollWeg");

        // when
        Configs modifiedParameter = experimentService.applyActiveExperiments(new Configs(params),
                                                                             experimentService.getParamsFromHeaderConfigs(headerConfigs));

        // then
        assertTrue(modifiedParameter.get("aKey").contains("aValue"));
    }

    @Test
    void shouldFindAllExperimentNames() throws SourceException {
        // given
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("E667", empyGroups,
                                                                 "E668", empyGroups))
                                             .build();
        Set<String> expected = Set.of("E667", "E668");
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        Set<String> actual = experimentService.getAllExperimentNames();

        // then
        assertEquals(expected, actual);
    }

    @Test
    void shouldListActiveExperimentsFromHeader() throws SourceException {
        // given
        Multimap<String, String> onexHeaders = ArrayListMultimap.create();
        onexHeaders.put("x-onexv3-exp-e3109", "e3109C");
        onexHeaders.put("x-onexv3-exp-e666", "e3109C");

        Map<String, Configs> configsMap = Map.of("e3109C", emptyConfigs);
        Groups groups = new Groups(configsMap);
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("e3109", groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        List<String> actual = experimentService.activeExperimentStringsByHeader(onexHeaders);

        // then
        assertEquals(1, actual.size());
        assertEquals("e3109-e3109C", actual.get(0));
    }

    @Test
    void shouldListAllExperimentsFromHeader() {
        // given
        Multimap<String, String> onexHeaders = ArrayListMultimap.create();
        onexHeaders.put("x-onexv3-exp-e3109", "e3109C");
        onexHeaders.put("x-onexv3-exp-e666", "e666C");

        // when
        List<String> actual = ExperimentService.allExperimentStringsByHeader(onexHeaders);

        // then
        assertEquals(2, actual.size());
        assertEquals("e3109-e3109C", actual.get(0));
        assertEquals("e666-e666C", actual.get(1));
    }

    @Test
    void shouldHandleEmptyExperimentsCorrectly() throws SourceException {
        // given
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of())
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        Configs actual = experimentService.getParamsFromActiveExperiments(Map.of());

        // then
        assertEquals(emptyConfigs, actual);
    }

    @Test
    void shouldReturnExperiments() throws SourceException {
        // given
        Groups groups1 = new Groups(Map.of("group1", of(Map.of("param1", List.of("value1")))));
        Groups groups2 = new Groups(Map.of("group2", of(Map.of("param2", List.of("value2")))));
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("experiment1", groups1, "experiment2", groups2))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        Map<String, Groups> actual = experimentService.getExperiments();

        // then
        assertEquals(2, actual.size());
        assertEquals(groups1, actual.get("experiment1"));
        assertEquals(groups2, actual.get("experiment2"));
    }

    @Test
    void parseOnexHeader_multimap_shouldReturnEmptyMapWhenNoMatchingHeaders() {
        // given
        Multimap<String, String> headers = ArrayListMultimap.create();
        headers.put("X-Other-Header", "foo");
        headers.put("x-another-header", "bar");

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void getParamsFromActiveExperiments_map_shouldReturnConfigsForMatchingHeaders() throws SourceException {
        // given
        Map<String, List<String>> httpHeaders = Map.of(
                "x-onexv3-exp-e123", List.of("groupA"),
                "x-onexv3-exp-e456", List.of("groupB")
        );

        Configs e123Configs = of(Map.of("param1", List.of("value1")));
        Configs e456Configs = of(Map.of("param2", List.of("value2")));

        Map<String, Configs> e123 = Map.of("groupA", e123Configs);
        Map<String, Configs> e456 = Map.of("groupB", e456Configs);

        Groups e123Groups = new Groups(e123);
        Groups e456Groups = new Groups(e456);

        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("e123", e123Groups, "e456", e456Groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        Configs expected = of(Map.of("param1", List.of("value1"), "param2", List.of("value2")));

        // when
        Configs actual = experimentService.getParamsFromActiveExperiments(httpHeaders);

        // then
        assertEquals(expected, actual);
    }

    @Test
    void getParamsFromActiveExperiments_map_shouldReturnEmptyConfigsWhenNoMatchingHeaders() throws SourceException {
        // given
        Map<String, List<String>> httpHeaders = Map.of(
                "X-Other-Header", List.of("foo")
        );
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of())
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        Configs actual = experimentService.getParamsFromActiveExperiments(httpHeaders);

        // then
        assertEquals(emptyConfigs, actual);
    }

    @Test
    void getParamsFromActiveExperiments_map_shouldJoinMultipleHeaderValuesWithPipe() throws SourceException {
        // given
        Map<String, List<String>> httpHeaders = Map.of(
                "x-onexv3-exp-e123", List.of("groupA", "groupB")
        );
        Map<String, Configs> e123 = Map.of(
                "groupA|groupB", of(Map.of("paramA", List.of("valueA"), "paramB", List.of("valueB")))
        );
        Groups e123Groups = new Groups(e123);
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("e123", e123Groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        Configs expected = of(Map.of("paramA", List.of("valueA"), "paramB", List.of("valueB")));

        // when
        Configs actual = experimentService.getParamsFromActiveExperiments(httpHeaders);

        // then
        assertEquals(expected, actual);
    }

    @Test
    void getParamsFromActiveExperiments_map_shouldBeCaseSensitiveForHeaderNames() throws SourceException {
        // given
        Map<String, List<String>> httpHeaders = Map.of(
                "X-ONEXV3-EXP-E123", List.of("groupA")
        );
        Configs groupAConfigs = of(Map.of("paramA", List.of("valueA")));
        Map<String, Configs> e123 = Map.of("groupA", groupAConfigs);
        Groups e123Groups = new Groups(e123);
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("e123", e123Groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        Configs actual = experimentService.getParamsFromActiveExperiments(httpHeaders);

        // then
        assertEquals(emptyConfigs, actual);
    }

    @Test
    void getParamsFromActiveExperiments_map_shouldHandlePipeValues() throws SourceException {
        // given
        Map<String, List<String>> httpHeaders = Map.of(
                "x-onexv3-exp-e123", List.of("groupA|exclude")
        );
        Configs groupAConfigs = of(Map.of("paramA", List.of("valueA")));
        Map<String, Configs> e123 = Map.of("groupA", groupAConfigs);
        Groups e123Groups = new Groups(e123);
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("e123", e123Groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();
        Configs expected = of(Map.of("paramA", List.of("valueA")));

        // when
        Configs actual = experimentService.getParamsFromActiveExperiments(httpHeaders);

        // then
        assertEquals(expected, actual);
    }


    @Test
    void parseOnexHeader_multimap_shouldReturnSingleMatchingHeader() {
        // given
        Multimap<String, String> headers = ArrayListMultimap.create();
        headers.put("x-onexv3-exp-e123", "groupA");

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertEquals(1, result.size());
        assertEquals("groupA", result.get("x-onexv3-exp-e123"));
    }

    @Test
    void parseOnexHeader_multimap_shouldReturnMultipleMatchingHeaders() {
        // given
        Multimap<String, String> headers = ArrayListMultimap.create();
        headers.put("x-onexv3-exp-e123", "groupA");
        headers.put("x-onexv3-exp-e456", "groupB");

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertEquals(2, result.size());
        assertEquals("groupA", result.get("x-onexv3-exp-e123"));
        assertEquals("groupB", result.get("x-onexv3-exp-e456"));
    }

    @Test
    void parseOnexHeader_multimap_shouldBeCaseInsensitiveForHeaderName() {
        // given
        Multimap<String, String> headers = ArrayListMultimap.create();
        headers.put("X-ONEXV3-EXP-E789", "groupC");

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertEquals(1, result.size());
        assertEquals("groupC", result.get("X-ONEXV3-EXP-E789"));
    }

    @Disabled
    @Test
    void parseOnexHeader_multimap_shouldJoinMultipleValuesWithPipe() {
        // given
        Multimap<String, String> headers = ArrayListMultimap.create();
        headers.put("x-onexv3-exp-e999", "groupA");
        headers.put("x-onexv3-exp-e999", "groupB");

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertEquals(1, result.size());
        assertEquals("groupA|groupB", result.get("x-onexv3-exp-e999"));
    }

    @Test
    void parseOnexHeader_map_shouldReturnEmptyMapWhenNoMatchingHeaders() {
        // given
        Map<String, List<String>> headers = Map.of(
                "X-Other-Header", List.of("foo"),
                "x-another-header", List.of("bar")
        );

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void parseOnexHeader_map_shouldReturnSingleMatchingHeader() {
        // given
        Map<String, List<String>> headers = Map.of(
                "x-onexv3-exp-e123", List.of("groupA")
        );

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertEquals(1, result.size());
        assertEquals("groupA", result.get("x-onexv3-exp-e123"));
    }

    @Test
    void parseOnexHeader_map_shouldReturnMultipleMatchingHeaders() {
        // given
        Map<String, List<String>> headers = Map.of(
                "x-onexv3-exp-e123", List.of("groupA"),
                "x-onexv3-exp-e456", List.of("groupB")
        );

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertEquals(2, result.size());
        assertEquals("groupA", result.get("x-onexv3-exp-e123"));
        assertEquals("groupB", result.get("x-onexv3-exp-e456"));
    }

    @Test
    void parseOnexHeader_map_shouldJoinMultipleValuesWithPipe() {
        // given
        Map<String, List<String>> headers = Map.of(
                "x-onexv3-exp-e999", List.of("groupA", "groupB")
        );

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertEquals(1, result.size());
        assertEquals("groupA|groupB", result.get("x-onexv3-exp-e999"));
    }

    @Test
    void parseOnexHeader_map_shouldBeCaseSensitiveForHeaderName() {
        // given
        Map<String, List<String>> headers = Map.of(
                "X-ONEXV3-EXP-E789", List.of("groupC")
        );

        // when
        Map<String, String> result = ExperimentService.parseOnexHeader(headers);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void withHeaders_shouldReturnExperimentQueryWithActiveConfigs() throws SourceException {
        // given
        Multimap<String, String> httpHeaders = ArrayListMultimap.create();
        httpHeaders.put("x-onexv3-exp-e123", "groupA");
        Configs groupAConfigs = of(Map.of("paramA", List.of("valueA")));
        Map<String, Configs> e123 = Map.of("groupA", groupAConfigs);
        Groups e123Groups = new Groups(e123);
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("e123", e123Groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        ExperimentService.ExperimentQuery query = experimentService.withHeaders(httpHeaders);

        // then
        assertEquals("valueA", query.getParameter("paramA"));
    }

    @Test
    void withHeaders_shouldReturnExperimentQueryWithEmptyConfigsWhenNoMatchingHeaders() throws SourceException {
        // given
        Multimap<String, String> httpHeaders = ArrayListMultimap.create();
        httpHeaders.put("X-Other-Header", "foo");
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of())
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        ExperimentService.ExperimentQuery query = experimentService.withHeaders(httpHeaders);

        // then
        assertEquals("", query.getParameter("nonexistent"));
    }

    @Test
    void withHeaders_shouldHandleMultipleExperimentHeaders() throws SourceException {
        // given
        Multimap<String, String> httpHeaders = ArrayListMultimap.create();
        httpHeaders.put("x-onexv3-exp-e123", "groupA");
        httpHeaders.put("x-onexv3-exp-e456", "groupB");
        Configs groupAConfigs = of(Map.of("paramA", List.of("valueA")));
        Configs groupBConfigs = of(Map.of("paramB", List.of("valueB")));
        Map<String, Configs> e123 = Map.of("groupA", groupAConfigs);
        Map<String, Configs> e456 = Map.of("groupB", groupBConfigs);
        Groups e123Groups = new Groups(e123);
        Groups e456Groups = new Groups(e456);
        Experiments experiments = Experiments.builder()
                                             .experiments(Map.of("e123", e123Groups, "e456", e456Groups))
                                             .build();
        when(experimentSource.load()).thenReturn(experiments);
        ExperimentService experimentService = ExperimentService.builder()
                                                               .experimentProvider(ExperimentProvider.builder()
                                                                                                     .context(context)
                                                                                                     .properties(experiments.getExperiments())
                                                                                                     .build())
                                                               .build();

        // when
        ExperimentService.ExperimentQuery query = experimentService.withHeaders(httpHeaders);

        // then
        assertEquals("valueA", query.getParameter("paramA"));
        assertEquals("valueB", query.getParameter("paramB"));
    }
}
