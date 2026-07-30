# AWS Configuration Guide

This guide provides detailed instructions for configuring Otto Config with AWS services.

## Table of Contents
- [AWS AppConfig](#aws-appconfig)
- [AWS Secrets Manager](#aws-secrets-manager)
- [AWS Parameter Store (SSM)](#aws-parameter-store-ssm)
- [Hashicorp Vault](#hashicorp-vault)
- [AWS S3 (Feature Toggles)](#aws-s3-feature-toggles)
- [Event-Driven Refresh](#event-driven-refresh)
- [Testing the Go Examples Against Real AWS](#testing-the-go-examples-against-real-aws)

## AWS AppConfig

AWS AppConfig provides validated configuration deployment with rollback capabilities.

### Terraform Setup

Use the provided Terraform module to provision AppConfig resources:

```terraform
module "appconfig_service" {
  source = "https://github.com/otto-de/otto-config.git//terraform"
  
  service = "my-service"
  
  toggles_configuration_content = file("${path.module}/appconfig_toggles.json") 
  properties_configuration_content = file("${path.module}/appconfig_properties.json")

  # Optional: enable event-driven refresh via EventBridge → SQS
  change_notification_enabled           = true
}
```

### Configuration Files

#### Properties File (`appconfig_properties.json`)

```json
{
  "properties": {
    "database.url": "jdbc:postgresql://prod-db:5432/myapp",
    "database.pool.size": "20",
    "feature.search.enabled": "true",
    "cache.timeout": "3600",
    "api.endpoint": "https://api.example.com"
  }
}
```

#### Toggles File (`appconfig_toggles.json`)

```json
{
  "flags": {
    "new_feature": { "name": "NEW_FEATURE" },
    "logging_enabled": { "name": "LOGGING_ENABLED" },
    "beta_ui": { "name": "BETA_UI" }
  },
  "values": {
    "new_feature": { "enabled": true },
    "logging_enabled": { "enabled": false },
    "beta_ui": { "enabled": false }
  },
  "version": "1"
}
```

### Application Configuration

Enable AppConfig sources in your `application.properties`:

```properties
otto.config.sources.enabled=aws.appconfig.properties,aws.appconfig.toggles
```

### Required IAM Permissions

```yaml
Version: '2012-10-17'
Statement:
  - Effect: Allow
    Action:
      - appconfig:StartConfigurationSession
      - appconfig:GetLatestConfiguration
    Resource: "*"
```

### Best Practices

- Use **deployment strategies** to gradually roll out changes
- Set up **validators** to prevent invalid configurations from being deployed
- Use **environments** (dev, staging, prod) to separate configurations
- Monitor deployment status and rollback if issues occur

## AWS Secrets Manager

AWS Secrets Manager stores encrypted secrets with automatic rotation support.

### Configuration

Specify one or more secret ARNs in your application properties:

```properties
# Single secret
otto.config.aws.secrets.arn=arn:aws:secretsmanager:us-east-1:123456789012:secret:my-app-secrets

# Multiple secrets (comma-separated)
otto.config.aws.secrets.arn=arn:aws:secretsmanager:us-east-1:123456789012:secret:app-secrets,arn:aws:secretsmanager:us-east-1:123456789012:secret:db-credentials
```

Enable the source:

```properties
otto.config.sources.enabled=aws.secrets
```

### Secret Structure

Secrets should be stored as JSON key-value pairs:

```json
{
  "database.password": "supersecret123",
  "api.key": "abc123xyz789",
  "jwt.secret": "jwt-signing-key"
}
```

### Versioning

Otto Config supports accessing previous versions of secrets:

```java
// Current version
String currentPassword = configurationProvider.getValue("database.password");

// Previous version (for rotation scenarios)
@PropertyValue("database.password")
private PropertyVersion databasePassword;

String current = databasePassword.getCurrent().get();
String previous = databasePassword.getPrevious().get();
```

### Required IAM Permissions

```yaml
Version: '2012-10-17'
Statement:
  - Effect: Allow
    Action:
      - secretsmanager:GetSecretValue
      - secretsmanager:ListSecrets
      - secretsmanager:DescribeSecret
      - secretsmanager:BatchGetSecretValue
    Resource: "*"
```

### Best Practices

- **Never hardcode secrets** in your application code
- Use **automatic rotation** for database credentials
- Enable **versioning** to support credential rotation without downtime
- Use **resource-based policies** to restrict access
- Monitor secret access via CloudTrail

## AWS Parameter Store (SSM)

AWS Systems Manager Parameter Store provides hierarchical configuration storage.

### Configuration

Specify one or more path prefixes:

```properties
# Single path
otto.config.aws.ssm.path.prefix=/my-app/config

# Multiple paths (comma-separated)
otto.config.aws.ssm.path.prefix=/my-app/config,/shared/config

# Load all accessible parameters (not recommended for production)
# otto.config.aws.ssm.path.prefix=
```

Enable the source:

```properties
otto.config.sources.enabled=aws.ssm
```

### Parameter Naming

Use hierarchical paths for organization:

```
/my-app/
  /config/
    /database/
      url
      pool-size
    /api/
      endpoint
      timeout
  /features/
    search-enabled
    beta-ui
```

Otto Config normalizes parameter names:
- `/my-app/config/database/url` → `database.url`
- Both formats can be used to access the value

### Required IAM Permissions

```yaml
Version: '2012-10-17'
Statement:
  - Effect: Allow
    Action:
      - ssm:GetParametersByPath
      - ssm:GetParameter
      - ssm:GetParameters
    Resource: 
      - "arn:aws:ssm:*:*:parameter/my-app/*"
```

### Best Practices

- Use **hierarchical paths** for organization
- Apply **least privilege** IAM policies with specific path restrictions
- Use **SecureString** type for sensitive data
- Tag parameters for cost tracking and governance
- Use **parameter policies** for expiration and change notifications

## Hashicorp Vault

Hashicorp Vault provides enterprise-grade secret management.

### Configuration

```properties
# Vault server URL
otto.config.hashicorp.vault.url=https://vault.example.com:8200

# Secret paths (comma-separated)
otto.config.hashicorp.vault.path=/secret/my-app,/secret/shared

# Number of previous versions to load (default: 1)
otto.config.hashicorp.vault.prev.versions=2
```

Enable the source:

```properties
otto.config.sources.enabled=hashicorp.vault
```

### Authentication Methods

#### AppRole Authentication (Default)

```properties
otto.config.hashicorp.vault.auth.approle.role.id=${VAULT_ROLE_ID}
otto.config.hashicorp.vault.auth.approle.secret.id=${VAULT_SECRET_ID}
```

**Always use environment variables for credentials, never hardcode them.**

#### AWS IAM Authentication

```properties
otto.config.hashicorp.vault.auth.type=aws
otto.config.hashicorp.vault.auth.aws.region=${AWS_REGION}
otto.config.hashicorp.vault.auth.aws.role.name=my-app-role
otto.config.hashicorp.vault.auth.aws.role.arn=${AWS_ROLE_ARN}
otto.config.hashicorp.vault.auth.aws.header.value=vault.example.com
```

### Versioning

Vault secrets support versioning. Access previous versions:

```java
@PropertyValue("database.password")
private PropertyVersion password;

String current = password.getCurrent().get();
String previous = password.getPrevious().get();
List<String> allVersions = password.getVersions();
```

### Best Practices

- Use **namespaces** to isolate different applications
- Enable **audit logging** to track secret access
- Configure **lease durations** appropriate for your use case
- Use **dynamic secrets** for database credentials when possible
- Implement **secret rotation** policies

## AWS S3 (Feature Toggles)

Read feature toggles from objects in an S3 bucket. The **toggle state is encoded
in the object name** and the object content is never read:

| Object key                                   | Resulting toggle               |
|----------------------------------------------|--------------------------------|
| `feature-toggles/on.my-feature`              | `my-feature` = `true`          |
| `feature-toggles/off.my-feature`             | `my-feature` = `false`         |
| `feature-toggles/on.team.my-feature`         | `team.my-feature` = `true`     |

The `on.`/`off.` prefix is case-insensitive. The toggle name is the object name
after the first dot, used verbatim (no aliasing). Objects under the prefix that
start with neither `on.` nor `off.` are ignored.

### Enable the source

```properties
otto.config.sources.enabled=aws.s3.toggles
otto.config.aws.s3.toggles.bucket.name=my-service-bucket
otto.config.aws.s3.toggles.folder.name=feature-toggles/
```

In local/test profiles the source falls back to the `toggles` section of
`properties.json`, like the other sources.

### IAM permissions

Only listing is required — the source never reads object content:

```yaml
- Effect: Allow
  Action:
    - s3:ListBucket
  Resource: "arn:aws:s3:::my-service-bucket"
```

## Event-Driven Refresh

Instead of polling every 5 minutes, Otto Config can receive immediate notifications when configuration changes.

### How It Works

1. EventBridge rules detect changes in AppConfig, Secrets Manager, and Parameter Store
2. Events are sent to an SQS queue
3. Otto Config polls the queue and refreshes configuration immediately

### Setup

#### 1. Enable in Terraform

```terraform
module "appconfig_service" {
  source = "https://github.com/otto-de/otto-config.git//terraform"
  
  service = "my-service"
  
  # ... other configuration ...
  
  change_notification_enabled           = true
}
```

This creates:
- SQS queue for change notifications
- EventBridge rules for AppConfig, Secrets Manager, and SSM changes
- Required IAM permissions

#### 2. Configure Application

Use the `change_notification_queue_url` output from Terraform:

```properties
otto.config.aws.change.notifications.enabled=true
otto.config.aws.change.notifications.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/otto-config-changes
```

**Go:** the same properties apply, plus starting a listener explicitly:

```go
import "github.com/otto-de/otto-config/go/event"

listener := event.NewSQSListener(ctx, queueURL)
go listener.Start(context.Background())
```

### Required IAM Permissions

```yaml
Version: '2012-10-17'
Statement:
  - Effect: Allow
    Action:
      - sqs:ReceiveMessage
      - sqs:DeleteMessage
      - sqs:GetQueueAttributes
    Resource: "arn:aws:sqs:*:*:otto-config-changes"
```

## Testing the Go Examples Against Real AWS

The Go examples under [`go/examples`](../go/examples) default to the local
moto/Vault docker-compose stack via `demo/local/.env`. To test against a
real account instead, don't source that file (so `AWS_ENDPOINT_URL` stays
unset), make sure real credentials are resolvable (`AWS_PROFILE`, static env
vars, or an instance/task role), and run e.g.:

```bash
cd go
AWS_REGION=eu-central-1 AWS_PROFILE=my-real-profile go run ./examples/plain
```

No code changes are needed — the AWS sources use the SDK's standard
`config.LoadDefaultConfig()` chain. Point the example's seed properties
(secret ARN, SSM path prefix, S3 bucket/folder) at resources provisioned per
the sections above.

### Benefits

- **Immediate updates**: Configuration changes take effect within seconds, not minutes
- **Reduced API calls**: Less frequent polling of AWS services
- **Cost savings**: Fewer API requests to AWS services
- **Better responsiveness**: Critical configuration changes propagate faster

### Monitoring

Monitor the SQS queue:
- **ApproximateNumberOfMessages**: Should stay near zero if consumption is healthy
- **ApproximateAgeOfOldestMessage**: Should be low (< 30 seconds)
- **NumberOfMessagesSent**: Indicates configuration change frequency

## Multi-Region Considerations

When running in multiple AWS regions:

1. **AppConfig**: Create applications in each region
2. **Secrets Manager**: Replicate secrets across regions
3. **Parameter Store**: Copy parameters to each region
4. **Event Notifications**: Set up SQS queues in each region

## Security Best Practices

1. **Use IAM roles** instead of access keys whenever possible
2. **Apply least privilege** - grant only necessary permissions
3. **Enable encryption** for Secrets Manager and Parameter Store
4. **Use VPC endpoints** to keep traffic within AWS network
5. **Enable CloudTrail** logging for audit trails
6. **Rotate credentials** regularly
7. **Use resource policies** to restrict access
8. **Tag resources** for governance and cost tracking

## Cost Optimization

- **AppConfig**: Charged per configuration request
- **Secrets Manager**: $0.40/secret/month + $0.05/10,000 API calls
- **Parameter Store**: Standard parameters are free, advanced parameters are $0.05/parameter/month
- **SQS**: First 1M requests/month free

Tips:
- Use **Parameter Store standard parameters** for non-sensitive config (free)
- Set appropriate **refresh intervals** (default 5 minutes is reasonable)
- Use **event-driven refresh** to reduce polling frequency
- Share secrets across applications when appropriate

## Troubleshooting

### Configuration Not Loading

1. Check IAM permissions
2. Verify resource ARNs/paths are correct
3. Enable DEBUG logging: `logging.level.de.otto.config=DEBUG`
4. Check CloudWatch Logs for error messages

### Slow Configuration Refresh

1. Check network latency to AWS services
2. Consider using VPC endpoints
3. Enable event-driven refresh for immediate updates
4. Review the number of configuration sources

### Authentication Failures

1. Verify AWS credentials are configured
2. Check IAM role/user has necessary permissions
3. For Vault, ensure role_id and secret_id are valid
4. Check security group rules for Vault connectivity

## Additional Resources

- [AWS AppConfig Documentation](https://docs.aws.amazon.com/appconfig/)
- [AWS Secrets Manager Documentation](https://docs.aws.amazon.com/secretsmanager/)
- [AWS Parameter Store Documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html)
- [Hashicorp Vault Documentation](https://www.vaultproject.io/docs)
