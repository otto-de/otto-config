package de.otto.config.core.registry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class ListRegistry<T> {
    protected final List<T> values = new CopyOnWriteArrayList<>();
    
    public void register(T value) {
        this.values.add(value);
    }

    public void unregister(T key) {
        this.values.remove(key);
    }

    public boolean contains(T value) {
        return this.values.contains(value);
    }

    public List<T> getValues() {
        return this.values;
    }

    public void clear() {
        this.values.clear();
    }
}
