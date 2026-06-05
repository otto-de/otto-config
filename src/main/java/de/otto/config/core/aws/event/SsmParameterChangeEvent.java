package de.otto.config.core.aws.event;

import de.otto.config.core.source.SourceChangeEvent;

public record SsmParameterChangeEvent(
        String source,
        String detailType,
        String parameterName,
        String operation
) implements SourceChangeEvent {}
