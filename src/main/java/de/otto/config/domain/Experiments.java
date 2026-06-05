package de.otto.config.domain;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import de.otto.config.core.Configuration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.collectingAndThen;
import static lombok.AccessLevel.PRIVATE;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = Experiments.ExperimentsDeserializer.class)
@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor
@Data
@Builder
public class Experiments implements Configuration<Experiments.Groups> {
    public static final Experiments empty = new Experiments(emptyMap());
    public static final TypeReference<Experiments> typeReference = new TypeReference<Experiments>() {};
    public static final String HEADER_ONEX_KEY_PREFIX = "x-onexv3-exp-";

    @Builder.Default
    private Map<String, Groups> experiments = emptyMap();

    @Override
    public Map<String, Groups> getProperties() {
        return this.getExperiments();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Groups {
        private Map<String, Configs> groups = new HashMap<>();
        public static final Groups empyGroups = new Groups(emptyMap());

        @JsonAnySetter
        public void setGroup (String group, Configs configs) {
            this.groups.put(group, configs);
        }

        public Optional<Map.Entry<String, Configs>> configsForGroup(String groupName) {
            return groups.entrySet()
                    .stream()
                    .filter(entry -> groupName.contains("|") ? groupName.toLowerCase().contains(entry.getKey().toLowerCase()) : 
                                                                entry.getKey().equalsIgnoreCase(groupName))
                    .findFirst();
        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Configs {
        @JsonDeserialize(using = ConfigsDeserializer.class)
        Multimap<String, String> configs;

        public static final Configs emptyConfigs = new Configs(ArrayListMultimap.create());

        @SuppressWarnings("null")
        public static Configs of(Map<String, List<String>> configs) {
            Multimap<String, String> multiValuedMap = ArrayListMultimap.create();
            configs.forEach((key, values) -> multiValuedMap.putAll(key, values != null ? values : emptyList()));
            return new Configs(multiValuedMap);
        }

        public static Configs of(Multimap<String, String> configs) {
            return new Configs(configs);
        }

        public static Configs flatten(List<Configs> configs) {
            return collectStream(
                configs.stream()
                    .map(Configs::getConfigs)
                    .flatMap(configMap -> configMap.asMap().entrySet().stream()
                                                    .map(entry -> Map.entry(entry.getKey(), new ArrayList<>(entry.getValue())))));
        }

        public Configs mergeWith(Configs toMergeConfigs) {
            return collectStream(Stream.concat(configs.asMap().entrySet().stream()
                                                    .map(entry -> Map.entry(entry.getKey(), new ArrayList<>(entry.getValue()))),
                                            toMergeConfigs.configs.asMap().entrySet().stream()
                                                            .map(entry -> Map.entry(entry.getKey(), new ArrayList<>(entry.getValue())))));
        }

        private static Configs collectStream(Stream<Map.Entry<String, List<String>>> entryStream) {
            return entryStream.collect(collectingAndThen(toMap(Map.Entry::getKey,
                                                            Map.Entry::getValue,
                                                            (val1, val2) -> val1), Configs::of));
        }

        public boolean isDebug() {
            return Optional.ofNullable(configs.get("debug"))
                        .flatMap(list -> list.stream().findFirst())
                        .map(value -> List.of("on", "true").contains(value.toLowerCase()))
                        .orElse(false);
        }
        
        @SuppressWarnings("null")
        public List<String> get(String key) {
            return configs.get(key).stream().collect(Collectors.toList());
        }

        @SuppressWarnings("null")
        public boolean containsKey(String key) {
            return configs.containsKey(key);
        }

        @SuppressWarnings("null")
        public String getFirst(String key) {
            return configs.get(key).stream().findFirst().orElse(null);
        }

        public List<String> getParam(String key) {
            return Optional.ofNullable(configs.asMap().getOrDefault(key, emptyList()))
                            .map(values -> {
                                if (values.size() == 1) {
                                    return Optional.ofNullable(values.iterator().next())
                                                    .map(val -> Arrays.asList(val.split(",")))
                                                    .orElse(emptyList());
                                }
                                return new ArrayList<>(values);
                        }
                    )
                    .orElse(emptyList());
        }

        public boolean isEmpty() {
            return this.equals(emptyConfigs);
        }
    }

    @Value
    public static class Experiment {
        private final String experimentName;
        private final String group;
        private final Configs configs;
    }

    public static class ExperimentsDeserializer extends JsonDeserializer<Experiments> {
        @Override
        public Experiments deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();
            JsonNode rootNode = parser.getCodec().readTree(parser);
            
            Map<String, Groups> experimentsData = emptyMap();
            
            // Only extract the experiments/onex section, ignore everything else
            if (rootNode.has("experiments")) {
                experimentsData = mapper.convertValue(
                    rootNode.get("experiments"), 
                    new TypeReference<Map<String, Groups>>() {}
                );
            } else if (rootNode.has("onex")) {
                experimentsData = mapper.convertValue(
                    rootNode.get("onex"), 
                    new TypeReference<Map<String, Groups>>() {}
                );
            }
            // If neither exists, return empty (don't fail)
            
            return Experiments.builder()
                              .experiments(experimentsData)
                              .build();
        }
    }

    public static class ConfigsDeserializer extends JsonDeserializer<Multimap<String, String>> {
        @Override
        public Multimap<String, String> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
            Map<String, String> map = mapper.readValue(jsonParser, new TypeReference<Map<String, String>>() {});
            
            Multimap<String, String> multiValueMap = ArrayListMultimap.create();
            map.forEach(multiValueMap::put);
            return multiValueMap;
        }
    }
}
