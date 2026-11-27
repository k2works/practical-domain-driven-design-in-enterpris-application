---
type: Plan
title: "Bolt 59 計画と報告 - メンテナンス表示"
description: "W10-1 計画の Bolt 59（技術、SP 0）。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T10:45:07Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T20:59:05Z }
---

# Bolt 59 計画と報告 - メンテナンス表示

[W10-1 計画](iteration_plan-W10-1.md)の Bolt 59（技術、SP 0）。

**状態**: 守り方と置き場所は W10-1 計画の CP-3・CP-4 で承認済み（2026-11-27）。この報告は 56-1 でまとめて承認を受ける。検証（`validating-design`）: A 終盤の技術 Bolt。B タスク名は `operation.md`、方式は ADR-023 の決定 3。**時点復旧の手順は ADR-023 と変えた**（下の議題 1）。C 運用タスクは Bolt 54 の形（環境の確認、`--dry-run`、純粋な関数のテスト）にそろえた。

## 計画と結果

- [x] (1) 有効のとき・無効のときの統合テストを先に書き、コンパイルの失敗を確かめた
- [x] (2) `MaintenanceFilter`（最も前の順序。503、固定の案内、`Retry-After` 1800 秒、`Cache-Control: no-store`、session を作らない。既定は無効で部品を作らない）
- [x] (3) タスク定義を作る関数のテストを先に書き、失敗を確かめてから `maintenance:on`・`maintenance:off` を作った
- [x] (4) 運用タスク手順書、`operation.md` の注、マニュアル 2.9

## 承認の議題

| # | 議題 | 内容と推奨 |
| :--- | :--- | :--- |
| 1 | **ADR-023 と計画のとおりにしなかった点: 時点復旧の最初の手順を、メンテナンス表示に差し替えていない** | 表示が止めるのは画面の要求だけで、定期処理と起動のときのイベントの配り直しは DB に書き続ける。差し替えると「書き込みを止める」を満たさない。手順書は `desiredCount` を 0 にする手順のまま残し、理由を書いた。推奨: このまま承認し、ADR-023 の決定 3 の該当の文を直す。表示のまま書き込みも止めたいなら、有効の間は定期処理と配信を止める仕組みを別の Bolt で設計する |
| 2 | 切り替えは配備のワークフローではなく、AWS CLI でタスク定義の版を登録して入れ替える | ADR-023 は「`deploy:release` と同じ経路」とする。ワークフローはイメージを作り直すので、今のイメージのまま環境変数だけを変える形（`deploy:rollback` と同じ経路）にした |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `com.example.cargotracker.operations.*`、`ActuatorHealthIntegrationTest`、アーキテクチャのテスト、`ModularityTest`、静的検査 | 成功。全件のゲートは 56-1 |
| `node --test ops/scripts/operation.test.js` | 19 件成功 |
| `maintenance:on`・`off` の `--dry-run` | ステージングは内容を表示した。本番は `--confirm` がないと失敗した |

## 未確認

- `maintenance:on`・`off` の実行（AWS の環境がない）。ALB の後ろでの 503 の見え方。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-27 | 初版 | anthropic/claude-sonnet-5-5 |
