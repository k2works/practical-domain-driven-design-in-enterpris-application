variable "name" {
  description = "リソース名の接頭辞"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  description = "RDS を置く非公開サブネット（2 つ以上）"
  type        = list(string)
}

variable "allowed_security_group_id" {
  description = "接続を許すセキュリティグループ（アプリケーションの ECS）"
  type        = string
}

variable "additional_allowed_security_group_ids" {
  description = "アプリケーションのほかに接続を許すセキュリティグループ（運用の踏み台）"
  type        = list(string)
  default     = []
}

variable "engine_version" {
  description = "PostgreSQL の版（ADR-008: 18）"
  type        = string
  default     = "18"
}

variable "instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "allocated_storage_gb" {
  type    = number
  default = 50
}

variable "max_allocated_storage_gb" {
  description = "ストレージの自動拡張の上限"
  type        = number
  default     = 200
}

variable "database_name" {
  type    = string
  default = "cargotracker"
}

variable "master_username" {
  description = "RDS のマスター利用者（運用の管理用。アプリケーションは使わない。パスワードは Secrets Manager が管理）"
  type        = string
  default     = "ctadmin"
}

variable "multi_az" {
  description = "Multi-AZ にするか。未決（非機能要件）のため既定値を置かず、環境が必ず指定する"
  type        = bool
}

variable "backup_retention_days" {
  description = "自動バックアップの保持日数（時点復旧の範囲）。未決（非機能要件）のため既定値を置かず、環境が必ず指定する"
  type        = number
}

variable "deletion_protection" {
  type    = bool
  default = true
}
