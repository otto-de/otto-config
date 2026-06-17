# Otto Config

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
![OSS Lifecycle](https://img.shields.io/osslifecycle?file_url=https%3A%2F%2Fgithub.com%2Fotto-de%2Fotto-config%2Fblob%2Fmain%2FOSSMETADATA)

A Java library for dynamic, centralized configuration management using AWS AppConfig, Secrets Manager, Parameter Store, and Hashicorp Vault. Otto Config lets you update feature toggles and application properties across distributed services without redeploying code.

## Table of Contents
- [Features](#features)
- [Quick Start](#quick-start)
- [Framework Integration](#framework-integration)
- [Configuration Sources](#configuration-sources)
- [Documentation](#documentation)
- [Contributing](#contributing)

## Features

- **Fast Setup** — Add the dependency and start using configuration immediately
- **Auto Refresh** — Configuration updates every 5 minutes by default, no restarts required; optional event-driven refresh available for immediate updates (see [AWS Setup Guide](docs/AWS_SETUP.md#event-driven-refresh))
- **Unified API** — Access properties and toggles from multiple sources through one interface
- **Framework Integration** — Auto-registers with Spring Boot and Helidon; works with plain Java too
- **Multiple Sources** — AWS AppConfig, Secrets Manager, Parameter Store, Hashicorp Vault, and local files
- **Type Safe** — Built-in support for String, Boolean, and Integer types

## Quick Start

### 1. Add Dependency

**Gradle:**
```groovy
dependencies {
    implementation "de.otto.config:otto-config:0.1.2"
}
```

**Maven:**
```xml
<dependency>
    <groupId>de.otto.config</groupId>
    <artifactId>otto-config</artifactId>
    <version>0.1.2</version>
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
private Property<Boolean> searchEnabled;
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
private Property<Boolean> searchEnabled;
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

## Configuration Sources

Otto Config supports multiple configuration sources that can be used together or independently.

### Supported Sources

| Source | Properties | Toggles | Use Case |
|--------|:----------:|:-------:|----------|
| **AWS AppConfig** | ✅ | ✅ | Validated configs with deployment controls |
| **AWS Secrets Manager** | ✅ | ❌ | Encrypted secrets (passwords, API keys) |
| **AWS Parameter Store** | ✅ | ❌ | Hierarchical application settings |
| **Hashicorp Vault** | ✅ | ❌ | Enterprise secret management |
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

**AWS AppConfig:**
```properties
# Automatically configured via Terraform module
# See docs/AWS_SETUP.md for details
```

**AWS Secrets Manager:**
```properties
otto.config.aws.secrets.arn=arn:aws:secretsmanager:region:account:secret:your-secret-name
```

**AWS Parameter Store:**
```properties
otto.config.aws.ssm.path.prefix=/your/app/config
```

**Hashicorp Vault:**
```properties
otto.config.hashicorp.vault.url=https://vault.example.com:8200
otto.config.hashicorp.vault.path=/secret/your-app
otto.config.hashicorp.vault.auth.approle.role.id=${VAULT_ROLE_ID}
otto.config.hashicorp.vault.auth.approle.secret.id=${VAULT_SECRET_ID}
```

For detailed AWS setup instructions, IAM permissions, and Terraform examples, see **[docs/AWS_SETUP.md](docs/AWS_SETUP.md)**.

## Documentation

- **[AWS Setup Guide](docs/AWS_SETUP.md)** — Detailed AWS configuration for AppConfig, Secrets Manager, Parameter Store, and Vault
- **[Advanced Topics](docs/ADVANCED.md)** — Architecture diagrams, custom sources, custom providers, priority order
- **[Development Guide](docs/DEVELOPMENT.md)** — Local development setup, VS Code configuration, testing
- **[Contributing](CONTRIBUTING.md)** — How to contribute to the project
- **[Publishing](PUBLISHING.md)** — Release and publishing process

## Examples

Complete working examples are available in the `demo/` directory:
- **[demo/java](demo/java)** — Plain Java application
- **[demo/spring](demo/spring)** — Spring Boot application
- **[demo/helidon](demo/helidon)** — Helidon application

Run examples locally:
```bash
# Plain Java
cd demo/java && ./gradlew run

# Spring Boot
cd demo/spring && ./gradlew bootRun --args='--spring.profiles.active=local'

# Helidon
cd demo/helidon && ./gradlew run -Pmp.config.profile=local
```

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
