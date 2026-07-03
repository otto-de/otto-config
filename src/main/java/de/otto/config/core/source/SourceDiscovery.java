package de.otto.config.core.source;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
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

        List<SourceFactory> factories = loader.stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());

        // Iterate enabledSources in declaration order so that the returned list
        // is deterministic regardless of the JVM's Method[] ordering (which
        // Class#getMethods() explicitly does not guarantee).
        List<Source<? extends Configuration<?>>> allSources = new ArrayList<>();
        for (String enabledSource : enabledSources) {
            for (SourceFactory factory : factories) {
                allSources.addAll(discoverFromFactory(factory, context, enabledSource));
            }
        }

        return allSources;
    }

    private static List<Source<? extends Configuration<?>>> discoverFromFactory(SourceFactory factory,
                                                                                Context context,
                                                                                String enabledSource) {
        List<Source<? extends Configuration<?>>> sources = new ArrayList<>();
        Method[] methods = factory.getClass().getMethods();

        for (Method method : methods) {
            if (isCreatorMethodFor(method, enabledSource)) {
                try {
                    sources.add((Source<? extends Configuration<?>>) method.invoke(factory, context));
                } catch (Exception e) {
                    log.error("Failed to invoke {}#{}: {}", factory.getClass().getSimpleName(), method.getName(), e.getMessage(), e);
                }
            }
        }

        return sources;
    }

    private static boolean isCreatorMethodFor(Method method, String enabledSource) {
        SourceCreator annotation = method.getAnnotation(SourceCreator.class);

        return annotation != null &&
               method.getParameterCount() == 1 &&
               method.getParameterTypes()[0].equals(Context.class) &&
               enabledSource.equals(annotation.value());
    }

    private static Set<String> getEnabledSources(Context context) {
        String enabledSourcesConfig = context.getConfiguration().getValue("otto.config.sources.enabled", "");

        // LinkedHashSet preserves declaration order from the configuration so
        // downstream ordering (and test expectations) are deterministic.
        return Arrays.stream(enabledSourcesConfig.split(","))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
