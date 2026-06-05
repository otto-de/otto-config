package de.otto.config.source.file;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.Configuration;
import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceException;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
@Getter
public class FileSource<T extends Configuration<?>> extends Source<T> {
    private final ObjectMapper objectMapper;
    private final String localFile;
    private final T emptyValue;
    private final TypeReference<T> typeReference;
    private final String section;

    @Override
    public T load() throws SourceException {
        try {
            InputStream is = Thread.currentThread()
                                   .getContextClassLoader()
                                   .getResourceAsStream(localFile);

            if (is == null) {
                return emptyValue;
            }

            if (section != null && !section.isEmpty()) {
                return loadFromSection(is);
            }

            return objectMapper.readValue(is, typeReference);

        } catch (Exception exception) {
            log.error("The Source<" + typeReference.getType().getTypeName() + "> object could not be created and has been initialized as empty.", exception);
        }
        return emptyValue;
    }

    private T loadFromSection(InputStream is) throws IOException {
        JsonNode rootNode = objectMapper.readTree(is);
        JsonNode sectionNode = rootNode.get(section);
            
        if (sectionNode == null) {
            log.debug("Section '{}' not found in {}, returning empty", section, localFile);
            return emptyValue;
        }
            
        return objectMapper.convertValue(sectionNode, typeReference);
    }
}
