package de.otto.config.core.property;


import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Property<T> {
    protected volatile T value;

    public Property(T value) {
        this.value = value;
    }

    public boolean isEmpty() {
        return this.value == null;
    }
}
