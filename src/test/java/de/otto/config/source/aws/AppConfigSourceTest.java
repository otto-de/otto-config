package de.otto.config.source.aws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.source.SourceException;
import de.otto.config.domain.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


public class AppConfigSourceTest {

    @Mock
    private AppConfigDataClient appConfigDataClient;

    private AppConfigSource<Properties> appConfigSource;

    private String jsonString;

    private static class TestableAwsAppConfigSource extends AppConfigSource<Properties> {

        public TestableAwsAppConfigSource(String applicationIdentifier,
            String environmentIdentifier,
            String configurationProfileIdentifier,
            AppConfigDataClient appConfigDataClient) {
            super(applicationIdentifier, configurationProfileIdentifier, appConfigDataClient,
                    new ObjectMapper(), Properties.empty, new TypeReference<Properties>() {}, "", true);
        }
    }

    @BeforeEach
    void setup() throws IOException, SourceException {
        MockitoAnnotations.openMocks(this);
        jsonString = Files.readString(Paths.get(
                Objects.requireNonNull(getClass().getClassLoader().getResource("properties.json")).getPath()));

        StartConfigurationSessionRequest expectedStartConfigurationSessionRequestRequest = StartConfigurationSessionRequest.builder()
            .applicationIdentifier("test-application")
            .environmentIdentifier("local")
            .configurationProfileIdentifier("properties")
            .build();

        StartConfigurationSessionResponse startSessionResponse = StartConfigurationSessionResponse.builder()
            .initialConfigurationToken("initial-token")
            .build();

        when(appConfigDataClient.startConfigurationSession(expectedStartConfigurationSessionRequestRequest)).thenReturn(
            startSessionResponse);

        GetLatestConfigurationRequest expectedGetLatestConfigurationRequestRequest = GetLatestConfigurationRequest.builder()
            .configurationToken("initial-token")
            .build();

        GetLatestConfigurationResponse getLatestConfigurationResponse = GetLatestConfigurationResponse.builder()
            .configuration(SdkBytes.fromUtf8String(jsonString))
            .nextPollConfigurationToken("next-token")
            .build();

        when(appConfigDataClient.getLatestConfiguration(expectedGetLatestConfigurationRequestRequest)).thenReturn(
            getLatestConfigurationResponse);

        appConfigSource = new TestableAwsAppConfigSource("test-application", "local", "properties",
            appConfigDataClient);
        appConfigSource.load();
    }

    @Test
    void shouldGenerateInitialConfigurationToken() {
        // when
        String configurationToken = appConfigSource.getConfigurationToken();

        // then
        assertEquals("next-token", configurationToken);
    }

    @Test
    void shouldHandleResourceNotFoundExceptionWhileGeneratingInitialConfigurationToken() {
        // given
        when(appConfigDataClient.startConfigurationSession(any(StartConfigurationSessionRequest.class))).thenThrow(
            ResourceNotFoundException.class);

        // when
        appConfigSource = new TestableAwsAppConfigSource("test-application", "local", "test",
            appConfigDataClient);

        // then
        assertTrue(appConfigSource.getConfigurationToken().isEmpty());
    }

    @Test
    void shouldHandleAllExceptionsWhileGeneratingInitialConfigurationToken() {
        // given
        when(appConfigDataClient.startConfigurationSession(any(StartConfigurationSessionRequest.class))).thenThrow(
            AppConfigDataException.class);

        // when
        appConfigSource = new TestableAwsAppConfigSource("test-application", "local", "test",
            appConfigDataClient);

        // then
        assertTrue(appConfigSource.getConfigurationToken().isEmpty());
    }

    @Test
    void shouldLoadConfiguration() {
        // then
        assertTrue(!appConfigSource.getConfigurationToken().isEmpty());
    }

    @Test
    void shouldKeepConfigurationIfNoConfiguration() throws SourceException {
        // given
        GetLatestConfigurationResponse getLatestConfigurationResponse = GetLatestConfigurationResponse.builder()
            .configuration(SdkBytes.fromUtf8String(""))
            .nextPollConfigurationToken("next-token")
            .build();
        when(appConfigDataClient.getLatestConfiguration(any(GetLatestConfigurationRequest.class))).thenReturn(
            getLatestConfigurationResponse);

        // when
        appConfigSource.load();

        // then
        assertEquals("next-token", appConfigSource.getConfigurationToken());
    }

    @Test
    void shouldHandleEmptyConfigurationTokenWhenLoadingConfiguration() throws SourceException {
        // given
        when(appConfigDataClient.startConfigurationSession(any(StartConfigurationSessionRequest.class))).thenThrow(
            ResourceNotFoundException.class);

        // when
        appConfigSource = new TestableAwsAppConfigSource("test-application", "local", "test",
            appConfigDataClient);

        // then
        assertTrue(appConfigSource.getConfigurationToken().isEmpty());

        // given
        StartConfigurationSessionResponse startSessionResponse = StartConfigurationSessionResponse.builder()
            .initialConfigurationToken("initial-token")
            .build();

        when(appConfigDataClient.startConfigurationSession(any(StartConfigurationSessionRequest.class))).thenReturn(
            startSessionResponse);

        GetLatestConfigurationResponse getLatestConfigurationResponse = GetLatestConfigurationResponse.builder()
            .configuration(SdkBytes.fromUtf8String(jsonString))
            .nextPollConfigurationToken("next-token")
            .build();

        when(appConfigDataClient.getLatestConfiguration(any(GetLatestConfigurationRequest.class))).thenReturn(
            getLatestConfigurationResponse);

        // when
        appConfigSource.load();

        // then
        assertEquals("next-token", appConfigSource.getConfigurationToken());
    }

    @Test
    void shouldThrowExceptionWhileLoadingConfiguration() throws SourceException {
        // given
        when(appConfigDataClient.getLatestConfiguration(any(GetLatestConfigurationRequest.class))).thenThrow(
            BadRequestException.class);

        // when
        appConfigSource = new TestableAwsAppConfigSource("test-application", "local", "properties",
            appConfigDataClient);
        assertThrows(SourceException.class, () -> appConfigSource.load());

        // then
        assertEquals("initial-token", appConfigSource.getConfigurationToken());
    }
}
