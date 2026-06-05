output "app_config_id" {
  value = aws_appconfig_application.app_config.id
}

output "experiments_configuration_profile_id" {
  value = aws_appconfig_configuration_profile.experiments.configuration_profile_id
}

output "toggles_configuration_profile_id" {
  value = aws_appconfig_configuration_profile.toggles.configuration_profile_id
}

output "properties_configuration_profile_id" {
  value = aws_appconfig_configuration_profile.properties.configuration_profile_id
}

output "environment_id" {
  value = aws_appconfig_environment.environment.environment_id
}

output "deployment_strategy_id" {
  value = aws_appconfig_deployment_strategy.deployment_strategy.id
}

output "change_notification_queue_url" {
  description = "URL of the SQS queue that receives EventBridge config-change notifications.  Non-empty only when change_notification_enabled=true."
  value       = var.change_notification_enabled ? aws_sqs_queue.change_notifications[0].url : ""
}

output "change_notification_queue_arn" {
  description = "ARN of the SQS queue that receives EventBridge config-change notifications.  Non-empty only when change_notification_enabled=true."
  value       = var.change_notification_enabled ? aws_sqs_queue.change_notifications[0].arn : ""
}
