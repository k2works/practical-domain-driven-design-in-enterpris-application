# 1 つの環境の全体（ステージング・本番で共通）。環境の違いは変数だけで表す。

module "network" {
  source = "../network"

  name               = var.name
  availability_zones = var.availability_zones
  nat_gateway_count  = var.nat_gateway_count
}

module "ecr" {
  source = "../ecr"

  name = var.name
}

# 外部原本の payload（RET-03・PV-01）
module "external_originals" {
  source = "../storage"

  bucket_name    = "${var.name}-external-originals"
  retention_mode = var.object_lock_mode
  retention_days = var.object_lock_days
}

# 必要書類（Bolt 7）
module "required_documents" {
  source = "../storage"

  bucket_name    = "${var.name}-required-documents"
  retention_mode = var.object_lock_mode
  retention_days = var.object_lock_days
}

module "database" {
  source = "../database"

  name                                  = var.name
  vpc_id                                = module.network.vpc_id
  private_subnet_ids                    = module.network.private_subnet_ids
  allowed_security_group_id             = module.application.security_group_id
  additional_allowed_security_group_ids = [module.bastion.security_group_id]
  multi_az                              = var.database_multi_az
  backup_retention_days                 = var.database_backup_retention_days
  instance_class                        = var.database_instance_class
  deletion_protection                   = var.deletion_protection
}

# 運用の踏み台（SSM のポートフォワード。bolt_54_plan.md の D-1）
module "bastion" {
  source = "../bastion"

  name                       = var.name
  vpc_id                     = module.network.vpc_id
  private_subnet_id          = module.network.private_subnet_ids[0]
  database_security_group_id = module.database.security_group_id
}

module "application" {
  source = "../application"

  name                      = var.name
  region                    = var.region
  vpc_id                    = module.network.vpc_id
  public_subnet_ids         = module.network.public_subnet_ids
  private_subnet_ids        = module.network.private_subnet_ids
  certificate_arn           = var.certificate_arn
  image_repository_url      = module.ecr.repository_url
  image_tag                 = var.image_tag
  spring_profile            = var.spring_profile
  deployment_environment    = var.deployment_environment
  database_endpoint         = module.database.endpoint
  database_name             = module.database.database_name
  application_db_secret_arn = var.application_db_secret_arn
  storage_bucket_arns       = [module.external_originals.bucket_arn, module.required_documents.bucket_arn]
  desired_count             = var.desired_count
  log_retention_days        = var.log_retention_days
  deletion_protection       = var.deletion_protection
}

# 予算の上限（ADR-020: 未決のため、空なら作らない）
resource "aws_budgets_budget" "monthly" {
  count = var.monthly_budget_usd == null ? 0 : 1

  name         = "${var.name}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"
}
