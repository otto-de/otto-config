variable "environment" {}
variable "team" {}
variable "vertical" {}
variable "service" {}
variable "region" {}
variable "build_version" {
  default = "latest"
}
variable "infrastructure_account_id" {}
variable "develop_account_id" {}
variable "live_account_id" {}
variable "current_desired_instances" {
  default = 2
}
variable "use_dr" { default = "false" }
variable "service_port" {
  default = 8443
}

variable "vault_address" {
  description = "Address of the Vault server"
  type        = string
  default     = "https://main.live.vault.platform.otto.de"
}

variable "vault_role_name" {
  description = "Role name for Vault authentication"
  type        = string
  default     = "search"
}

variable "vault_header_value" {
  description = "Header value for Vault authentication"
  type        = string
  default     = "vault-prod.esb.ottogroup.com"
}

variable "vault_aws_region" {
  description = "AWS region for Vault authentication"
  type        = string
  default     = "eu-central-1"
}
