package de.otto.config.source.aws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.Configuration;
import de.otto.config.core.aws.event.AppConfigDeploymentEvent;
import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceChangeEvent;
import de.otto.config.core.source.SourceException;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.AppConfigDataException;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.ResourceNotFoundException;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;

@Slf4j
@Getter
@Builder
public class AppConfigSource<T extends Configuration<?>> extends Source<T> {
    private final @NonNull String applicationIdentifier;
    private final String environmentIdentifier = "local";
    private final @NonNull String configurationProfileIdentifier;
    private final @NonNull AppConfigDataClient appConfigDataClient;
    private final @NonNull ObjectMapper objectMapper;
    private final @NonNull T emptyValue;
    private final @NonNull TypeReference<T> typeReference;

    @Builder.Default
    private String configurationToken = "";
    @Builder.Default
    private final boolean isPullRefreshEnabled = true;

    @Override
    public boolean isPullRefreshEnabled() {
        return this.isPullRefreshEnabled;
    }
    
    @Override
    public boolean onChanged(SourceChangeEvent event) {
        if (!(event instanceof AppConfigDeploymentEvent e)) {
            return false;
        }
        // AppConfig EventBridge events always include application-id and configuration-profile-id
        // (AWS-generated IDs), but application-name and configuration-profile-name are absent in
        // some schema versions and default to "". applicationIdentifier and
        // configurationProfileIdentifier hold the human-readable names used to start the session,
        // so direct ID comparison will never match.
        //
        // Application match: compare by name if the event carries it; otherwise trust the
        // EventBridge rule, which already filters on application-id, so any arriving event
        // belongs to our application.
        boolean appMatches = this.applicationIdentifier.equals(e.applicationId())
                || this.applicationIdentifier.equals(e.applicationName())
                || e.applicationName().isEmpty();

        // Profile match: compare by name if the event carries it; otherwise accept all profiles
        // (getLatestConfiguration returns empty content for unchanged profiles, making the
        // extra reload a cheap no-op).
        boolean profileMatches = this.configurationProfileIdentifier.equals(e.configProfileId())
                || this.configurationProfileIdentifier.equals(e.configProfileName())
                || e.configProfileName().isEmpty();

        return appMatches && profileMatches;
    }

    @Override
    public T load() throws SourceException{
        ensureConfigurationTokenExists();

        if (!this.configurationToken.isEmpty()) {
            try {
                GetLatestConfigurationRequest request = GetLatestConfigurationRequest.builder()
                    .configurationToken(configurationToken)
                    .build();
                GetLatestConfigurationResponse response = this.appConfigDataClient.getLatestConfiguration(request);

                String jsonString = response.configuration().asUtf8String();
                this.configurationToken = response.nextPollConfigurationToken();
                if (!jsonString.isEmpty()) {
                    log.debug("Loading configuration for applicationIdentifier: {}, environmentIdentifier: {}, configurationProfileIdentifier: {}, jsonString: {}",
                              this.applicationIdentifier, this.environmentIdentifier, this.configurationProfileIdentifier, jsonString);
                    return load(jsonString);
                }
            } catch (Exception e) {
                throw new SourceException("Could not load configuration profile for applicationIdentifier: " + this.applicationIdentifier + 
                                          ", environmentIdentifier: " + this.environmentIdentifier + 
                                          ", configurationProfileIdentifier: " + this.configurationProfileIdentifier, e);
            }
        }

        return emptyValue;
    }

    private T load(String jsonString) throws Exception {
        return objectMapper.readValue(jsonString, typeReference);
    }

    private String initializeConfigurationToken() {
        StartConfigurationSessionRequest request = StartConfigurationSessionRequest.builder()
            .applicationIdentifier(this.applicationIdentifier)
            .environmentIdentifier(this.environmentIdentifier)
            .configurationProfileIdentifier(this.configurationProfileIdentifier)
            .build();

        try {
            log.debug("Initializing configuration token for applicationIdentifier: {}, environmentIdentifier: {}, configurationProfileIdentifier: {}",
                      this.applicationIdentifier, this.environmentIdentifier, this.configurationProfileIdentifier);
            StartConfigurationSessionResponse response = this.appConfigDataClient.startConfigurationSession(request);
            return response.initialConfigurationToken();
        } catch (ResourceNotFoundException e) {
            log.info("Configuration profile not found or is empty for applicationIdentifier: {}, environmentIdentifier: {}, configurationProfileIdentifier: {}",
                     this.applicationIdentifier, this.environmentIdentifier, this.configurationProfileIdentifier);
        } catch (AppConfigDataException e) {
            log.error("Could not initialize configuration token for applicationIdentifier: {}, environmentIdentifier: {}, configurationProfileIdentifier: {}", 
                      this.applicationIdentifier, this.environmentIdentifier, this.configurationProfileIdentifier, e);
        }
        return "";
    }

    private void ensureConfigurationTokenExists() {
        if (this.configurationToken.isEmpty()) {
            this.configurationToken = initializeConfigurationToken();
        }
    }
}
