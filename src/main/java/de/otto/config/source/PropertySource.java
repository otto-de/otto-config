package de.otto.config.source;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;

import de.otto.config.core.source.Source;
import de.otto.config.domain.Properties;

public abstract class PropertySource extends Source<Properties> {
    
    @Override
    public TypeReference<Properties> getTypeReference() {
        return Properties.typeReference;
    }

    @Override
    public Properties getEmptyValue() {
        return Properties.empty;
    }

    protected void mergeAsListValues(Map<String, String> source, Map<String, String> destination) {
        source.forEach((key, value) -> 
            destination.merge(key, value, (existing, newValue) -> existing + "," + newValue));
    }

    protected void mergeAsListValues(Map<String, String> source, 
                                     Map<String, String> destination, 
                                     Function<String, String> keyFormatter) {
        source.entrySet().stream()
                .forEach(entry -> {
                    String transformedKey = keyFormatter.apply(entry.getKey());
                    destination.merge(transformedKey, entry.getValue(), (existing, newValue) -> existing + "," + newValue);
                });
    }

    protected Map<String, String> mergeEntriesAsListValues(Stream<Map.Entry<String, String>> entries,
                                                           Function<String, String> keyFormatter) {
        return entries
                .sorted(Map.Entry.<String, String>comparingByKey()
                        .thenComparing(Map.Entry.comparingByValue()))
                .collect(Collectors.groupingBy(
                        entry -> keyFormatter.apply(entry.getKey()),
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.joining(","))));
    }
}
