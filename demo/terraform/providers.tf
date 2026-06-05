terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0.0"
    }
    vault = {
      source  = "hashicorp/vault"
      version = "= 5.6.0"
    }
  }
}

provider "aws" {
  alias = "infrastructure"
  region = var.region
  assume_role {
    role_arn     = "arn:aws:iam::${local.account_ids[var.environment]}:role/pipeline-role"
    session_name = "terraform-pipeline"
    duration     = "1h"
  }
  default_tags {
   tags = local.common_tags
  }
}

provider "aws" {
  alias = "develop"
  region = var.region
  assume_role {
    role_arn     = "arn:aws:iam::${local.account_ids["develop"]}:role/pipeline-role"
    session_name = "terraform-develop-pipeline"
    duration     = "1h"
  }
  default_tags {
    tags = local.common_tags
  }
}

provider "aws" {
  alias = "live"
  region = var.region
  assume_role {
    role_arn     = "arn:aws:iam::${local.account_ids["live"]}:role/pipeline-role"
    session_name = "terraform-live-pipeline"
    duration     = "1h"
  }
  default_tags {
    tags = local.common_tags
  }
}

provider "aws" {
  region = var.region
  assume_role {
    role_arn     = "arn:aws:iam::${local.account_ids[var.environment]}:role/pipeline-role"
    session_name = "terraform-infrastructure-pipeline"
    duration     = "1h"
  }
  default_tags {
    tags = local.common_tags
  }
}

provider "aws" {
  alias = "payeraccount"
  assume_role {
    role_arn = "arn:aws:iam::689069506545:role/listaccounts"
  }
  region = "us-east-1"
}

provider "vault" {
  auth_login_aws {
    role         = var.vault_role_name
    aws_role_arn = "arn:aws:iam::${local.account_ids[var.environment]}:role/pipeline-role"
    aws_region   = var.vault_aws_region
    header_value = var.vault_header_value
  }
  address          = var.vault_address
  skip_child_token = true
}

provider "random" {}