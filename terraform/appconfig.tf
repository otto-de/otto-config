resource "aws_appconfig_application" "app_config" {
  name        = var.service
}

# Retain the legacy "onex" configuration profile for backward compatibility during migration to the new
# "experiments" configuration profile.
resource "aws_appconfig_configuration_profile" "onex" {
  count              = var.onex_configuration_content != "" ? 1 : 0

  name               = "onex"
  application_id     = aws_appconfig_application.app_config.id
  description        = "Onex configuration profile"
  location_uri       = "hosted"

  lifecycle {
    prevent_destroy = false
  }
}

# Retain the legacy "onex" hosted configuration version for backward compatibility during migration to the new
# "experiments" hosted configuration version.
resource "aws_appconfig_hosted_configuration_version" "onex" {
  count                    = var.onex_configuration_content != "" ? 1 : 0

  application_id           = aws_appconfig_application.app_config.id
  configuration_profile_id = aws_appconfig_configuration_profile.onex[0].configuration_profile_id
  description              = "Onex Hosted Configuration"
  content_type             = "application/json"

  content                  = var.onex_configuration_content
}

resource "aws_appconfig_configuration_profile" "experiments" {
  name               = "experiments"
  application_id     = aws_appconfig_application.app_config.id
  description        = "Experiments configuration profile"
  location_uri       = "hosted"

  validator {
    content = var.experiments_json_schema
    type    = "JSON_SCHEMA"
  }
}

resource "aws_appconfig_hosted_configuration_version" "experiments" {
  count                    = var.experiments_configuration_content != "" ? 1 : 0

  application_id           = aws_appconfig_application.app_config.id
  configuration_profile_id = aws_appconfig_configuration_profile.experiments.configuration_profile_id
  description              = "Experiments Hosted Configuration"
  content_type             = "application/json"

  content                  = var.experiments_configuration_content
}

resource "aws_appconfig_deployment" "experiments_deployment" {
  count                    = var.experiments_configuration_content != "" ? 1 : 0

  application_id           = aws_appconfig_application.app_config.id
  configuration_profile_id = aws_appconfig_configuration_profile.experiments.configuration_profile_id
  configuration_version    = aws_appconfig_hosted_configuration_version.experiments[0].version_number
  deployment_strategy_id   = aws_appconfig_deployment_strategy.deployment_strategy.id
  description              = "Experiments Deployment"
  environment_id           = aws_appconfig_environment.environment.environment_id
}

resource "aws_appconfig_configuration_profile" "toggles" {
  name               = "toggles"
  application_id     = aws_appconfig_application.app_config.id
  description        = "Toggles configuration profile"
  location_uri       = "hosted"
  type               = "AWS.AppConfig.FeatureFlags"
}

resource "aws_appconfig_hosted_configuration_version" "toggles" {
  count                    = var.toggles_configuration_content != "" ? 1 : 0

  application_id           = aws_appconfig_application.app_config.id
  configuration_profile_id = aws_appconfig_configuration_profile.toggles.configuration_profile_id
  description              = "Toggles Hosted Configuration"
  content_type             = "application/json"

  content                  = var.toggles_configuration_content

  # Ignore changes in AWS UI
  lifecycle {
    ignore_changes = [
      content,
      version_number
    ]
  }
}

resource "aws_appconfig_deployment" "toggles_deployment" {
  count                    = var.toggles_configuration_content != "" ? 1 : 0

  application_id           = aws_appconfig_application.app_config.id
  configuration_profile_id = aws_appconfig_configuration_profile.toggles.configuration_profile_id
  configuration_version    = aws_appconfig_hosted_configuration_version.toggles[0].version_number
  deployment_strategy_id   = aws_appconfig_deployment_strategy.deployment_strategy.id
  description              = "Toggles Deployment"
  environment_id           = aws_appconfig_environment.environment.environment_id
}

resource "aws_appconfig_configuration_profile" "properties" {
  name               = "properties"
  application_id     = aws_appconfig_application.app_config.id
  description        = "Properties configuration profile"
  location_uri       = "hosted"
}

resource "aws_appconfig_hosted_configuration_version" "properties" {
  count                    = var.properties_configuration_content != "" ? 1 : 0

  application_id           = aws_appconfig_application.app_config.id
  configuration_profile_id = aws_appconfig_configuration_profile.properties.configuration_profile_id
  description              = "Properties Hosted Configuration"
  content_type             = "application/json"

  content = var.properties_configuration_content
}

resource "aws_appconfig_deployment" "properties_deployment" {
  count                    = var.properties_configuration_content != "" ? 1 : 0

  application_id           = aws_appconfig_application.app_config.id
  configuration_profile_id = aws_appconfig_configuration_profile.properties.configuration_profile_id
  configuration_version    = aws_appconfig_hosted_configuration_version.properties[0].version_number
  deployment_strategy_id   = aws_appconfig_deployment_strategy.deployment_strategy.id
  description              = "Properties Deployment"
  environment_id           = aws_appconfig_environment.environment.environment_id
}

resource "aws_appconfig_environment" "environment" {
  name           = "local"
  application_id = aws_appconfig_application.app_config.id
}

resource "aws_appconfig_deployment_strategy" "deployment_strategy" {
  name                           = "${var.service}-deployment-strategy"
  description                    = "Linear, 0 minute deployment time, 0 minute bake time"
  deployment_duration_in_minutes = 0
  final_bake_time_in_minutes     = 0
  growth_factor                  = 100
  growth_type                    = "LINEAR"
  replicate_to                   = "NONE"
}
