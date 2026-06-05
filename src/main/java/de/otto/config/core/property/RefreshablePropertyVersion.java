package de.otto.config.core.property;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.Refreshable;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder
public class RefreshablePropertyVersion extends PropertyVersion implements Refreshable {
    private final @NonNull String key;
    private final @NonNull Configuration<String> configuration;

    public RefreshablePropertyVersion(String key, Configuration<String> configuration) {
        super();
        this.key = key;
        this.configuration = configuration;
        this.refresh();
    }

    @Override
    public void refresh() {
        this.setVersions(this.configuration.getValues(key, String.class));
    }

    public static RefreshablePropertyVersion register(Context context, String key) {
        return context.getPropertyRegistry()
                      .registerIfAbsent(key,
                                        () -> RefreshablePropertyVersion.builder()
                                                                        .key(key)
                                                                        .configuration(context.getConfiguration())
                                                                        .build());
    }
}
