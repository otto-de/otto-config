package de.otto.config.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Multimap;

import de.otto.config.core.Context;
import de.otto.config.domain.Experiments.Configs;
import de.otto.config.domain.Experiments.Experiment;
import de.otto.config.domain.Experiments.Groups;
import de.otto.config.provider.ExperimentProvider;
import lombok.Builder;

import static java.util.stream.Collectors.toList;

public class ExperimentService {
    public static final String HEADER_ONEX_KEY_PREFIX = "x-onexv3-exp-";

    private final ExperimentProvider experimentProvider;

    @Builder
    public ExperimentService(Context context, ExperimentProvider experimentProvider) {
        this.experimentProvider = experimentProvider != null ? experimentProvider 
                                                             : ExperimentProvider.builder().context(context).build();
    }

    public ExperimentQuery withHeaders(Multimap<String, String> httpHeaders) {
        return new ExperimentQuery(getParamsFromActiveExperiments(httpHeaders));
    }

    public ExperimentQuery withHeaders(Map<String, List<String>> httpHeaders) {
        return new ExperimentQuery(getParamsFromActiveExperiments(httpHeaders));
    }

    public Configs getParamsFromActiveExperiments(Multimap<String, String> httpHeaders) {
        Map<String, String> headerConfigs = parseOnexHeader(httpHeaders);
        return getParamsFromHeaderConfigs(headerConfigs);
    }

    public Configs getParamsFromActiveExperiments(Map<String, List<String>> httpHeaders) {
        Map<String, String> headerConfigs = parseOnexHeader(httpHeaders);
        return getParamsFromHeaderConfigs(headerConfigs);
    }

    public Configs getParamsFromHeaderConfigs(Map<String, String> headerConfigs) {
        return evaluateFromHeader(headerConfigs).stream()
                                                .map(Experiment::getConfigs)
                                                .collect(Collectors.collectingAndThen(toList(), Configs::flatten));
    }

    public Map<String, Groups> getExperiments() {
        return this.experimentProvider.getProperties();
    }

    public List<String> activeExperimentStringsByHeader(Multimap<String, String> httpHeaders) {
        return evaluateFromHeader(parseOnexHeader(httpHeaders)).stream()
                                                               .map(e -> String.format("%s-%s",
                                                                                       e.getExperimentName(),
                                                                                       e.getGroup()))
                                                                                       .collect(Collectors.toList());
    }

    public static List<String> allExperimentStringsByHeader(Multimap<String, String> httpHeaders) {
        return parseOnexHeader(httpHeaders)
                .entrySet()
                .stream()
                .map(e -> String.format("%s-%s",
                                        e.getKey().toLowerCase().replace(HEADER_ONEX_KEY_PREFIX, ""),
                                        e.getValue()))
                                        .collect(Collectors.toList());
    }

    public Set<String> getAllExperimentNames() {
        return this.experimentProvider.getProperties().keySet();
    }

    public Configs applyActiveExperiments(Configs queryParameterConfigs,
                                          Configs headerConfigs) {
        // The experiment params should always win. We want to prevent a wrong experiment setup, if the client sends a
        // toggle, which is also included in experiment setup.
        return headerConfigs.mergeWith(queryParameterConfigs);
    }

    public static Map<String, String> parseOnexHeader(Multimap<String, String> httpHeaders) {
        return httpHeaders.entries().stream()
                          .filter(e -> e.getKey().toLowerCase().startsWith(HEADER_ONEX_KEY_PREFIX))
                          .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join("|", e.getValue())));
    }

    public static Map<String, String> parseOnexHeader(Map<String, List<String>> httpHeaders) {
        return httpHeaders.entrySet()
                          .stream()
                          .filter(entry -> entry.getKey().startsWith(HEADER_ONEX_KEY_PREFIX))
                          .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join("|", e.getValue())));
    }

    private List<Experiment> evaluateFromHeader(Map<String, String> headerConfigs) {
        return this.experimentProvider
                   .getProperties()
                   .entrySet()
                   .stream()
                   .flatMap(e -> findExperimentsByHeader(headerConfigs, e))
                   .collect(Collectors.toList());
    }

    private Stream<Experiment> findExperimentsByHeader(Map<String, String> headerConfigs, Map.Entry<String, Groups> experiment) {
        return headerConfigs.entrySet()
                            .stream()
                            .filter(onexHeader -> onexHeader.getKey()
                                                            .equalsIgnoreCase(HEADER_ONEX_KEY_PREFIX + experiment.getKey()))
                            .map(onexHeader -> experiment.getValue().configsForGroup(onexHeader.getValue()))
                            .filter(Optional::isPresent)
                            .map(group -> new Experiment(experiment.getKey(),
                                                         group.get().getKey(),
                                                         group.get().getValue()));
    }

    public static class ExperimentQuery {
        private final Configs activeConfigs;

        public ExperimentQuery(Configs activeConfigs) {
            this.activeConfigs = activeConfigs;
        }

        public String getParameter(String key) {
            return getParameter(key, activeConfigs);
        }

        public String getParameter(String key, String defaultValue) {
            String value = getParameter(key);
            return value.isEmpty() ? defaultValue : value;
        }

        public boolean getParameterAsBoolean(String key) {
            return getParameterAsBoolean(key, activeConfigs);
        }

        public boolean getParameterAsBoolean(String key, boolean defaultValue) {
            String value = getParameter(key);
            return value.isEmpty() ? defaultValue : Boolean.parseBoolean(value);
        }

        private String getParameter(String key, Configs params) {
            return params.containsKey(key) ? params.get(key).get(0) : "";
        }

        private Boolean getParameterAsBoolean(String key, Configs params) {
            return (Boolean.parseBoolean(getParameter(key, params)));
        }
    }
}
