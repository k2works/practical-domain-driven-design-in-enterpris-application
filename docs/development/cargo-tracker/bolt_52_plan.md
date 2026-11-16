---
type: Plan
title: "Bolt 52 計画と中間報告 - CI/CD（ステージング・本番の配備のワークフロー）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T03:28:58Z }
---

# Bolt 52 計画と中間報告 - CI/CD（ステージング・本番の配備のワークフロー）

[W10 計画](iteration_plan-W10.md)の Bolt 52（技術、SP 0、見積り 200 分）。ワークフローの作成までを終えた。52-1（ロールバックの運用タスク、起動時の dev プロファイルの拒否）は未着手。

**状態**: 外部連携・本番にあたるが、`/goal` のもとで計画を承認される前に書いた。**人が読むまで有効にしない**（`workflow_dispatch` のみ。リポジトリの変数と Environment を人が用意するまで動かない）。検証済み: `validating-design`（A 終盤の技術 Bolt。B `architecture_infrastructure.md` の CI/CD の定め（配備を取り消しから切り離す・イメージを 1 回ビルドして digest を昇格・本番は必須のレビュアー）と一致。C 既存の `cargo-tracker-ci.yml` の規律（SHA 固定・permissions）を踏襲）。

## 範囲と決定

| # | 内容 |
| :--- | :--- |
| 1 | 新しい `.github/workflows/cargo-tracker-deploy.yml`（手動起動のみ）。ビルド（runtime ステージ・不変のタグ）→ ステージングの ECR → ステージングの ECS 配備 → 本番へ digest で昇格（Environment `production` の承認が先） |
| 2 | 認証は OIDC（長期のアクセスキーなし）。ロールの ARN とリージョンはリポジトリの変数。アクションは SHA 固定（`gh` で最新の版の SHA を取得した: configure-aws-credentials v6.3.0、amazon-ecr-login v2.1.7、amazon-ecs-render-task-definition v1.9.1、amazon-ecs-deploy-task-definition v2.6.4） |
| 3 | 配備は `cancel-in-progress: false` の専用のグループで取り消さない。ECS はサーキットブレーカーで失敗時に自動で戻す（Terraform 側の設定） |

## 承認の議題

| # | 議題 | 推奨 |
| :--- | :--- | :--- |
| 1 | **`actionlint` で構文を検査できなかった**（外部のコンテナイメージの取得が権限の確認で止められた）。YAML の構文・式の誤りは未確認 | 人が `actionlint` を流すか、ローカルに入れる許可を出す。有効にする前に必須 |
| 2 | 実行は一度もしていない（AWS のアカウント・ロール・Environment が未用意） | ステージングの用意の後に `workflow_dispatch` で試す |
| 3 | Terraform の `terraform fmt`・`validate` を PR で流すワークフロー、`terraform plan` の PR への表示は未作成 | 認証情報が決まってから足す（plan は認証情報が要る） |

## 計画

- [x] (1) 配備のワークフローを書く
- [ ] (2) 構文の検査（actionlint）— 上の議題 1
- [ ] (3) 52-1: ロールバックと `maintenance:on/off` の運用タスク（Gulp）。**未着手**（運用タスクの設計が要る）
- [x] (3a) 52-1: アプリの起動時の dev プロファイルの拒否 `DevProfileGuard`（TDD。ルートのパッケージ。`platform` は ArchUnit の規則で Spring に依存できないため、規則は変えず `ClockConfiguration` と同じ場所に置いた。置き場所は人に諮る議題）。Terraform が `CARGOTRACKER_DEPLOYMENT_ENVIRONMENT` を渡す。単体のテスト・アーキテクチャのテスト・静的検査は成功。全体のゲートは未再実行
- [ ] (4) Terraform の PR 検査のワークフロー

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版（draft。中間報告つき） | anthropic/claude-sonnet-5-5 |
