package de.otto.config.source.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.config.domain.Properties;
import de.otto.config.domain.Toggles;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class FileSourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldLoadPropertiesFromFileWithoutSection() {
        // given – properties.json is on the test classpath; its "properties" key is mapped to Properties.properties
        FileSource<Properties> source = FileSource.<Properties>builder()
                .objectMapper(objectMapper)
                .localFile("properties.json")
                .emptyValue(Properties.empty)
                .typeReference(Properties.typeReference)
                .build();

        // when
        Properties result = source.getOrLoad();

        // then
        assertThat(result.getProperties(), hasEntry("myKey1", "myValue"));
        assertThat(result.getProperties(), hasEntry("myKey2", "myValue1;myValue2"));
        assertThat(result.getProperties(), not(anEmptyMap()));
    }

    @Test
    void shouldLoadTogglesFromFileSection() {
        // given – section "toggles" inside properties.json
        FileSource<Toggles> source = FileSource.<Toggles>builder()
                .objectMapper(objectMapper)
                .localFile("properties.json")
                .emptyValue(Toggles.empty)
                .typeReference(Toggles.typeReference)
                .section("toggles")
                .build();

        // when
        Toggles result = source.getOrLoad();

        // then
        assertThat(result.getProperties(), hasEntry("another_toggle", false));
        assertThat(result.getProperties(), hasEntry("ftsn-415-test-toggle", true));
    }

    @Test
    void shouldReturnEmptyValueWhenFileDoesNotExist() {
        // given
        FileSource<Properties> source = FileSource.<Properties>builder()
                .objectMapper(objectMapper)
                .localFile("nonexistent-file.json")
                .emptyValue(Properties.empty)
                .typeReference(Properties.typeReference)
                .build();

        // when
        Properties result = source.getOrLoad();

        // then
        assertThat(result, is(Properties.empty));
        assertThat(result.getProperties(), anEmptyMap());
    }

    @Test
    void shouldReturnEmptyValueWhenSectionDoesNotExist() {
        // given
        FileSource<Toggles> source = FileSource.<Toggles>builder()
                .objectMapper(objectMapper)
                .localFile("properties.json")
                .emptyValue(Toggles.empty)
                .typeReference(Toggles.typeReference)
                .section("nonexistent-section")
                .build();

        // when
        Toggles result = source.getOrLoad();

        // then
        assertThat(result, is(Toggles.empty));
    }

    @Test
    void shouldReturnTrueForIsPullRefreshEnabled() {
        // given
        FileSource<Properties> source = FileSource.<Properties>builder()
                .objectMapper(objectMapper)
                .localFile("properties.json")
                .emptyValue(Properties.empty)
                .typeReference(Properties.typeReference)
                .build();

        // then
        assertThat(source.isPullRefreshEnabled(), is(true));
    }
}
