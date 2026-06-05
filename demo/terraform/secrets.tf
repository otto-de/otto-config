resource "random_password" "aes_key" {
  length  = 64
  special = true
  upper   = true
  lower   = true
  keepers = {
    timestamp = timestamp()
  }
}

resource "aws_secretsmanager_secret" "application_secrets" {
  name        = "${var.service}"
  description = "Secrets for ${var.service} application"

  tags = {
    Service     = var.service
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "application_secrets_version" {
  secret_id     = aws_secretsmanager_secret.application_secrets.id
  secret_string = jsonencode({
    some_secret = "some very secret value",
    some_other_secret = "some other secret value",
    some_aes_key = random_password.aes_key.result
  })
}

resource "aws_ssm_parameter" "application_secret_arn" {
  name  = "/${var.vertical}/${var.environment}/${var.service}/config/otto.config.aws.secrets.arn"
  type  = "String"
  value = aws_secretsmanager_secret.application_secrets.arn
}