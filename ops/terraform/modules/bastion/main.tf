# 運用の踏み台（W10 の Bolt 54。bolt_54_plan.md の D-1）。SSM Session Manager のポートフォワードで DB に入るための小さな EC2。
# インバウンドは開けない（SSH なし）。操作は SSM のセッションの記録に残る。DB への接続は RDS のセキュリティグループが許す。

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "${var.name}-bastion"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "this" {
  name = "${var.name}-bastion"
  role = aws_iam_role.this.name
}

resource "aws_security_group" "this" {
  name        = "${var.name}-bastion"
  description = "運用の踏み台。インバウンドなし。SSM（443）と DB（5432）への送信だけ"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name}-bastion" }
}

resource "aws_vpc_security_group_egress_rule" "ssm" {
  security_group_id = aws_security_group.this.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "SSM のエンドポイント（NAT 経由）"
}

resource "aws_vpc_security_group_egress_rule" "database" {
  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = var.database_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "RDS への接続（ポートフォワードの先）"
}

resource "aws_instance" "this" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = "t4g.nano"
  subnet_id              = var.private_subnet_id
  vpc_security_group_ids = [aws_security_group.this.id]
  iam_instance_profile   = aws_iam_instance_profile.this.name

  metadata_options {
    http_tokens = "required"
  }

  # 最新の AMI が出るたびに踏み台が置き換わらないようにする。AMI の更新は運用の判断で行う（-replace で入れ替える）
  lifecycle {
    ignore_changes = [ami]
  }

  root_block_device {
    encrypted = true
  }

  tags = { Name = "${var.name}-bastion" }
}

variable "name" {
  description = "リソース名の接頭辞"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_id" {
  description = "踏み台を置く非公開サブネット（NAT で SSM に出られる）"
  type        = string
}

variable "database_security_group_id" {
  description = "RDS のセキュリティグループ（送信の先）"
  type        = string
}

output "security_group_id" {
  description = "踏み台のセキュリティグループ。RDS が接続を許す相手"
  value       = aws_security_group.this.id
}

output "instance_id" {
  description = "SSM の --target に渡す。.env の OPS_<ENV>_BASTION_ID に入れる"
  value       = aws_instance.this.id
}
