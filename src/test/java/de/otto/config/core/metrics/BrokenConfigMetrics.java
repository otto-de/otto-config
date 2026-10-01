package de.otto.config.core.metrics;

/**
 * Test-only {@link ConfigMetrics} implementation that fails to construct, simulating a real-world scenario
 * such as {@code MicrometerConfigMetrics} being registered via {@code META-INF/services} while Micrometer is
 * missing from the classpath (which manifests as a {@link NoClassDefFoundError} when the no-arg constructor
 * is invoked). Discovered alongside {@link DiscoverableTestConfigMetrics} to verify that
 * {@link ConfigMetricsRegistry} skips this broken provider and still picks up the working one.
 */
public class BrokenConfigMetrics implements ConfigMetrics {
    public BrokenConfigMetrics() {
        throw new NoClassDefFoundError("Simulated: a required runtime dependency is missing from the classpath");
    }
}
