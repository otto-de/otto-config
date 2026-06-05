package de.otto.config.core.registry;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public abstract class MapRegistry<K, V> {
    protected final Map<K, V> values = new ConcurrentHashMap<>();

    public <T> T get(Class<T> type) {
        return type.cast(values.get(type));
    }
    
    public void register(K key, V value) {
        this.values.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerIfAbsent(K key, Supplier<T> valueSupplier) {
        if (this.values.containsKey(key)) {
            return (T) this.values.get(key);
        }

        T newValue = valueSupplier.get();
        this.values.put(key, (V) newValue);
        return newValue;
    }

    public void unregister(K key) {
        this.values.remove(key);
    }

    public boolean contains(K key) {
        return this.values.containsKey(key);
    }

    public Collection<V> getValues() {
        return this.values.values();
    }

    public void clear() {
        this.values.clear();
    }
}
