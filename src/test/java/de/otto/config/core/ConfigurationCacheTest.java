package de.otto.config.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class ConfigurationCacheTest {

    @Test
    void givenConcurrentRefreshWithIdenticalContent_whenReading_thenReadersNeverSeeMissingKey() throws InterruptedException {
        // Given
        ConfigurationCache<String> cache = new ConfigurationCache<>(Map.of("/svc/config/table", "my-table"));
        Map<String, String> freshValues = Map.of("/svc/config/table", "my-table");
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong misses = new AtomicLong();
        List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread reader = new Thread(() -> {
                while (running.get()) {
                    if (cache.getValue("/svc/config/table") == null) {
                        misses.incrementAndGet();
                    }
                }
            });
            readers.add(reader);
            reader.start();
        }

        // When
        long end = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < end) {
            cache.setProperties(freshValues);
        }
        running.set(false);
        for (Thread reader : readers) {
            reader.join();
        }

        // Then
        assertThat(misses.get(), is(0L));
    }

    @Test
    void givenCachedValues_whenSetPropertiesWithDifferentKeys_thenOldKeysAreRemovedAndNewKeysAdded() {
        // Given
        ConfigurationCache<String> cache = new ConfigurationCache<>(Map.of("old", "1", "kept", "2"));

        // When
        cache.setProperties(Map.of("kept", "3", "new", "4"));

        // Then
        assertThat(cache.getProperties(), aMapWithSize(2));
        assertThat(cache.getProperties(), hasEntry("kept", "3"));
        assertThat(cache.getProperties(), hasEntry("new", "4"));
        assertThat(cache.getValue("old"), is(nullValue()));
    }

    @Test
    void givenCachedValues_whenSetPropertiesWithEmptyMap_thenCacheIsEmpty() {
        // Given
        ConfigurationCache<String> cache = new ConfigurationCache<>(Map.of("key", "value"));

        // When
        cache.setProperties(Map.of());

        // Then
        assertThat(cache.getProperties().isEmpty(), is(true));
    }
}
