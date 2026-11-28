# コンテナイメージの置き場（ADR-008）。イメージは不変のタグで置き、脆弱性スキャンを有効にする。

resource "aws_ecr_repository" "this" {
  name                 = var.name
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "最新の ${var.keep_images} 個のイメージだけを残す"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = var.keep_images
      }
      action = { type = "expire" }
    }]
  })
}

variable "name" {
  description = "リポジトリ名"
  type        = string
}

variable "keep_images" {
  description = "残すイメージの数（ロールバックに使うので、少なくとも 10）"
  type        = number
  default     = 30
}

output "repository_url" {
  value = aws_ecr_repository.this.repository_url
}

output "repository_arn" {
  value = aws_ecr_repository.this.arn
}
