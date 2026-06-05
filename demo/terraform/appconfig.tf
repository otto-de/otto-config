module "appconfig_service" {
  source = "../../terraform"

  service = var.service

  experiments_configuration_content = file("${path.module}/appconfig_experiments.json")
  toggles_configuration_content = file("${path.module}/appconfig_toggles.json")
  properties_configuration_content = file("${path.module}/appconfig_properties.json")

  # Enable push-based configuration refresh: EventBridge → SQS for AppConfig, SecretsManager, and SSM
  change_notification_enabled           = true
  change_notification_consumer_role_arn = aws_iam_role.service_role.arn
}
