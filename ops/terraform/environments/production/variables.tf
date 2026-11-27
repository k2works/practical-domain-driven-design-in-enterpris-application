variable "region" {
  description = "リージョン（ADR-020: ap-northeast-1）"
  type        = string
  default     = "ap-northeast-1"
}

variable "availability_zones" {
  type    = list(string)
  default = ["ap-northeast-1a", "ap-northeast-1c"]
}

variable "certificate_arn" {
  description = "ALB の ACM 証明書の ARN（人が用意する）"
  type        = string
}

variable "image_tag" {
  description = "配備するイメージのタグ（CD が渡す。ステージングで確かめたタグ）"
  type        = string
}

variable "spring_profile" {
  description = "Spring のプロファイル（dev・demo・test は拒否される）"
  type        = string
  default     = "production"
}

variable "application_db_secret_arn" {
  description = "アプリケーションの DB 利用者の認証情報の Secrets Manager の ARN（db:init の後）"
  type        = string
}

variable "database_multi_az" {
  description = "未決（非機能要件）"
  type        = bool
}

variable "database_backup_retention_days" {
  description = "未決（非機能要件）"
  type        = number
}

variable "object_lock_mode" {
  description = "未決（RET-03）"
  type        = string
}

variable "object_lock_days" {
  description = "未決（RET-03）"
  type        = number
}

variable "log_retention_days" {
  description = "未決（非機能要件）"
  type        = number
}

variable "monthly_budget_usd" {
  description = "未決（ADR-020）。null なら予算を作らない"
  type        = number
  default     = null
}

variable "operations_alert_emails" {
  description = "運用の警告のメールの配布先（未決。運用責任者が決める。ADR-023）"
  type        = list(string)
}

variable "data_alert_emails" {
  description = "データ責任者への警告のメールの配布先（未決）"
  type        = list(string)
}

variable "tracking_alert_emails" {
  description = "追跡管理者への警告のメールの配布先（未決）"
  type        = list(string)
}
