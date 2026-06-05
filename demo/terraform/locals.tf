locals {
  account_ids = {
    infrastructure = var.infrastructure_account_id
    develop        = var.develop_account_id
    live           = var.live_account_id
  }
  common_tags = {
    team        = var.team
    service     = var.service
    environment = var.environment
    vertical    = var.vertical
  }
}

