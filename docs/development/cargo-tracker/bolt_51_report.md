---
type: Plan
title: "Bolt 51 終了報告 - Terraform（AWS の構成。コードと検証まで）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T02:36:11Z }
---

# Bolt 51 終了報告 - Terraform（AWS の構成。コードと検証まで）

[Bolt 51 計画](bolt_51_plan.md)（51・51-1・51-2・51-3 をまとめた）の終了報告。人の承認待ち。

## 承認の議題（先に確かめてほしいこと）

| # | 議題 | 推奨 |
| :--- | :--- | :--- |
| 1 | **外部連携・DB・セキュリティ・本番にあたる成果物**（`ops/terraform/`）を、`/goal` のもとで人が計画を読む前に書いた。コードは develop にコミットしただけで、main には統合していない。`apply` もしていない。構成（VPC・ECR・RDS・S3 Object Lock・ECS Fargate・ALB・WAF・IAM）を確かめてほしい | レビューして、直す点があれば次の Bolt（52）の前に指示する |
| 2 | 未決の値は既定値を置かず、環境が必ず指定する変数にした: Multi-AZ・バックアップの保持日数・Object Lock のモードと日数・ログの保持日数・予算の上限（null なら作らない）・アカウント・状態の保存先 | 決まった値を人が与える（ADR-020 の未決 1〜3） |
| 3 | **`plan` と静的検査（tflint・tfsec）は行っていない。** `plan` は AWS の認証情報が要る。静的検査の道具は入っていない | 道具の導入を別に諮る（外部ツール）。`validate` までを W10 の検証とする |
| 4 | `dev`・`demo`・`test` のプロファイルは変数の検証で拒否した（#48 K-03 の IaC 側）。アプリ側の起動時の拒否は Bolt 52-1 で足す | 承認 |

## 成果

- `ops/terraform/modules/{network,ecr,database,storage,application,stack}` と `environments/{staging,production}`。
- 秘密の値を持たない: RDS は `manage_master_user_password`、アプリの DB 利用者の認証情報は Secrets Manager の ARN だけ受ける。
- S3 は 2 つ（外部原本・必要書類）で、バージョニング・Object Lock・KMS の暗号化・公開の遮断・TLS 以外の拒否。
- 本番はステージングと同じモジュールで、削除保護・NAT 2 つ・大きいインスタンスだけを変えた。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `terraform fmt -recursive` | 差分なし（整形済み） |
| `terraform init -backend=false` と `validate`（staging・production） | どちらも成功 |
| `plan`・静的検査 | **未実施**（上の議題 3） |
| アプリのゲート（`check` など） | 今回はアプリのコードを変えていない。実行していない |

## 申し送り

- 状態の保存先（S3 + ロック）は `backend "s3" {}` の部分設定で、バケットとテーブルは人が用意する。運用手順（`-backend-config` の与え方、`apply` の前の確認）は Bolt 54 の運用手順書で書く。
- Bolt 7（必要書類の保存とウイルスの検査）の検査の仕組みは、バケットと権限の枠だけ。
- 規律: 検証のコマンドの先頭に標準入力につながる形（`cat > /dev/null <<EOF`）を 1 回誤って書いた。何も読まず何も変えていない。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版 | anthropic/claude-sonnet-5-5 |
