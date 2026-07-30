# Development Guide

This guide provides detailed instructions for setting up your development environment to work on Otto Config.

This repository contains two independent implementations of otto-config: the **Java** library
(root Gradle project + `demo/java`, `demo/spring`, `demo/helidon`) and the **Go** module (`go/`,
with its own `go/examples`). This guide covers Java first; see
[Go Development](#go-development) below for the Go-specific workflow.

## Prerequisites

- **Java 21** or later
- **Gradle** (wrapper included)
- **Docker** (optional, for local Vault testing)
- **AWS CLI** configured (for AWS integration testing)
- **Git**

## Building the Project

```bash
# Clone the repository
git clone https://github.com/otto-de/otto-config.git
cd otto-config

# Build the project
./gradlew clean build

# Run tests
./gradlew test

# Generate JavaDoc
./gradlew javadoc
```

## Running Locally for Testing

When developing or testing Otto Config locally, you can use file-based configuration sources instead of connecting to AWS or Vault.

### 1. Create Local Configuration Files

Create `src/main/resources/properties.json` in your application or demo project:

```json
{
  "properties": {
    "database.url": "jdbc:h2:mem:testdb",
    "feature.search.enabled": "true",
    "cache.timeout": "3600"
  },
  "toggles": {
    "new_feature": { "enabled": true },
    "beta_ui": { "enabled": false }
  }
}
```

### 2. Set Profile for Local Development

Configure your application to use the local profile:

**Spring Boot:**
```bash
--spring.profiles.active=local
```

**Helidon:**
```bash
-Dmp.config.profile=local
```

**Plain Java:**
```java
System.setProperty("otto.config.profile", "local");
```

**Via Environment Variable:**
```bash
export OTTO_CONFIG_SOURCES_ENABLED=file
```

### 3. Run Your Application

With the local profile set, Otto Config will automatically load configuration from your local `properties.json` file. This allows you to test configuration changes without connecting to external services.

## Running Demo Projects

Otto Config includes three demo projects showcasing integration with different frameworks:

### 1. Plain Java Demo

```bash
cd demo/java
./gradlew run
```

### 2. Spring Boot Demo

```bash
cd demo/spring
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 3. Helidon Demo

```bash
cd demo/helidon
./gradlew run -Pmp.config.profile=local
```

## Local Development with Vault

For testing Hashicorp Vault integration locally:

### Start Local Vault Server

```bash
# From project root
./demo/ci/start_vault.sh
```

This script:
- Starts Vault in dev mode on `http://localhost:8200`
- Configures AppRole authentication
- Sets up test secrets
- Outputs the `VAULT_ROLE_ID` and `VAULT_SECRET_ID` for use in your application

### Stop Local Vault Server

```bash
./demo/ci/stop_vault.sh
```

## Go Development

The Go module lives entirely in [go/](../go/) with its own `go.mod`. See the [Go section](../README.md#go)
of the main README for full Go usage documentation; this section covers the build/test workflow only.

### Prerequisites

- **Go 1.24+** (the module pins `toolchain go1.24.6` in `go.mod`; the `go` command downloads it
  automatically if your installed version is older)

### Build & Test

```bash
cd go

go build ./...
go vet ./...
gofmt -l .      # should print nothing; run `gofmt -w .` to fix
go test ./...
```

### Running Go Demos & Examples

```bash
# Simplest possible usage: local embedded file, no AWS/network
go run ./examples/quickstart

# Plain net/http demo server
go run ./examples/plain

# Gin demo server with the REST configuration endpoint
go run ./examples/gin
```

`examples/direct` and `examples/gin` exercise the full AWS/Vault source stack and expect the
`demo/local` docker-compose stack (moto + Vault + AppConfigData stub) to be running, the same as
the Java `moto` profile (run from the repository root, then return to `go/` to run the example):

```bash
cd ../demo/local && docker compose up -d
until [ -f .env ]; do sleep 1; done
source ./.env && cd ../../go
go run ./examples/direct
```

## VS Code Development Setup

### tasks.json

Create `.vscode/tasks.json` to start Vault automatically:

```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "vault",
            "type": "shell",
            "command": "${workspaceFolder}/demo/ci/start_vault.sh",
            "problemMatcher": []
        }
    ]
}
```

### launch.json

Create `.vscode/launch.json` for debugging demo applications:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Java Demo",
            "request": "launch",
            "mainClass": "de.otto.config.demo.Main",
            "projectName": "java",
            "env": {
                "OTTO_CONFIG_HASHICORP_VAULT_AUTH_TYPE": "approle",
                "OTTO_CONFIG_HASHICORP_VAULT_URL": "http://localhost:8200",
                "OTTO_CONFIG_SOURCES_ENABLED": "file"
            },
            "preLaunchTask": "vault"
        },
        {
            "type": "java",
            "name": "Spring Boot Demo",
            "request": "launch",
            "mainClass": "de.otto.config.demo.DemoApplication",
            "projectName": "spring",
            "args": [
                "--spring.profiles.active=local"
            ],
            "env": {
                "OTTO_CONFIG_HASHICORP_VAULT_AUTH_TYPE": "approle",
                "OTTO_CONFIG_HASHICORP_VAULT_URL": "http://localhost:8200",
                "OTTO_CONFIG_SOURCES_ENABLED": "file"
            },
            "preLaunchTask": "vault"
        },
        {
            "type": "java",
            "name": "Helidon Demo",
            "request": "launch",
            "mainClass": "io.helidon.microprofile.cdi.Main",
            "projectName": "helidon",
            "vmArgs": "-Dmp.config.profile=local",
            "env": {
                "OTTO_CONFIG_HASHICORP_VAULT_AUTH_TYPE": "approle",
                "OTTO_CONFIG_HASHICORP_VAULT_URL": "http://localhost:8200",
                "OTTO_CONFIG_SOURCES_ENABLED": "file"
            },
            "preLaunchTask": "vault"
        }
    ]
}
```

### Environment Variables

Create a `.env` file in the project root (add to `.gitignore`):

```bash
# For local Vault testing (get these values from start_vault.sh output)
VAULT_ROLE_ID=your-role-id-here
VAULT_SECRET_ID=your-secret-id-here

# AWS Configuration (for AWS integration testing)
# AWS_PROFILE=your-profile-name
# AWS_REGION=us-east-1

# Optional: AWS resource ARNs for testing
# OTTO_CONFIG_AWS_SECRETS_ARN=arn:aws:secretsmanager:...
# OTTO_CONFIG_AWS_SSM_PATH_PREFIX=/your/path
```

## Testing with AWS Services

### Local Testing (No AWS Required)

Use the built-in file-based configuration:

1. Create `src/main/resources/properties.json` in your demo project
2. Set profile to `local` or `test`
3. Run the application

```json
{
  "properties": {
    "database.url": "jdbc:h2:mem:testdb",
    "feature.enabled": "true"
  },
  "toggles": {
    "new_feature": { "enabled": true }
  }
}
```

### AWS Integration Testing

To test with real AWS services:

1. Configure AWS credentials:
   ```bash
   aws configure
   ```

2. Deploy test infrastructure:
   ```bash
   cd demo/terraform
   terraform init
   terraform apply
   ```

3. Update your environment variables with the created resource ARNs

4. Run the demo with AWS sources enabled:
   ```bash
   OTTO_CONFIG_SOURCES_ENABLED=aws.appconfig.properties,aws.secrets,aws.ssm ./gradlew bootRun
   ```

## Project Structure

```
otto-config/
├── src/main/java/de/otto/config/
│   ├── core/              # Core interfaces and implementations
│   ├── integration/       # Framework integrations (Spring, Helidon)
│   └── source/           # Configuration source implementations
├── src/test/java/        # Unit and integration tests
├── demo/
│   ├── java/            # Plain Java demo
│   ├── spring/          # Spring Boot demo
│   ├── helidon/         # Helidon demo
│   └── terraform/       # Test infrastructure
├── docs/                # Documentation
└── gradle/              # Gradle configuration
```

## Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :demo:spring:test

# Run with coverage
./gradlew test jacocoTestReport

# Run integration tests (requires AWS credentials)
./gradlew integrationTest
```

## Code Style

The project uses:
- **Lombok** for reducing boilerplate
- **SLF4J** for logging
- **Standard Java naming conventions**

Run the formatter before committing:
```bash
./gradlew spotlessApply
```

## Debugging

### Enable Debug Logging

Add to `application.properties` or `logback.xml`:

```properties
logging.level.de.otto.config=DEBUG
```

### Common Issues

**Issue**: Vault connection failed
- **Solution**: Ensure Vault is running (`./demo/ci/start_vault.sh`)

**Issue**: AWS credentials not found
- **Solution**: Run `aws configure` or set `AWS_PROFILE` environment variable

**Issue**: Tests failing with SourceException
- **Solution**: Check that local profile is active or test resources exist

**Issue**: VS Code - Application properties not loading (resources not found)
- **Root Cause**: VS Code's Java extension uses the `bin/` directory (Eclipse-style) for compiled classes and resources, while Gradle uses `build/`. When debugging from VS Code, the application can't find resources because they're only in `build/resources/` and not in `bin/`.
- **Solution**: The project includes Gradle tasks (`syncResourcesToVSCode` and `syncTestResourcesToVSCode`) that automatically copy resources from `build/resources/` to `bin/` after processing. These run automatically when you build or run `./gradlew processResources`.
- **Manual Trigger**: If resources are stale, run `./gradlew processResources processTestResources`
- **Note**: This issue is specific to VS Code's Java extension and does not occur in IntelliJ IDEA or production environments

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for contribution guidelines.

## Questions?

- Open an [issue](https://github.com/otto-de/otto-config/issues)
- Contact the maintainers (see [MAINTAINERS](../MAINTAINERS))
