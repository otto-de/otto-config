package de.otto.config.core.aws.event;

import de.otto.config.core.source.SourceChangeEvent;

public record AppConfigDeploymentEvent(
        String source,
        String detailType,
        String applicationId,
        String applicationName,
        String environmentId,
        String environmentName,
        String configProfileId,
        String configProfileName
) implements SourceChangeEvent {}
