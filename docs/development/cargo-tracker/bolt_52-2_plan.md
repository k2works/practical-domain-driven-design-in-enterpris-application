---
type: Plan
title: "Bolt 52-2 計画と報告 - Terraform の PR 検査"
description: "W10-1 計画の Bolt 52-2（技術、SP 0）。"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T11:14:30Z }
---

# Bolt 52-2 計画と報告 - Terraform の PR 検査

[W10-1 計画](iteration_plan-W10-1.md)の Bolt 52-2（技術、SP 0）。

**状態**: 範囲は W10-1 計画の CP-1 で承認済み（2026-11-27）。この報告は 56-1 でまとめて承認を受ける。検証（`validating-design`）: A 終盤の技術 Bolt。B `architecture_infrastructure.md` の CI/CD のうち、認証情報の要らない検査だけを作る。C 既存のワークフローと同じく、Action は SHA で固定した `actions/checkout` だけを使う。

## 計画と結果

- [x] (1) `.github/workflows/cargo-tracker-terraform.yml`（`ops/terraform/` を変える PR と、develop・main への push で、`fmt -check` と `validate`（staging・production））
- [x] (2) `actionlint` と、同じコマンドの Linux のコンテナでの実行

## 決めたこと

Terraform は版を固定した公式のイメージ（`hashicorp/terraform:1.9.8`。手元の版と同じ）で実行し、新しい Action を足さない。イメージはタグで固定していて、digest では固定していない。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `actionlint`（docker の `rhysd/actionlint`） | 指摘なし |
| Linux のコンテナでの `terraform fmt -check -recursive` と `validate`（staging・production） | 成功 |

## 未確認

- GitHub Actions 上での実行（push していないので、まだ一度も動いていない）。
- `plan` の PR への表示と、静的検査の道具（tflint・tfsec）は作っていない（H-9）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-27 | 初版 | anthropic/claude-sonnet-5-5 |
