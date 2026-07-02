package de.otto.config.source.aws;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;

import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceException;
import de.otto.config.domain.Toggles;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Reads feature toggles from S3. Toggle state is encoded in the object name:
 * {@code on.<name>} maps to enabled, {@code off.<name>} maps to disabled. The
 * object content is never read, so this source only requires {@code s3:ListBucket}.
 */
@Slf4j
public class S3TogglesSource extends Source<Toggles> {
    private final @NonNull S3Client s3Client;
    private final @NonNull String bucketName;
    private final @NonNull String togglesFolder;

    @Builder
    private S3TogglesSource(@NonNull S3Client s3Client, @NonNull String bucketName, @NonNull String togglesFolder) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.togglesFolder = asS3Folder(togglesFolder);
    }

    /**
     * S3 has no folders: a prefix only bounds one when it ends with '/'. Normalising stops a
     * configured {@code feature-toggles} from also matching {@code feature-toggles-archive/}.
     */
    private static String asS3Folder(String location) {
        return location.isEmpty() || location.endsWith("/") ? location : location + "/";
    }

    @Override
    public TypeReference<Toggles> getTypeReference() {
        return Toggles.typeReference;
    }

    @Override
    public Toggles getEmptyValue() {
        return Toggles.empty;
    }

    @Override
    public Toggles load() throws SourceException {
        try {
            Map<String, Boolean> toggles = new HashMap<>();
            fetchToggleKeys().map(ToggleEntry::parse)
                              .flatMap(Optional::stream)
                              .forEach(entry -> toggles.merge(entry.name(), entry.enabled(), (existing, next) -> existing || next));

            log.info("Loaded {} feature toggles from S3 bucket='{}' prefix='{}'",
                     toggles.size(), this.bucketName, this.togglesFolder);

            return toToggles(toggles);
        } catch (Exception e) {
            throw new SourceException("Could not load Otto Config toggles from S3", e);
        }
    }

    private Stream<String> fetchToggleKeys() {
        return this.s3Client.listObjectsV2Paginator(
                        ListObjectsV2Request.builder()
                                            .bucket(this.bucketName)
                                            .prefix(this.togglesFolder)
                                            .build())
                .stream()
                .flatMap(page -> page.contents().stream())
                .map(S3Object::key);
    }

    private static Toggles toToggles(Map<String, Boolean> toggles) {
        Map<String, Map<String, Object>> values = new HashMap<>();
        toggles.forEach((toggleName, enabled) -> values.put(toggleName, Map.of("enabled", enabled)));
        return new Toggles(values);
    }

    /**
     * Interprets an S3 object key's file name as a toggle: {@code on.<name>} / {@code off.<name>}
     * (case-insensitive prefix), empty if the key does not follow this convention.
     */
    record ToggleEntry(String name, boolean enabled) {
        private static final String ON_PREFIX = "on.";
        private static final String OFF_PREFIX = "off.";

        static Optional<ToggleEntry> parse(String key) {
            String fileName = key.substring(key.lastIndexOf('/') + 1);
            String lower = fileName.toLowerCase();
            if (lower.startsWith(ON_PREFIX)) {
                return toEntry(fileName.substring(ON_PREFIX.length()), true);
            }
            if (lower.startsWith(OFF_PREFIX)) {
                return toEntry(fileName.substring(OFF_PREFIX.length()), false);
            }
            return Optional.empty();
        }

        private static Optional<ToggleEntry> toEntry(String toggleName, boolean enabled) {
            return toggleName.isEmpty() ? Optional.empty() : Optional.of(new ToggleEntry(toggleName, enabled));
        }
    }
}
