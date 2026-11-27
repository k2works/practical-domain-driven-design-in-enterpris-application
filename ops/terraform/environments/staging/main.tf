# ステージング（ADR-020: 東京リージョン。本番と同じ構成を小さい規模で）。
# 状態の保存先は部分設定（-backend-config）で与える。アカウントとバケットは人が用意する（ADR-020 の未決）。

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }

  backend "s3" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "cargo-tracker"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}

module "stack" {
  source = "../../modules/stack"

  operations_alert_emails = var.operations_alert_emails
  data_alert_emails       = var.data_alert_emails
  tracking_alert_emails   = var.tracking_alert_emails

  name                           = "cargo-tracker-staging"
  region                         = var.region
  availability_zones             = var.availability_zones
  nat_gateway_count              = 1
  certificate_arn                = var.certificate_arn
  image_tag                      = var.image_tag
  spring_profile                 = var.spring_profile
  deployment_environment         = "staging"
  application_db_secret_arn      = var.application_db_secret_arn
  desired_count                  = 2
  database_multi_az              = var.database_multi_az
  database_backup_retention_days = var.database_backup_retention_days
  database_instance_class        = "db.t4g.medium"
  object_lock_mode               = var.object_lock_mode
  object_lock_days               = var.object_lock_days
  log_retention_days             = var.log_retention_days
  deletion_protection            = false
  monthly_budget_usd             = var.monthly_budget_usd
}

output "ecr_repository_url" {
  value = module.stack.ecr_repository_url
}

output "alb_dns_name" {
  value = module.stack.alb_dns_name
}

output "cluster_name" {
  value = module.stack.cluster_name
}

output "service_name" {
  value = module.stack.service_name
}
