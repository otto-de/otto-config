package de.otto.config.core.aws.event;

import de.otto.config.core.source.SourceChangeEvent;

public record SecretsManagerChangeEvent(
        String source,
        String detailType,
        String secretId
) implements SourceChangeEvent {}
