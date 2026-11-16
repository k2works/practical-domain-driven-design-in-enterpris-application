variable "name" {
  description = "リソース名の接頭辞（環境名を含める。例: cargo-tracker-staging）"
  type        = string
}

variable "cidr_block" {
  description = "VPC の CIDR"
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "使うアベイラビリティゾーン（2 つ以上）"
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) >= 2
    error_message = "可用性のため 2 つ以上のアベイラビリティゾーンを指定する。"
  }
}

variable "nat_gateway_count" {
  description = "NAT ゲートウェイの数（1 以上。availability_zones の数以下）。ステージングは 1、本番は 2 を想定"
  type        = number
  default     = 1

  validation {
    condition     = var.nat_gateway_count >= 1
    error_message = "NAT ゲートウェイは 1 つ以上必要。"
  }
}
