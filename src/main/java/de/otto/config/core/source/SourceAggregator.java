package de.otto.config.core.source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import de.otto.config.core.Configuration;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class SourceAggregator {
    private static final Pattern KEY_REPLACEMENT_PATTERN = Pattern.compile("[/_]");
    private static final Pattern LEADING_DOTS_PATTERN = Pattern.compile("^\\.+");

    public static <T> Map<String, T> aggregate(List<Source<? extends Configuration<?>>> sources, Function<Object, T> valueTransformer, boolean normalizeKeys) {
        return aggregate(sources, valueTransformer, normalizeKeys, true);
    }

    public static <T> Map<String, T> aggregate(List<Source<? extends Configuration<?>>> sources, Function<Object, T> valueTransformer, boolean normalizeKeys, boolean forceReload) {
        Map<String, T> result = new HashMap<>();
        
        sources.forEach(source -> {
            source.getOrLoad(forceReload).getProperties().forEach((key, value) -> {
                if (!result.containsKey(key)) {
                    T transformedValue = valueTransformer.apply(value);
                    result.putIfAbsent(key, transformedValue);
                    
                    if (normalizeKeys) {
                        String normalizedKey = normalizeKey(key);
                        if (!normalizedKey.equals(key)) {
                            result.putIfAbsent(normalizedKey, transformedValue);
                        }
                    }
                }
            });
        });
        
        return Map.copyOf(result);
    }

    private static String normalizeKey(String key) {
        String normalized = KEY_REPLACEMENT_PATTERN.matcher(key).replaceAll(".");
        return LEADING_DOTS_PATTERN.matcher(normalized).replaceAll("");
    }
}
