package de.otto.config.core.registry;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;

public final class ClientRegistry extends MapRegistry<Class<?>, Object> {

    public static ClientRegistry createDefault() {
        return ClientRegistry.builder()
                             .clients(Map.of(ObjectMapper.class, 
                                             new ObjectMapper())).build();
    }
   
    @Builder
    public ClientRegistry(Map<Class<?>, Object> clients) {
        if (clients != null) {
            this.values.putAll(clients);
        }
    }
}