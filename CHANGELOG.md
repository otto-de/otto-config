# Release Notes

## 0.1.11
* **[go]**: Add a native Go module ([go/](go/)) porting the library to Go with full feature parity: all six configuration sources (AWS AppConfig, Secrets Manager, Parameter Store, S3 toggles, Hashicorp Vault, local files), event-driven refresh via SQS/EventBridge, a REST configuration endpoint (plain `net/http` and gin integrations), and struct-tag based property binding (`bind` package) as the idiomatic Go equivalent of Java's `@PropertyValue`. See the [Go section](README.md#go) of the README for full Go documentation and usage examples.
* **[go]**: Add runnable Go examples and demos, all under `go/examples/`: a zero-dependency `quickstart`, a full AWS/Vault-backed `direct` demonstrating direct in-process usage without HTTP/gin, and `plain` / `gin` REST-endpoint demo servers.
* **[ci]**: Add a dedicated `go` job (gofmt, vet, build, test) to the `build-main.yml` workflow, and extend the release workflow to tag the Go module `go/vX.Y.Z` alongside each Java release. Go modules aren't published to Maven Central or GitHub Packages; they're consumed directly via `go get` (see [PUBLISHING.md](PUBLISHING.md#go-module)).
* **[ci]**: Extend the `e2e` test harness (`DemoE2ETest`) to also build and launch the `go/examples/plain` binary against the shared docker-compose (moto/vault) stack, asserting the same fixture `/config` payload as the Spring, Helidon, and plain Java demos.

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
