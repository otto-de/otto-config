package de.otto.config.source.aws;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.source.Source;
import de.otto.config.domain.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SecretsManagerSourceTest {

    @Mock
    private SecretsManagerClient secretsManagerClient;

    private final String secretARN = "someARN";

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldLoadPropertiesFromSecretsManager() {
        // given
        GetSecretValueRequest currentRequest = GetSecretValueRequest.builder()
                                                                    .secretId(secretARN)
                                                                    .versionStage("AWSCURRENT")
                                                                    .build();
        GetSecretValueRequest previousRequest = GetSecretValueRequest.builder()
                                                                     .secretId(secretARN)
                                                                     .versionStage("AWSPREVIOUS")
                                                                     .build();

        GetSecretValueResponse currentResponse = GetSecretValueResponse.builder()
                                                                       .arn(secretARN)
                                                                       .secretString("{\"key1\":\"value1\", \"key2\":\"value3\"}")
                                                                       .versionStages("AWSCURRENT")
                                                                       .build();
        GetSecretValueResponse previousResponse = GetSecretValueResponse.builder()
                                                                        .arn(secretARN)
                                                                        .secretString("{\"key1\":\"value2\"}")
                                                                        .versionStages("AWSPREVIOUS")
                                                                        .build();

        when(secretsManagerClient.getSecretValue(currentRequest))
                .thenReturn(currentResponse);
        when(secretsManagerClient.getSecretValue(previousRequest))
                .thenReturn(previousResponse);

        Source<Properties> secretsManagerPropertySource = SecretsManagerSource.builder()
                .secretARN(secretARN)
                .secretsManagerClient(secretsManagerClient)
                .objectMapper(new ObjectMapper())
                .build();

        // when
        Properties result = secretsManagerPropertySource.getOrLoad();

        // then
        assertEquals(2, result.getProperties().size());
        assertEquals("value1,value2", result.getProperties().get("key1"));
        assertEquals("value3", result.getProperties().get("key2"));
        verify(secretsManagerClient, times(2)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    public void shouldHandleMissingVersion() {
        // given
        GetSecretValueRequest currentRequest = GetSecretValueRequest.builder()
                                                                    .secretId(secretARN)
                                                                    .versionStage("AWSCURRENT")
                                                                    .build();
        GetSecretValueRequest previousRequest = GetSecretValueRequest.builder()
                                                                     .secretId(secretARN)
                                                                     .versionStage("AWSPREVIOUS")
                                                                     .build();

        GetSecretValueResponse currentResponse = GetSecretValueResponse.builder()
                                                                       .arn(secretARN)
                                                                       .secretString("{\"key1\":\"value1\"}")
                                                                       .versionStages("AWSCURRENT")
                                                                       .build();
        when(secretsManagerClient.getSecretValue(currentRequest))
                .thenReturn(currentResponse);
        when(secretsManagerClient.getSecretValue(previousRequest))
                .thenThrow(ResourceNotFoundException.builder().build());

        Source<Properties> secretsManagerPropertySource = SecretsManagerSource.builder()
                .secretARN(secretARN)
                .secretsManagerClient(secretsManagerClient)
                .objectMapper(new ObjectMapper())
                .build();

        // when
        Properties result = secretsManagerPropertySource.getOrLoad();

        // then
        assertEquals(1, result.getProperties().size());
        assertEquals("value1", result.getProperties().get("key1"));
        verify(secretsManagerClient, times(2)).getSecretValue(any(GetSecretValueRequest.class));
    }
}
