---
type: ADR
title: "ADR-008: AWS の ECS Fargate・RDS・S3 で実行する"
description: "アプリケーションを AWS の ECS Fargate で動かし、RDS for PostgreSQL、S3（バージョニング・Object Lock）、ALB・WAF を使い、Terraform と GitHub Actions（OIDC）で構築・配備する決定。"
tags: [adr, tech-stack]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-01T07:14:57Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-01T07:29:05Z }
---

# ADR-008: AWS の ECS Fargate・RDS・S3 で実行する

マネージドサービスでインフラ設計の構成を実現し、運用の負担を小さくする。

日付: 2026-10-01

## ステータス

承認済み

## コンテキスト

- インフラ設計は、マネージドなコンテナ実行、マネージドなリレーショナル DB、削除保護付きのオブジェクトストレージ、TLS 終端と WAF、シークレット管理を求め、クラウド事業者の選定を技術スタック選定に委ねた（AI-01）。
- 本プロジェクトのテンプレート（`docs/template/`）には AWS のステージング・本番の環境構築手順書があり、運用スキル（`operating-provision`、`operating-deploy` など）も AWS と Terraform を前提にしている。
- PV-01 は外部原本の消失を 1 件も許さない。
- 荷主はアジア・欧州にいるため、データの所在地と越境移転（NFR-PRIVACY-01）が未決である。

## 決定

**AWS で次の構成をとる。**

| 要素 | 採用 |
| :--- | :--- |
| コンテナ実行 | Amazon ECS on Fargate（2 タスク以上） |
| DB | Amazon RDS for PostgreSQL 18（自動バックアップ、時点復旧） |
| 外部原本 | Amazon S3（バージョニング、Object Lock） |
| エッジ | Application Load Balancer、AWS WAF、ACM |
| イメージ・シークレット | Amazon ECR、AWS Secrets Manager |
| 可観測性 | Amazon CloudWatch（Logs、Metrics、Alarms） |
| IaC・配備 | Terraform、GitHub Actions（AWS へは OIDC で認証） |
| リージョン | 東京（ap-northeast-1）を仮置きし、非機能要件で確定する |

Multi-AZ の要否、バックアップの保持期間、Object Lock の保持期間は非機能要件で決める。

### 代替案

| 案 | 却下理由 |
| :--- | :--- |
| Amazon EKS | Kubernetes の運用負担が大きく、単一デプロイ（ADR-001）には過大 |
| AWS App Runner | VPC 内の RDS 接続やデプロイの細かな制御で制約がある。運用スキルの前提とも合わない |
| 他のクラウド・PaaS | テンプレートと運用スキルを作り直す必要がある |

## 影響

### ポジティブ

- 既存のテンプレートと運用スキルをそのまま使える。
- コンテナ・DB・ストレージの運用をマネージドサービスに任せられる。
- S3 の Object Lock で、外部原本を保持期間中に削除できない状態にできる。

### ネガティブ

- AWS への依存が強くなる。アプリケーションは S3 互換の API とコンテナで動くようにし、依存をインフラ層に閉じる。
- リージョンの確定が非機能要件の結論待ちになる。

## コンプライアンス

- Terraform で、S3 のバージョニングと Object Lock、RDS の暗号化と自動バックアップが有効であることを確認する。
- GitHub Actions に長期のアクセスキーを置かず、OIDC で認証していることを確認する（AWS への認証。OIDC に対応しない Heroku のデモ環境の API キーは、ADR-013 の例外として Environment `demo` に置く）。

## 備考

- 著者: anthropic/claude-opus-5-5（AI の提案。human:kakimomokuri が 2026-10-01 に承認）
- 関連文書: [技術スタック](../../design/cargo-tracker/tech_stack.md)、[インフラストラクチャアーキテクチャ](../../design/cargo-tracker/architecture_infrastructure.md)
- 関連 ADR: ADR-001、ADR-004、ADR-007
