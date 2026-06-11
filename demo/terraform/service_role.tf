# ---------------------------------------------------------------------------
# Example service role for a Otto Config based application
#
# In a real project this role would already exist (created by your platform /
# infrastructure team).  It is shown here as a complete example so you can see
# which AWS permissions are required.
#
# Responsibilities:
#   1. Trust policy   – allows ECS tasks (or EC2 instances running your JVM) to
#                       assume the role.
#   2. Otto Config policy  – minimum read permissions for every AWS source type that
#                       Otto Config supports: AppConfig, Secrets Manager, and SSM.
#   3. Vault auth     – sts:GetCallerIdentity so that VaultAwsAuthenticator can
#                       prove its identity to the HashiCorp Vault AWS auth backend.
#   4. SQS consumer   – automatically attached by the reusable Otto Config Terraform
#                       module (terraform/eventbridge.tf) when
#                       change_notification_enabled = true.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Trust policy – allow ECS tasks to assume this role
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "service_role_trust" {
  statement {
    sid     = "AllowEcsTasksToAssumeRole"
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ---------------------------------------------------------------------------
# IAM role
# ---------------------------------------------------------------------------
resource "aws_iam_role" "service_role" {
  name               = "${var.service}-service-role"
  assume_role_policy = data.aws_iam_policy_document.service_role_trust.json
  description        = "Runtime role for the ${var.service} application (Otto-Config-managed config sources)"
}

# ---------------------------------------------------------------------------
# Otto Config source permissions
#
# Grants the minimum set of actions required by Otto Config's AWS source
# implementations:
#   - AppConfigSource       → appconfig + appconfigdata APIs
#   - SecretsManagerSource  → secretsmanager:GetSecretValue
#   - SsmSource             → ssm:GetParameter(s) / ssm:GetParametersByPath
#   - VaultAwsAuthenticator → sts:GetCallerIdentity (does not call STS itself;
#                             the Vault server calls back with this identity)
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "otto_config_sources" {
  # AppConfig – start a session and poll for configuration updates
  statement {
    sid    = "AppConfigRead"
    effect = "Allow"
    actions = [
      "appconfig:GetConfiguration",
      "appconfig:GetLatestConfiguration",
      "appconfig:StartConfigurationSession",
      "appconfig:ListApplications",
      "appconfig:ListEnvironments",
      "appconfig:ListConfigurationProfiles",
    ]
    resources = ["*"]
  }

  # Secrets Manager – read current and previous secret version
  statement {
    sid    = "SecretsManagerRead"
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
      "secretsmanager:ListSecretVersionIds",
    ]
    resources = ["*"]
  }

  # SSM Parameter Store – read parameters under the service path
  statement {
    sid    = "SsmParameterRead"
    effect = "Allow"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:DescribeParameters",
    ]
    resources = ["*"]
  }

  # Vault AWS auth – Vault calls back to STS to verify the caller identity
  statement {
    sid     = "VaultAwsAuth"
    effect  = "Allow"
    actions = ["sts:GetCallerIdentity"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "otto_config_sources" {
  name        = "${var.service}-otto-config-sources"
  description = "Minimum permissions required by all Otto Config AWS configuration sources for ${var.service}"
  policy      = data.aws_iam_policy_document.otto_config_sources.json
}

resource "aws_iam_role_policy_attachment" "otto_config_sources" {
  role       = aws_iam_role.service_role.name
  policy_arn = aws_iam_policy.otto_config_sources.arn
}
