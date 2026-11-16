---
type: Plan
title: "Bolt 51 計画 - Terraform（AWS の構成。コードと検証まで）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T03:49:24Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-16T03:49:25Z }
---

# Bolt 51 計画 - Terraform（AWS の構成。コードと検証まで）

[W10 計画](iteration_plan-W10.md)の Bolt 51・51-1・51-2・51-3 をまとめた計画（技術、SP 0。見積り 200 + 180 + 180 + 220 = 780 分）。ADR-008・ADR-020 の構成を Terraform で書く。**`apply` はしない**（W10 計画 CP-1、ADR-020）。

**状態**: 承認済み（2026-11-16、human:kakimomokuri。置き場所とコードの承認。`apply` は行わない）。外部連携・DB・セキュリティ・本番にあたる内容で、`apply` と main への統合は別に人が判断する（コードは書くが、承認の議題に出す）。検証済み（`validating-design`: A 終盤の技術 Bolt。テストの入口は IaC の検査、B `architecture_infrastructure.md`・ADR-008・ADR-020・`operation.md` と構成が一致、C W9 までの運用の規律に反しない。`validating-iteration-plan` は事実の確認で代えた）。

## 範囲

| 区分 | 内容 |
| :--- | :--- |
| 51 | 状態の保存先（S3 + DynamoDB のロック。変数）、ネットワーク（VPC・公開と非公開のサブネット 2 AZ・NAT）、ECR、環境ごとの変数（リージョン・環境名・予算の上限は変数） |
| 51-1 | RDS for PostgreSQL 18（暗号化、自動バックアップ、時点復旧、非公開サブネット）、Secrets Manager（DB の認証情報の置き場。値は入れない）。DB 利用者と権限の初期化（DA-02）は運用タスク（Bolt 54）で、ここでは RDS の枠まで |
| 51-2 | S3（バージョニング、Object Lock、暗号化、公開の遮断）。外部原本の payload の保存先と必要書類の保存先の 2 つのバケット（RET-03、PV-01）。ウイルスの検査の仕組み（Bolt 7）は枠（バケットと権限）まで |
| 51-3 | ECS Fargate（2 タスク以上）、ALB、ACM、WAF、CloudWatch Logs、IAM（タスクの役割・実行の役割）。環境の変数で `dev` プロファイルを使わせない（#48 K-03） |

構成は `ops/terraform/modules/*` に分け、`ops/terraform/environments/{staging,production}` は同じモジュールを環境ごとの変数で呼ぶ。

## 決めたこと

| # | 決定 | 理由 |
| :--- | :--- | :--- |
| 1 | `terraform apply` はしない。検証は `terraform fmt -check`・`terraform init -backend=false`・`terraform validate` まで。`plan` は AWS の認証情報がないので行わない | CP-1・ADR-020。実アカウントは人が用意する |
| 2 | 秘密の値（DB のパスワードなど）は Terraform の変数やコードに書かない。RDS の `manage_master_user_password`（Secrets Manager が管理）を使う | セキュリティ。状態ファイルに平文を残さない |
| 3 | 状態の保存先・アカウント・予算の上限は変数にする（ADR-020 の未決） | 未決を直書きしない |
| 4 | S3 の Object Lock の保持期間は変数にし、既定は設けない（非機能要件 RET-03 で決める。未決） | 仮の値を入れない |

## 承認の議題

- 新しい成果物 `ops/terraform/`（承認済み）。
- 本番の構成（Multi-AZ、保持期間）の既定値は、人が決めるまで変数にして値を入れない。

## 計画

- [x] (1) モジュール（network・ecr・database・storage・application）と環境（staging・production）を書く
- [x] (2) `terraform fmt -check`・`init -backend=false`・`validate` を環境ごとに通す
- [x] (3) 運用手順書（`docs/operation/cargo-tracker/`）に、Terraform の使い方（apply 前の確認事項）を書く
- [ ] (4) 静的検査の道具（tflint・tfsec など）の導入は別に諮る（外部ツール。W10 では `validate` までとする）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版（draft） | anthropic/claude-sonnet-5-5 |
