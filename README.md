# Otto Config

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![Go Version](https://img.shields.io/badge/Go-1.24+-00ADD8.svg)](https://go.dev/)
![OSS Lifecycle](https://img.shields.io/osslifecycle?file_url=https%3A%2F%2Fgithub.com%2Fotto-de%2Fotto-config%2Fblob%2Fmain%2FOSSMETADATA)

A library for dynamic, centralized configuration management using AWS AppConfig, Secrets Manager, Parameter Store, and Hashicorp Vault. Otto Config lets you update feature toggles and application properties across distributed services without redeploying code. Available for both **Java** and **Go**.

## Table of Contents
- [Features](#features)
- [Quick Start](#quick-start)
- [Framework Integration](#framework-integration)
- [Go](#go)
- [Configuration Sources](#configuration-sources)
- [Documentation](#documentation)
- [Contributing](#contributing)

## Features

- **Fast Setup** — Add the dependency and start using configuration immediately
- **Auto Refresh** — Configuration updates every 5 minutes by default, no restarts required; optional event-driven refresh available for immediate updates (see [AWS Setup Guide](docs/AWS_SETUP.md#event-driven-refresh))
- **Unified API** — Access properties and toggles from multiple sources through one interface
- **Framework Integration** — Auto-registers with Spring Boot and Helidon; works with plain Java and Clojure too
- **REST API:** Exposes a REST API for accessing configuration values, enabling non-Java apps (such as frontend applications) to consume centralized configuration without direct Otto Config integration
- **Multiple Sources** — AWS AppConfig, Secrets Manager, Parameter Store, Hashicorp Vault, and local files
- **Type Safe** — Built-in support for String, Boolean, and Integer types

## Quick Start

### 1. Add Dependency

**Gradle:**
```groovy
dependencies {
    implementation "de.otto.config:otto-config:0.1.11"
}
```

**Maven:**
```xml
<dependency>
    <groupId>de.otto.config</groupId>
    <artifactId>otto-config</artifactId>
    <version>0.1.11</version>
</dependency>
```

### 2. Configure Sources

Configure which configuration sources to use via `application.properties` or environment variables. Otto Config supports AWS AppConfig, Secrets Manager, Parameter Store, Hashicorp Vault, and local files.

```properties
# Example: Enable AWS sources for production
otto.config.sources.enabled=aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm
```

See the [Configuration Sources](#configuration-sources) section below for detailed configuration options and the **[AWS Setup Guide](docs/AWS_SETUP.md)** for infrastructure setup including IAM permissions and Terraform examples.

### 3. Use Configuration in Your Code

That's it! Otto Config will automatically discover and integrate with your framework. Access your configuration through standard framework mechanisms or the ConfigurationProvider API.

**For local development and testing**, see the **[Development Guide](docs/DEVELOPMENT.md)** for instructions on running with local configuration files.

## Framework Integration

Otto Config integrates automatically through Java's ServiceLoader (SPI) mechanism. Just add the dependency and it works.

### Spring Boot

Properties are available via standard Spring mechanisms:

```java
@Value("${database.url}")
private String databaseUrl;

@Value("${feature.search.enabled}")
private boolean searchEnabled;

// For auto-refreshing properties
@PropertyValue("feature.search.enabled")
private Property<Boolean> searchEnabledProperty;
```

### Helidon

Use MicroProfile Config:

```java
@Inject
@ConfigProperty(name = "database.url")
private String databaseUrl;

@Inject
@ConfigProperty(name = "feature.search.enabled")
private Boolean searchEnabled;

// For auto-refreshing properties
@Inject
@PropertyValue("feature.search.enabled")
private Property<Boolean> searchEnabledProperty;
```

### Plain Java

Use the ConfigurationProvider directly:

```java
import de.otto.config.core.Context;
import de.otto.config.provider.ConfigurationProvider;
import de.otto.config.source.CoreSourceFactory;

public class MyApplication {
    private final ConfigurationProvider configurationProvider;
    
    public MyApplication() {
        Context context = Context.from("my-application");
        
        this.configurationProvider = ConfigurationProvider.builder()
            .context(context)
            .source(CoreSourceFactory.createPropertiesSource(context))
            .source(CoreSourceFactory.createTogglesSource(context))
            .build();
    }
    
    public void processRequest() {
        String databaseUrl = configurationProvider.getValue("database.url", "jdbc:h2:mem:testdb");
        boolean searchEnabled = configurationProvider.getValueAsBoolean("feature.search.enabled", false);
        
        // Use configuration...
    }
}
```

### Clojure

Use Java interop to access Otto Config:

**Add dependency (deps.edn):**
```clojure
{:deps {de.otto.config/otto-config {:mvn/version "0.1.11"}}}
```

**Or with Leiningen (project.clj):**
```clojure
:dependencies [[de.otto.config/otto-config "0.1.11"]]
```

**Usage:**
```clojure
(ns myapp.config
  (:import [de.otto.config.core Context]
           [de.otto.config.provider ConfigurationProvider]
           [de.otto.config.source CoreSourceFactory]))

(defn create-config-provider []
  (let [context (Context/from "my-application")]
    (-> (ConfigurationProvider/builder)
        (.context context)
        (.source (CoreSourceFactory/createPropertiesSource context))
        (.source (CoreSourceFactory/createTogglesSource context))
        (.build))))

(def config-provider (create-config-provider))

;; Get configuration values
(defn get-database-url []
  (.getValue config-provider "database.url" "jdbc:h2:mem:testdb"))

(defn search-enabled? []
  (.getValueAsBoolean config-provider "feature.search.enabled" false))
```

## Go

Otto Config is also available as a native Go module at [`go/`](go/), with full feature parity: the
same six configuration sources, event-driven refresh, and a REST endpoint -- plus idiomatic Go
additions like struct-tag binding.

> The module lives in the `go/` subdirectory of this repository, so its releases are tagged
> `go/vX.Y.Z` rather than a bare `vX.Y.Z` (see [PUBLISHING.md](PUBLISHING.md#go-module) for why).

### Add the dependency

```bash
go get github.com/otto-de/otto-config/go@v0.1.11
```

### Read configuration directly (no HTTP/gin required)

```go
import (
	ottoconfig "github.com/otto-de/otto-config/go"
	_ "github.com/otto-de/otto-config/go/source/appconfig"
	_ "github.com/otto-de/otto-config/go/source/secretsmanager"
	_ "github.com/otto-de/otto-config/go/source/ssm"
)

func main() {
	ctx, err := ottoconfig.NewContext("my-application")
	if err != nil {
		log.Fatal(err)
	}

	provider := ottoconfig.NewConfigurationProvider(ctx)

	databaseURL := provider.GetValueOr("database.url", "jdbc:h2:mem:testdb")
	searchEnabled := ottoconfig.GetValueAsBool[string](provider, "feature.search.enabled", false)

	// Use configuration...
}
```

Sources are enabled via the same `otto.config.sources.enabled` convention as Java, either through
environment variables (`OTTO_CONFIG_SOURCES_ENABLED=aws.appconfig.properties,aws.secrets,aws.ssm`) or a
local `properties.json` file for development.

### Bind values onto a struct

```go
type AppConfig struct {
	DatabaseURL   string `config:"database.url,default=jdbc:h2:mem:testdb"`
	SearchEnabled bool   `config:"feature.search.enabled,default=false"`
}

var cfg AppConfig
if _, err := bind.Register(ctx, "app-config", &cfg); err != nil {
	log.Fatal(err)
}
// cfg fields are re-populated automatically on every refresh.
```

### Keep configuration fresh in the background

```go
import "github.com/otto-de/otto-config/go/scheduler"

sched := scheduler.StartDefault(ctx) // full refresh every 5m, poll every 10s
defer sched.Stop()
```

### REST endpoint (plain net/http or gin)

```go
handler, err := endpoint.NewHandler(ctx)
if err != nil {
	log.Fatal(err)
}
http.Handle("/", handler.Mux()) // GET /configs, /configs/{key}, /{app}/configs, /{app}/configs/{key}
```

```go
router := gin.Default()
ginconfig.RegisterRoutes(router, ctx)
```

Secret-backed properties (Secrets Manager, Vault, SSM `SecureString`) are always excluded from
endpoint responses, the same as in Java.

### Go package layout

| Source | Package |
|--------|---------|
| **AWS AppConfig** | [`go/source/appconfig`](go/source/appconfig) |
| **AWS Secrets Manager** | [`go/source/secretsmanager`](go/source/secretsmanager) |
| **AWS Parameter Store (SSM)** | [`go/source/ssm`](go/source/ssm) |
| **Hashicorp Vault** | [`go/source/vault`](go/source/vault) |
| **AWS S3 (Toggles)** | [`go/source/s3toggles`](go/source/s3toggles) |
| **Local Files** | [`go/source/file`](go/source/file) |

Blank-import the packages for the sources you want auto-registered
(`_ "github.com/otto-de/otto-config/go/source/appconfig"`), then list them in
`otto.config.sources.enabled` as usual. Infrastructure requirements (IAM permissions, Terraform)
are identical to Java -- see the [AWS Setup Guide](docs/AWS_SETUP.md).

For the Go build/test workflow (running `go build`, `go vet`, `gofmt`, `go test`), see
[Go Development](docs/DEVELOPMENT.md#go-development) in the Development Guide.

See **[go/examples](go/examples)** for complete, runnable programs: a zero-dependency
`quickstart`, a full AWS/Vault-backed `direct` example, and the `plain` net/http and `gin`
REST-endpoint demo servers.

## Configuration Sources

Otto Config supports multiple configuration sources that can be used together or independently.

### Supported Sources

| Source | Properties | Toggles | Use Case |
|--------|:----------:|:-------:|----------|
| **AWS AppConfig** | ✅ | ✅ | Validated configs with deployment controls |
| **AWS Secrets Manager** | ✅ | ❌ | Encrypted secrets (passwords, API keys) |
| **AWS Parameter Store** | ✅ | ❌ | Hierarchical application settings |
| **Hashicorp Vault** | ✅ | ❌ | Enterprise secret management |
| **AWS S3 (Toggles)** | ❌ | ✅ | Feature toggles as S3 objects (no service code) |
| **Local Files** | ✅ | ✅ | Development & testing |

### Enable Sources

Configure which sources to use in `application.properties`:

```properties
# For local development (default)
otto.config.sources.enabled=file

# For production with AWS
otto.config.sources.enabled=aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm

# With Hashicorp Vault
otto.config.sources.enabled=aws.appconfig.properties,hashicorp.vault
```

### AWS Configuration Quick Reference

**AWS AppConfig**

Automatically configured via Terraform module. see see **[docs/AWS_SETUP.md](docs/AWS_SETUP.md)** for more details.

**AWS Secrets Manager:**

Specify one or more secret ARNs in your application properties:

```properties
otto.config.aws.secrets.arn=arn:aws:secretsmanager:region:account:secret:your-secret-name
```

**AWS Parameter Store:**

Specify one or more path prefixes:

```properties
otto.config.aws.ssm.path.prefix=/your/app/config
```

**Hashicorp Vault:**

Configure the Vault URL, application path, and authentication credentials:

```properties
otto.config.hashicorp.vault.url=https://vault.example.com:8200
otto.config.hashicorp.vault.path=/secret/your-app
otto.config.hashicorp.vault.auth.approle.role.id=${VAULT_ROLE_ID}
otto.config.hashicorp.vault.auth.approle.secret.id=${VAULT_SECRET_ID}
```

**AWS S3 (Feature Toggles):**

Specify the bucket and folder that hold the toggle objects:

```properties
otto.config.sources.enabled=aws.s3.toggles
otto.config.aws.s3.toggles.bucket.name=my-service-bucket
otto.config.aws.s3.toggles.folder.name=feature-toggles/
```

For detailed AWS setup instructions, IAM permissions, and Terraform examples, see **[docs/AWS_SETUP.md](docs/AWS_SETUP.md)**.

## Documentation

- **[AWS Setup Guide](docs/AWS_SETUP.md)** — Detailed AWS configuration for AppConfig, Secrets Manager, Parameter Store, and Vault
- **[Advanced Topics](docs/ADVANCED.md)** — Architecture diagrams, custom sources, custom providers, priority order
- **[Development Guide](docs/DEVELOPMENT.md)** — Local development setup, VS Code configuration, testing (Java and [Go](docs/DEVELOPMENT.md#go-development))
- **[Contributing](CONTRIBUTING.md)** — How to contribute to the project
- **[Publishing](PUBLISHING.md)** — Release and publishing process (JReleaser → Maven Central + GitHub Packages; Go module tagging)
- **[Changelog](CHANGELOG.md)** — Release notes per version

## Examples

Complete working examples are available in the `demo/` directory:
- **[demo/java](demo/java)** — Plain Java application
- **[demo/spring](demo/spring)** — Spring Boot application
- **[demo/helidon](demo/helidon)** — Helidon application

Go examples and demos are available in [go/examples](go/examples):
- **[go/examples/quickstart](go/examples/quickstart)** — Simplest possible usage: a local embedded file, no AWS/network
- **[go/examples/direct](go/examples/direct)** — Reading config directly in a long-running app, no HTTP/gin
- **[go/examples/plain](go/examples/plain)** — Plain `net/http` demo server
- **[go/examples/gin](go/examples/gin)** — Gin demo server with the REST configuration endpoint

Run examples locally (from the repo root — the Gradle wrapper only exists there):
```bash
# Plain Java
./gradlew :demo:java:run

# Spring Boot
./gradlew :demo:spring:bootRun --args='--spring.profiles.active=local'

# Helidon
./gradlew :demo:helidon:run -Pmp.config.profile=local
```

To exercise the AWS/Vault code paths without hitting real AWS or the
corporate Vault, use the local docker-compose stack (moto + Vault +
AppConfigData stub) documented in **[demo/local/README.md](demo/local/README.md)**:

```bash
cd demo/local && docker compose up -d
until [ -f .env ]; do sleep 1; done
source ./.env && cd ../..
./gradlew :demo:spring:bootRun --args='--spring.profiles.active=moto'
```

To instead run the Go examples against a **real** AWS account (no code
changes needed — the AWS sources use the standard SDK credential chain), see
[Testing the Go Examples Against Real AWS](docs/AWS_SETUP.md#testing-the-go-examples-against-real-aws).

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Reporting Issues

Found a bug or have a feature request? [Open an issue](https://github.com/otto-de/otto-config/issues).

### Development

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for detailed development setup instructions.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Maintainers

- Joseph Soliday (joseph.soliday@otto.de)
- Pavlo Fedyna (pavlo.fedyna@otto.de)

## Support

- **Documentation**: See the [docs/](docs/) directory
- **Issues**: [GitHub Issues](https://github.com/otto-de/otto-config/issues)
- **Questions**: Open a discussion or contact the maintainers
