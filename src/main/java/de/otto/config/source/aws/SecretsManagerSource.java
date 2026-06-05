package de.otto.config.source.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.aws.event.SecretsManagerChangeEvent;
import de.otto.config.core.source.SourceChangeEvent;
import de.otto.config.core.source.SourceException;
import de.otto.config.domain.Properties;
import de.otto.config.source.PropertySource;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SecretsManagerSource extends PropertySource { 
    private final @NonNull SecretsManagerClient secretsManagerClient;
    private final @NonNull ObjectMapper objectMapper;
    private final @NonNull String secretARN;
    private final boolean isPullRefreshEnabled;
    
    private final List<GetSecretValueRequest> requests;
    private static final List<String> VERSIONS = List.of("AWSCURRENT", "AWSPREVIOUS");
    private static final String VERSION_SUFFIX_REGEX = "_(" + String.join("|", VERSIONS) + ")$";
    private static final String EMPTY_BODY = "{}";

    @Builder
    private SecretsManagerSource(String secretARN,
                                 SecretsManagerClient secretsManagerClient,
                                 ObjectMapper objectMapper,
                                 boolean isPullRefreshEnabled) {
        this.secretARN = secretARN;
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = objectMapper;
        this.isPullRefreshEnabled = isPullRefreshEnabled;
        GetSecretValueRequest.Builder requestBuilder = GetSecretValueRequest.builder()
                                                                            .secretId(secretARN);
        this.requests = VERSIONS.stream()
                                .map(version -> requestBuilder.versionStage(version).build())
                                .collect(Collectors.toList());
        log.debug("Initialized SecretsPropertySource with secret name prefix: '{}'", secretARN);
    }

    @Override
    public boolean isPullRefreshEnabled() {
        return this.isPullRefreshEnabled;
    }
    
    @Override
    public boolean onChanged(SourceChangeEvent event) {
        if (!(event instanceof SecretsManagerChangeEvent e)) {
            return false;
        }
        // secretId in the event may be the full ARN or just the secret name;
        // match if either value contains the other to cover both forms.
        String eventSecretId = e.secretId();
        return eventSecretId.contains(this.secretARN) || this.secretARN.contains(eventSecretId);
    }

    @Override
    public Properties load() throws SourceException {
        try {
            Map<String, String> responseMap = this.mergeEntriesAsListValues(
                    requests.stream()
                            .map(this::getSecretValue)
                            .flatMap(this::asEntryStream),
                    key -> key.replaceAll(VERSION_SUFFIX_REGEX, ""));
            log.debug("Loaded '{}' properties from AWS Secrets Manager", responseMap.size());

            return new Properties(responseMap);
        } catch (Exception e) {
            throw new SourceException("Could not load Zealot properties from Secrets Manager", e);
        }
    }

    private GetSecretValueResponse getSecretValue(GetSecretValueRequest request) {
        try {
            return this.secretsManagerClient.getSecretValue(request);
        } catch (ResourceNotFoundException e) {
            return GetSecretValueResponse.builder()
                                         .secretString(EMPTY_BODY)
                                         .build();
        }
    }

    private Stream<Map.Entry<String, String>> asEntryStream(GetSecretValueResponse response) {
        try {
            Map<String, String> stringStringMap = objectMapper.readValue(response.secretString(), new TypeReference<>() {});
            return tagKeys(stringStringMap, response.versionStages()
                                                    .stream()
                                                    .findAny()
                                                    .orElse("UNKNOWN"));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<Map.Entry<String, String>> tagKeys(Map<String, String> responseMap, String versionStage) {
        return responseMap.entrySet()
                          .stream()
                          .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey() + "_" + versionStage, entry.getValue()));
    }
}
