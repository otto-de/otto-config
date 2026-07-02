package de.otto.config.source.aws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.otto.config.source.aws.S3TogglesSource.ToggleEntry;

public class ToggleEntryTest {

    @Test
    public void shouldParseOnPrefixAsEnabled() {
        Optional<ToggleEntry> entry = ToggleEntry.parse("feature-toggles/on.featureA");
        assertThat(entry, is(Optional.of(new ToggleEntry("featureA", true))));
    }

    @Test
    public void shouldParseOffPrefixAsDisabled() {
        Optional<ToggleEntry> entry = ToggleEntry.parse("feature-toggles/off.featureB");
        assertThat(entry, is(Optional.of(new ToggleEntry("featureB", false))));
    }

    @Test
    public void shouldTreatOnPrefixCaseInsensitively() {
        Optional<ToggleEntry> entry = ToggleEntry.parse("feature-toggles/ON.featureC");
        assertThat(entry, is(Optional.of(new ToggleEntry("featureC", true))));
    }

    @Test
    public void shouldTreatOffPrefixCaseInsensitively() {
        Optional<ToggleEntry> entry = ToggleEntry.parse("feature-toggles/OFF.featureD");
        assertThat(entry, is(Optional.of(new ToggleEntry("featureD", false))));
    }

    @Test
    public void shouldMapDottedToggleNameVerbatim() {
        Optional<ToggleEntry> entry = ToggleEntry.parse("feature-toggles/on.description.use-dynamodb");
        assertThat(entry, is(Optional.of(new ToggleEntry("description.use-dynamodb", true))));
    }

    @Test
    public void shouldStripS3FolderPathFromKey() {
        Optional<ToggleEntry> entry = ToggleEntry.parse("a/b/c/on.featureA");
        assertThat(entry, is(Optional.of(new ToggleEntry("featureA", true))));
    }

    @Test
    public void shouldIgnoreKeyWithoutRecognizedPrefix() {
        assertThat(ToggleEntry.parse("feature-toggles/featureA"), is(Optional.empty()));
    }

    @Test
    public void shouldIgnoreOnPrefixWithEmptyToggleName() {
        assertThat(ToggleEntry.parse("feature-toggles/on."), is(Optional.empty()));
    }

    @Test
    public void shouldIgnoreOffPrefixWithEmptyToggleName() {
        assertThat(ToggleEntry.parse("feature-toggles/off."), is(Optional.empty()));
    }
}