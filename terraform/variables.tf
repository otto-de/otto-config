variable "service" {
  description = "Service name for tagging und naming"
  type = string
}

variable "toggles_configuration_content" {
  default = ""
  description = "Content of the Toggles configuration"
  type = string
}

variable "properties_configuration_content" {
  default = ""
  description = "Content of the Properties configuration"
  type = string
}

variable "change_notification_enabled" {
  description = "When true, creates an SQS queue, EventBridge rules for AppConfig/SecretsManager/SSM, and the necessary IAM policy so that the Otto Config library can receive push notifications instead of relying solely on periodic polling."
  type        = bool
  default     = false
}
