package de.otto.config.source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.otto.config.core.source.SourceChangeEvent;
import de.otto.config.core.source.SourceException;
import de.otto.config.domain.Properties;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Builder
@Getter
public class CombinedPropertySource extends PropertySource {
    private final @NonNull List<PropertySource> sources;

    @Override
    public boolean onChanged(SourceChangeEvent event) {
        for (PropertySource source : sources) {
            if (source.onChanged(event)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("null")
    @Override
    public boolean hasSecrets() {
        return sources.stream().anyMatch(PropertySource::hasSecrets);
    }

    @Override
    public Properties load() throws SourceException {
        Map<String, String> combined = new HashMap<>();
        for (PropertySource source : sources) {
            combined.putAll(source.getOrLoad(true).getProperties());
        }
        return new Properties(combined);
    }
}
