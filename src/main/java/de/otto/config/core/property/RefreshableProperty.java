package de.otto.config.core.property;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.Refreshable;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder
public class RefreshableProperty<T> extends Property<T> implements Refreshable {
    private final @NonNull String key;
    private final @NonNull Class<T> type;
    private final @NonNull Configuration<String> configuration;

    public RefreshableProperty(String key, Class<T> type, Configuration<String> configuration) {
        this.key = key;
        this.type = type;
        this.configuration = configuration;
        this.refresh();
    }

    @Override
    public void refresh() {
        this.value = this.configuration.getValueByType(key, type);
    }

    public static <T> RefreshableProperty<T> register(Context context, String key, Class<T> type) {
        return context.getPropertyRegistry()
                      .registerIfAbsent(key,
                                        () -> RefreshableProperty.<T>builder()
                                                                 .key(key)
                                                                 .type(type)
                                                                 .configuration(context.getConfiguration())
                                                                 .build());
    }
}
