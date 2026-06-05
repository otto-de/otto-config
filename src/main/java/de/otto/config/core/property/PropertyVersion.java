package de.otto.config.core.property;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.Getter;

@Getter
public class PropertyVersion extends Property<PropertyVersion.VersionSnapshot> {

    public static class VersionSnapshot {
        final List<String> versions;
        final Optional<String> current;
        final Optional<String> previous;

        VersionSnapshot(List<String> versions) {
            this.versions = Collections.unmodifiableList(versions);
            this.current = versions.isEmpty() ? Optional.empty() : Optional.of(versions.get(0));
            this.previous = versions.size() < 2 ? Optional.empty() : Optional.of(versions.get(1));
        }
    }
    
    public PropertyVersion() {
        super(new VersionSnapshot(Collections.emptyList()));
    }

    public PropertyVersion(List<String> versions) {
        super(new VersionSnapshot(versions));
    }

    public Optional<String> getCurrent() {
        return this.value.current;
    }

    public Optional<String> getPrevious() {
        return this.value.previous;
    }

    public List<String> getVersions() {
        return this.value.versions;
    }

    protected void setVersions(List<String> versions) {
        this.value = new VersionSnapshot(versions);
    }

    @Override
    public boolean isEmpty() {
        return this.value.versions.isEmpty();
    }

    public static PropertyVersion of(List<String> values) {
        return new PropertyVersion(values);
    }
}
