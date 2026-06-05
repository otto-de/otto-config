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
  description = "When true, creates an SQS queue, EventBridge rules for AppConfig/SecretsManager/SSM, and the necessary IAM policy so that the Zealot library can receive push notifications instead of relying solely on periodic polling."
  type        = bool
  default     = false
}

variable "change_notification_consumer_role_arn" {
  description = "ARN of the IAM role that the application uses at runtime.  When change_notification_enabled is true, this role receives sqs:ReceiveMessage, sqs:DeleteMessage and sqs:GetQueueAttributes permissions on the notification queue."
  type        = string
  default     = ""

  validation {
    condition     = !var.change_notification_enabled || var.change_notification_consumer_role_arn != ""
    error_message = "change_notification_consumer_role_arn must be set when change_notification_enabled is true."
  }
}
