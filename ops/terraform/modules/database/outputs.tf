output "endpoint" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "database_name" {
  value = aws_db_instance.this.db_name
}

output "master_secret_arn" {
  description = "RDS が管理するマスターの認証情報の Secrets Manager の ARN"
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}
