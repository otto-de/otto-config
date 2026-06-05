package de.otto.config.core.source;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class SourceDiscovery {
    
    public static List<Source<? extends Configuration<?>>> discover(Context context) {
        ServiceLoader<SourceFactory> loader = ServiceLoader.load(SourceFactory.class);
        Set<String> enabledSources = getEnabledSources(context);
        
        List<Source<? extends Configuration<?>>> allSources = loader.stream()
                .map(ServiceLoader.Provider::get)
                .flatMap(factory -> discoverFromFactory(factory, context, enabledSources).stream())
                .collect(Collectors.toList());
        
        return allSources;
    }
    
    private static List<Source<? extends Configuration<?>>> discoverFromFactory(SourceFactory factory, 
                                                                                Context context, 
                                                                                Set<String> enabledSources) {
        List<Source<? extends Configuration<?>>> sources = new ArrayList<>();
        Method[] methods = factory.getClass().getMethods();
        
        for (Method method : methods) {
            if (isValidCreatorMethod(method, enabledSources)) {
                try {
                    sources.add((Source<? extends Configuration<?>>) method.invoke(factory, context));
                } catch (Exception e) {
                    log.error("Failed to invoke {}#{}: {}", factory.getClass().getSimpleName(), method.getName(), e.getMessage(), e);
                }
            } 
        }
        
        return sources;
    }

    private static boolean isValidCreatorMethod(Method method, Set<String> enabledSources) {
        SourceCreator annotation = method.getAnnotation(SourceCreator.class);

        return annotation != null && 
               method.getParameterCount() == 1 && 
               method.getParameterTypes()[0].equals(Context.class) &&
               enabledSources.contains(annotation.value());
    }
    
    private static Set<String> getEnabledSources(Context context) {
        String enabledSourcesConfig = context.getConfiguration().getValue("otto.config.sources.enabled", "");

        Set<String> enabled = Arrays.stream(enabledSourcesConfig.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toSet());
        
        return enabled;
    }
}
