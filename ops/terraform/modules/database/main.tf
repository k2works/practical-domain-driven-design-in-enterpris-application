# RDS for PostgreSQL（ADR-008）。暗号化、自動バックアップ（時点復旧）、非公開サブネット。
# マスターのパスワードは RDS が Secrets Manager で管理する。Terraform の変数にも状態にも平文を置かない。
# DB 利用者（マイグレーション用・アプリケーション用）と権限の初期化は運用タスク（db:init）で行う。

resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-db"
  subnet_ids = var.private_subnet_ids
}

resource "aws_security_group" "database" {
  name        = "${var.name}-db"
  description = "RDS への接続はアプリケーションからだけ許す"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name}-db" }
}

resource "aws_vpc_security_group_ingress_rule" "from_application" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = var.allowed_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "アプリケーション（ECS）から PostgreSQL"
}

resource "aws_db_instance" "this" {
  identifier = "${var.name}-db"

  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage_gb
  max_allocated_storage = var.max_allocated_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.database_name
  username = var.master_username

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false
  multi_az               = var.multi_az

  backup_retention_period      = var.backup_retention_days
  backup_window                = "17:00-18:00"
  maintenance_window           = "sun:18:30-sun:19:30"
  copy_tags_to_snapshot        = true
  deletion_protection          = var.deletion_protection
  skip_final_snapshot          = false
  final_snapshot_identifier    = "${var.name}-db-final"
  performance_insights_enabled = true

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}
