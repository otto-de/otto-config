package de.otto.config.core.aws.event;

import de.otto.config.core.source.SourceChangeEvent;

public record UnknownChangeEvent(
        String source,
        String detailType
) implements SourceChangeEvent {}
