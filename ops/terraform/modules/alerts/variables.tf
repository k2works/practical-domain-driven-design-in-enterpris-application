variable "name" {
  description = "リソース名の接頭辞"
  type        = string
}

variable "log_group_name" {
  description = "アプリケーションのログのロググループ"
  type        = string
}

variable "load_balancer_arn_suffix" {
  type = string
}

variable "target_group_arn_suffix" {
  type = string
}

variable "operations_alert_emails" {
  description = "運用の警告の配布先（運用責任者が決める。ADR-023）"
  type        = list(string)
}

variable "data_alert_emails" {
  description = "データ責任者への警告の配布先"
  type        = list(string)
}

variable "tracking_alert_emails" {
  description = "追跡管理者への警告の配布先"
  type        = list(string)
}
