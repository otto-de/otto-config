module "appconfig_service" {
  source = "../../terraform"

  service = var.service

  toggles_configuration_content = file("${path.module}/appconfig_toggles.json")
  properties_configuration_content = file("${path.module}/appconfig_properties.json")

  # Enable push-based configuration refresh: EventBridge → SQS for AppConfig, SecretsManager, and SSM
  change_notification_enabled           = true
}
