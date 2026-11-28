---
type: Plan
title: "Bolt 51-4 計画と報告 - ECS のタスク定義の版を Terraform の管理から外す"
description: "Bolt 56-1 の報告の議題 1 の実施（技術、SP 0）。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T00:10:43Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-28T00:10:43Z }
---

# Bolt 51-4 計画と報告 - ECS のタスク定義の版を Terraform の管理から外す

[Bolt 56-1 の報告](bolt_56-1_plan.md)の議題 1 の実施（技術、SP 0）。

**状態**: 構成の変更は議題 1 の推奨として 2026-11-28 に承認され、実施は同日の人の指示（「承認の対象外実施」。直前に挙げた 3 件と解釈した）による。この報告は 2026-11-28 に承認された（human:kakimomokuri。「承認してコミットして push して」）。

## 計画と結果

- [x] (1) `deploy:rollback` の戻し先を、イメージを digest で固定した版に限るテストを先に書き、失敗を確かめてから直した
- [x] (2) `aws_ecs_service.this` に `lifecycle { ignore_changes = [task_definition] }` を足した
- [x] (3) 運用タスク手順書の注意を書き直した

## 構成を変えて分かったこと

| 項目 | 内容 | 守り方 |
| :--- | :--- | :--- |
| 戻し先の取り違え | Terraform が登録して配備していない版は、イメージがタグ指定で、「イメージの違う版」として戻し先に選ばれうる | 戻し先を `@sha256:` で固定した版（配備のワークフローが作る形）に限る。コードで直した |
| Terraform の変更の反映 | `apply` でタスク定義を変えても、次の配備までサービスに反映されない。先に `maintenance:on`・`off` や `deploy:rollback` をすると、次の配備に Terraform の変更が入らないことがある | 手順書の注意だけ。タスクは検出しない |

最初の配備の前（digest で固定した版が 1 つしかない間）は、`deploy:rollback` は戻し先がなく失敗する。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `node --test ops/scripts/operation.test.js` | 24 件成功 |
| `terraform fmt -check`・`validate`（staging・production） | 成功。`plan`・`apply` は未実施 |

## 未確認

- 実環境での `apply` と配備の組み合わせの振る舞い。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-28 | 初版 | anthropic/claude-sonnet-5-5 |
| 2026-11-28 | 人の承認を記録した（human:kakimomokuri） | anthropic/claude-sonnet-5-5 |
