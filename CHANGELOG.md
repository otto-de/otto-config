# Release Notes

## 0.1.14
* **[core]**: Add pluggable metrics via the `de.otto.config.core.metrics.ConfigMetrics` SPI. Reports source requests (`hit`/`miss`/`refresh`), source load duration and outcome (`success`/`empty`/`failure`), HTTP calls of built-in REST clients (e.g. Vault: status and latency), full/poll refresh duration, and received change events. Register an implementation via `META-INF/services/de.otto.config.core.metrics.ConfigMetrics` or `ConfigMetricsRegistry.register(...)`.
* **[core]**: Metrics are opt-in: enable with `otto.config.metrics.enabled=true` (default `false`). When disabled or when no implementation is registered, all calls are no-ops. Exceptions thrown by an implementation are caught and logged and never affect configuration loading; providers that fail to load (e.g. missing Micrometer on the classpath) are skipped with a `WARN`.
* **[core]**: Add `MicrometerConfigMetrics` adapter (Micrometer is a `compileOnly` dependency; not auto-registered). Publishes `otto.config.source.requests`, `otto.config.source.loads`, `otto.config.http.requests`, `otto.config.refresh` and `otto.config.change.events` to `Metrics.globalRegistry`, which Spring Boot exposes via Actuator. See [docs/ADVANCED.md](docs/ADVANCED.md#metrics).
* **[demo]**: Add metrics examples: Spring (Micrometer adapter, `/actuator/metrics` and `/actuator/prometheus`), Helidon (bridge into Helidon's built-in `/metrics`), and plain Java (custom counting implementation).

## 0.1.13
* **[core]**: Add logging to the Hashicorp Vault source. Token generation (AppRole and AWS IAM), token renewal, and secret/metadata reads are logged at `DEBUG`; login and fetch failures are logged at `ERROR`. No tokens, credentials, or secret values are logged. Enable via `logging.level.de.otto.config.client.hashicorp=DEBUG` and `logging.level.de.otto.config.source.hashicorp=DEBUG`.
* **[core]**: Improve HTTP error diagnostics. `RestException` messages now include the request method and URL, elapsed time, the response body (whitespace-collapsed, truncated to 512 chars) and identifying response headers (`server`, `via`, `content-type`, `x-request-id`, `x-amzn-requestid`, `x-amzn-trace-id`, `x-amz-cf-id`), e.g. to tell a load-balancer `502` apart from a Vault error. Request headers (including the Vault token) are never included. Vault read failures now log the stack trace once (in `Source`) instead of three times.

## 0.1.12
* **[core]**: Make the refresh scheduling configurable via `otto.config.refresh.interval` (default `PT5M`, full refresh of all sources) and `otto.config.refresh.poll.interval` (default `PT10S`, change-event polling). Both accept ISO-8601 durations and work for Spring and Helidon. See [docs/ADVANCED.md](docs/ADVANCED.md#refresh-scheduling).

## 0.1.11
* **[core]**: Replace `System.out.println` debug output in `SsmSource` with `log.debug`, avoiding noisy stdout logging in production.

## 0.1.10
* **[core]**: Add a REST API (`SpringConfigurationEndpoint` for Spring, `HelidonConfigurationEndpoint` for Helidon) that exposes configuration values over HTTP (`GET /configs`, `GET /configs/{key}`, `GET /{app}/configs`, `GET /{app}/configs/{key}`), enabling non-Java clients to consume centralized configuration. Disabled by default; enable via `otto.config.endpoint.configs.enabled=true` and optionally expose additional apps via `otto.config.endpoint.configs.apps`. See [docs/ADVANCED.md](docs/ADVANCED.md#-rest-api) for details.
* **[core]**: Secret-backed properties (AWS Secrets Manager, Hashicorp Vault, and SSM `SecureString` parameters) are automatically excluded from REST endpoint responses; they remain fully available to in-process Java code as before.
* **[core]**: Support an app-specific SSM path prefix override (`<appName>.otto.config.aws.ssm.path.prefix`), used when exposing other applications' configuration through the REST endpoint.

## 0.1.9
* **[core]**: Fix nondeterministic source ordering in `SourceDiscovery`. Sources are now returned in the order declared in `otto.config.sources.enabled`, matching the user's intent and no longer depending on JVM-specific `Class#getMethods()` ordering.
* **[build]**: Migrate release pipeline to JReleaser targeting the new Maven Central Portal (`central.sonatype.com`).
* **[ci]**: Run the build workflow on pushes to `feat/**`, `fix/**`, and `chore/**` branches.

## 0.1.7
* Previous release (see git history)

## 0.1.6
* Previous release (see git history)

## 0.1.4
* Previous release (see git history)

## 0.1.3
* Previous release (see git history)

## 0.1.1
* Previous release (see git history)

## 0.1.0
* Initial release
