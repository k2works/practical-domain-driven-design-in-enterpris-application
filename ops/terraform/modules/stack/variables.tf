variable "name" {
  description = "環境の名前（例: cargo-tracker-staging）"
  type        = string
}

variable "region" {
  description = "リージョン（ADR-020: 東京 ap-northeast-1。環境の変数で与える）"
  type        = string
}

variable "availability_zones" {
  type = list(string)
}

variable "nat_gateway_count" {
  type    = number
  default = 1
}

variable "certificate_arn" {
  description = "ALB の ACM 証明書の ARN。ドメインと証明書は人が用意する"
  type        = string
}

variable "image_tag" {
  description = "配備するイメージのタグ"
  type        = string
}

variable "spring_profile" {
  description = "dev・demo・test は拒否される（#48 K-03）"
  type        = string
}

variable "application_db_secret_arn" {
  description = "アプリケーションの DB 利用者の認証情報の Secrets Manager の ARN（db:init の後に作る）"
  type        = string
}

variable "desired_count" {
  type    = number
  default = 2
}

variable "database_multi_az" {
  description = "未決（非機能要件）。環境が指定する"
  type        = bool
}

variable "database_backup_retention_days" {
  description = "未決（非機能要件）。環境が指定する"
  type        = number
}

variable "database_instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "object_lock_mode" {
  description = "未決（RET-03）。環境が指定する"
  type        = string
}

variable "object_lock_days" {
  description = "未決（RET-03）。環境が指定する"
  type        = number
}

variable "log_retention_days" {
  description = "未決（非機能要件）。環境が指定する"
  type        = number
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "monthly_budget_usd" {
  description = "月額の予算の上限（USD）。未決（ADR-020）のため null なら予算を作らない"
  type        = number
  default     = null
}
