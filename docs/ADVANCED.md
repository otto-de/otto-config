# Advanced Topics

This document covers advanced customization and architecture details for Otto Config.

## Table of Contents
- [Architecture](#architecture)
- [Priority Order](#priority-order)
- [Adding Custom Sources](#adding-custom-sources)
- [Implementing a Custom Provider](#implementing-a-custom-provider)

## Architecture

### High-Level Overview

```mermaid
flowchart TD
  A[📱 <b>Your App</b>] --> B[⚙️ <b>Otto Config</b>]
  B --> C[☁️ <b>AWS AppConfig</b>]
  B --> D[🔐 <b>AWS Secrets Manager</b>] 
  B --> E[📝 <b>AWS Parameter Store</b>]
  B --> F[📝 <b>Hashicorp Vault</b>]
  B --> G[📄 <b>Local Files</b>]

  classDef appStyle fill:#e3f2fd,stroke:#1976d2,stroke-width:2px,color:#000
  classDef ottoConfigStyle fill:#e8f5e8,stroke:#388e3c,stroke-width:2px,color:#000
  classDef awsStyle fill:#ffebee,stroke:#d32f2f,stroke-width:2px,color:#000

  class A appStyle
  class B ottoConfigStyle
  class C,D,E,F,G awsStyle
```

### Detailed Component Diagram

```mermaid
flowchart TD

    %% Applications
    subgraph A[📱 Applications]
        A1[🌱 <b>Spring Boot App</b>] -->
        A2[☀️ <b>Helidon App</b>]
    end

    %% Framework Integration
    subgraph B[🔗 Framework Integration]
        B1[<b>SpringPropertySource</b><br/><small>Integrates Otto Config with Spring's config system</small>] --> 
        B2[<b>SpringSchedulerConfiguration</b><br/><small>Triggers periodic config refresh in Spring</small>]  --> 
        B3[<b>HelidonPropertySource</b><br/><small>Integrates Otto Config with Helidon's config system</small>] --> 
        B4[<b>HelidonSchedulerConfiguration</b><br/><small>Triggers periodic config refresh in Helidon</small>]
    end

    %% Core Components
    subgraph C[⚙️ Core Components & Services]
        direction TB
        C1[<b>ConfigurationProvider</b><br/><small>Configuration provider for properties with caching</small>]
        C3[<b>Context</b><br/><small>Framework-agnostic configuration</small>]
        C4[<b>ConfigurationCache</b><br/><small>Cache for all combined and normalized values</small>]
        C5[<b>SourceAggregator</b><br/><small>Combines and normalizes source data</small>]
        C6[<b>ClientRegistry</b><br/><small>Registry for re-usable clients</small>]
        C7[<b>SourceRegistry</b><br/><small>Registry for sources</small>]
        C8[<b>ProviderRegistry</b><br/><small>Registry for providers</small>]
    end

    %% Sources
    subgraph D[📡 Sources]
        D1[<b>AppConfigSource</b><br/><small>AWS AppConfig</small>] --> D2[<b>SecretsManagerSource</b><br/><small>AWS Secrets Manager</small>]  --> D3[<b>SsmSource</b><br/><small>AWS Parameter Store</small>] --> D4[<b>VaultSource</b><br/><small>Hashicorp Vault</small>] --> D5[<b>FileSource</b><br/><small>Local JSON properties.json file</small>]
    end

    %% Internal connections (simplified)
    A -.-> B
    B -.-> C
    C -.-> D

    C1 -.-> C3
    C1 -.-> C4
    C1 -.-> C5

    C3 -.-> C6
    C3 -.-> C7
    C3 -.-> C8

    %% Styling
    classDef subgraphStyle fill:#f9f9f9,stroke:#333,stroke-width:2px,color:#000
    classDef appStyle fill:#e3f2fd,stroke:#1976d2,stroke-width:2px,color:#000
    classDef frameworkStyle fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px,color:#000
    classDef coreStyle fill:#e8f5e8,stroke:#388e3c,stroke-width:2px,color:#000
    classDef sourceStyle fill:#fce4ec,stroke:#c2185b,stroke-width:2px,color:#000
    classDef externalStyle fill:#ffebee,stroke:#d32f2f,stroke-width:2px,color:#000

    class A1,A2 appStyle
    class B1,B2,B3,B4 frameworkStyle
    class C1,C3,C4,C5,C6,C7,C8,C8 coreStyle
    class D1,D2,D3,D4,D5 sourceStyle

    %% Style invisible links
    linkStyle 0 stroke:transparent,stroke-width:0px
    linkStyle 1 stroke:transparent,stroke-width:0px
    linkStyle 2 stroke:transparent,stroke-width:0px
    linkStyle 3 stroke:transparent,stroke-width:0px
    linkStyle 4 stroke:transparent,stroke-width:0px
    linkStyle 5 stroke:transparent,stroke-width:0px
    linkStyle 6 stroke:transparent,stroke-width:0px
```

### Sequence Diagram

```mermaid
flowchart LR
    A[👤 <b>User Request:</b><br/>configurationProvider.getValue] --> B[🔧 <b>ConfigurationProvider</b><br/>delegates to ConfigurationCache]
    B --> C[📊 <b>ConfigurationCache</b><br/>looks up cached values]
    C --> I[✅ <b>Return cached value</b>]
    
    D[🚀 <b>Service Startup</b>] --> E[📋 <b>ConfigurationProvider</b><br/>loads from sources]
    E --> F[🏭 <b>SourceRegistry</b><br/>Load from AWS/Local]
    F --> G[⚙️ <b>SourceAggregator</b><br/>combine + normalize]
    G --> H[💾 <b>Cache values in</b><br/>ConfigurationCache]
    
    J[⏰ <b>Every 5 minutes</b><br/>Auto refresh] -.-> K[🔄 <b>ConfigurationProvider</b><br/>refresh method]
    K -.-> F
    F -.-> G
    
    H -.-> C
    
    style A fill:#e3f2fd,stroke:#1976d2,stroke-width:2px,color:#000
    style B fill:#e8f5e8,stroke:#388e3c,stroke-width:2px,color:#000
    style C fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px,color:#000
    style D fill:#e1f5fe,stroke:#0277bd,stroke-width:2px,color:#000
    style E fill:#e8f5e8,stroke:#388e3c,stroke-width:2px,color:#000
    style F fill:#fff8e1,stroke:#f57c00,stroke-width:2px,color:#000
    style G fill:#ffebee,stroke:#d32f2f,stroke-width:2px,color:#000
    style H fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px,color:#000
    style I fill:#e8f5e8,stroke:#388e3c,stroke-width:2px,color:#000
    style J fill:#e1f5fe,stroke:#0277bd,stroke-width:2px,color:#000
    style K fill:#e8f5e8,stroke:#388e3c,stroke-width:2px,color:#000
```

## Priority Order

Configuration properties are resolved in this order (highest to lowest priority):
1. **System properties** and environment variables
2. **Otto Config sources** (AWS AppConfig, Secrets Manager, Parameter Store)
3. **Local application config** (application.properties, application.yml)  

This enables local overrides via environment variables for development and testing while maintaining production configurations.

## Adding Custom Sources

Otto Config can be extended to support additional configuration sources beyond those provided out of the box. This section describes how to implement and register a custom source—such as one backed by DynamoDB—so that its configuration data is seamlessly integrated into Otto Config's unified configuration system.

### 1. Implement Your Custom Source

Create a class that extends the `PropertySource` abstract class. For example, here's how you might create a custom source that loads properties from DynamoDB:

```java
package com.mycompany.source;

import de.otto.config.domain.Properties;
import de.otto.config.source.PropertySource;

@Slf4j
@Builder
public class DynamoDbSource extends PropertySource {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final String environment;

    @Override
    public Properties load() throws SourceException {
        // Load configuration from DynamoDB
        Map<String, String> properties = new HashMap<>();
        try {
            // ... implementation details
            return new Properties(properties);
        } catch (Exception e) {
            throw new SourceException("Unable to load DynamoDB data.", e);
        }
    }
}
```

### 2. Register Your Source with Otto Config

You can register your custom source in two ways:

#### Option 1: Static Registration (SPI via Factory Class)

1. **Implement a Factory Class**

    Create a class that implements `SourceFactory` and provides a static factory method annotated with `@SourceCreator`. The annotation value is the unique name for your source (e.g., `"dynamodb"`):

    ```java
    package com.mycompany.config;

    import de.otto.config.core.Context;
    import de.otto.config.source.SourceFactory;

    @Slf4j
    public class MySourceFactory implements SourceFactory {

        @SourceCreator("aws.dynamodb")
        public static Source<Properties> createDynamoDbSource(Context context) {
            return DynamoDbSource.builder()
                                 .dynamoDbClient(DynamoDbClient.builder().build())
                                 .tableName("table_name")
                                 .environment(context.getApplicationConfiguration().getValue("environment"))
                                 .build();
        }
    }
    ```

    > **Tip:** To share a client instance (like `DynamoDbClient`) across sources, use the context's client registry `registerIfAbsent` method:

    ```java
    DynamoDbClient dynamoDbClient = context.getClientRegistry().registerIfAbsent(DynamoDbClient.class,
                                                                                 () -> DynamoDbClient.builder().build());
    ```

    > **Tip:** To make your source more flexible for local development and testing, consider checking for a local profile and falling back to a file source (such as `properties.json`) when appropriate using the `CoreSourceFactory` class:

    ```java
    if (CoreSourceFactory.isLocalProfile(context.getProfile())) {
        return CoreSourceFactory.createFileSource(context);
    }
    ```

2. **Register the Factory with SPI**

    Create a file at:

    ```
    resources/META-INF/services/source.core.de.otto.config.SourceFactory
    ```

    Add your factory class's fully qualified name:

    ```
    com.mycompany.config.MySourceFactory
    ```

3. **Enable Your Source in Configuration**

    Add your source's name to the `otto.config.sources.enabled` property:

    ```properties
    otto.config.sources.enabled=aws.appconfig.properties,aws.appconfig.toggles,aws.secrets,aws.ssm,aws.dynamodb
    ```

#### Option 2: Dynamic Registration (At Runtime)

If your source needs runtime parameters or should only be added conditionally, register it dynamically:

```java
// Example: Registering a custom source at runtime
import de.otto.config.core.Context;
import de.otto.config.provider.ConfigurationProvider;

// Inject Context for use in building your source
@Autowired // or @Inject
private Context context;

// Inject ConfigurationProvider
@Autowired // or @Inject
private ConfigurationProvider configurationProvider;

// Register the source with ConfigurationProvider
DynamoDbSource dynamoDbSource = DynamoDbSource.builder()
                                              .dynamoDbClient(DynamoDbClient.builder().build())
                                              .tableName("my-table")
                                              .environment(context.getApplicationConfiguration().getValue("environment"))
                                              .build();
configurationProvider.addSource(dynamoDbSource);
```

With either approach, Otto Config will merge your custom source's data into the unified configuration, making it available alongside all other sources.

## Implementing a Custom Provider

In some cases, you may want to implement your own `Provider<T>` to handle custom data types or specialized configuration needs within your application or service. For instance, you might need to load configuration for internal analytics, auditing, or integration with external systems.

To achieve this, define your own custom type (e.g., `MyCustomType`) and create a new source for it. Instead of implementing `PropertySource`, use the generic `Source<T>` interface with your custom type. For detailed steps on registering your source, refer to [Register Your Source with Otto Config](#2-register-your-source-with-otto-config).

### 1. Create Custom Source for Custom Type

After defining your custom type (e.g., `MyCustomType`), implement a custom source that loads and provides instances of this type:

```java
package com.mycompany.sources;

import de.otto.config.core.Context;
import de.otto.config.source.Source;
import com.mycompany.domain.MyCustomType;
import lombok.Builder;

import java.util.Map;

@Builder
public class MyCustomTypeSource implements Source<MyCustomType> {
  private final Context context;

  @Override
  public Map<String, MyCustomType> load() throws SourceException {
    // Load your custom data here (e.g., from a database, API, or file)
    // Example:
    Map<String, MyCustomType> data = Map.of(
      "exampleKey", new MyCustomType(/* ... */)
    );
    return data;
  }
}
```

Then register the source with Otto Config as normal (see [Register Your Source with Otto Config](#2-register-your-source-with-otto-config)).

### 2. Create Custom Provider

Implement your custom provider by extending the `Provider<T>` abstract class, parameterized with your custom type:

```java
package com.mycompany.providers;

import java.util.Map;
import de.otto.config.core.Context;
import de.otto.config.provider.Provider;
import com.mycompany.domain.MyCustomType;
import lombok.Builder;

public class MyCustomProvider extends Provider<MyCustomType> {

    @Builder
    public MyCustomProvider(Context context) {
        // Pass a transformer function and a list of supported types to the base Provider constructor.
        // This enables your provider to handle values of MyCustomType and expose them via the unified API.
        super(context, List.of(MyCustomType.class), MyCustomType.class::cast, false);
    }
}
```

### 3. Register Your Provider as a Bean (Spring/Helidon)

To use your custom provider in a dependency injection framework like Spring or Helidon, define it as a bean and inject the `Context` into its constructor.

#### Spring Example

```java
import com.mycompany.providers.MyCustomProvider;
import de.otto.config.core.Context;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyCustomProviderConfig {

  @Bean
  public MyCustomProvider myCustomProvider(Context context) {
    return MyCustomProvider.builder()
                           .context(context)
                           .build();
  }
}
```

#### Helidon Example

```java
import com.mycompany.providers.MyCustomProvider;
import de.otto.config.core.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class MyCustomProviderProducer {

  @Produces
  public MyCustomProvider myCustomProvider(Context context) {
    return MyCustomProvider.builder()
                           .context(context)
                           .build();
  }
}
```

You can now inject `MyCustomProvider` wherever needed in your application and use it to access your custom configuration values. For example, to retrieve a value of type `MyCustomType` under the `exampleKey` key:

```java
MyCustomType value = myCustomProvider.getValue("exampleKey");
```

This approach allows you to seamlessly access your custom configuration data throughout your application using your custom provider.
