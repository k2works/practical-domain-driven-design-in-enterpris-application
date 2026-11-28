variable "name" {
  description = "リソース名の接頭辞"
  type        = string
}

variable "region" {
  description = "リージョン（ログの出力先。直書きしない。ADR-020）"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "certificate_arn" {
  description = "ALB の ACM 証明書の ARN（ドメインと証明書は人が用意する）"
  type        = string
}

variable "image_repository_url" {
  description = "ECR のリポジトリ URL"
  type        = string
}

variable "image_tag" {
  description = "配備するイメージのタグ（不変のタグ。CD が渡す）"
  type        = string
}

variable "spring_profile" {
  description = "Spring のプロファイル。ステージング・本番では dev を使わせない（#48 K-03）"
  type        = string

  validation {
    condition     = !contains(["dev", "demo", "test"], var.spring_profile)
    error_message = "ステージング・本番で dev・demo・test のプロファイルは使えない（H2 のインメモリで動いてしまう）。"
  }
}

variable "deployment_environment" {
  description = "環境の名前（staging か production）。アプリの DevProfileGuard が dev を拒否する判断に使う"
  type        = string

  validation {
    condition     = contains(["staging", "production"], var.deployment_environment)
    error_message = "staging か production を指定する。"
  }
}

variable "database_endpoint" {
  type = string
}

variable "database_port" {
  type    = number
  default = 5432
}

variable "database_name" {
  type = string
}

variable "application_db_secret_arn" {
  description = "アプリケーションの DB 利用者の認証情報（username・password）の Secrets Manager の ARN。db:init が作る"
  type        = string
}

variable "storage_bucket_arns" {
  description = "アプリケーションが使う S3 バケットの ARN"
  type        = list(string)
}

variable "desired_count" {
  description = "タスクの数（ADR-008: 2 以上）"
  type        = number
  default     = 2

  validation {
    condition     = var.desired_count >= 2
    error_message = "ADR-008 のとおり 2 タスク以上にする。"
  }
}

variable "cpu" {
  type    = number
  default = 1024
}

variable "memory" {
  type    = number
  default = 2048
}

variable "health_check_path" {
  description = "ヘルスチェックのパス（OBS-10。Actuator の導入後に確定する）"
  type        = string
  default     = "/actuator/health"
}

variable "log_retention_days" {
  description = "ログの保持日数。未決（非機能要件）のため環境が指定する"
  type        = number
}

variable "deletion_protection" {
  type    = bool
  default = true
}
