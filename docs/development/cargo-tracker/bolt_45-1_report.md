---
type: Plan
title: "Bolt 45-1 終了報告 - 過去の見積依頼の複製（US-01 AC5。US-01 を締める）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T16:00:00Z }
---

# Bolt 45-1 終了報告 - 過去の見積依頼の複製（US-01 AC5。US-01 を締める）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | 複製は、作成画面を `?copyFrom=` 付きで開くと輸送条件が入る形（保存はしない）。希望到着期限は空、必要書類は引き継がない（Q-INV-11）。他社の見積依頼は 404 | 承認 | [計画](bolt_45-1_plan.md) |
| 2 | 複製のリンクは詳細（C-04）だけに足した。一覧に足すかは W10 以降の見直し | 承認 | 計画の決定 |
| 3 | **Bolt 45 と 45-1 のゲートをまとめた**（45 は文書とシナリオだけだったため）。US-01 の 2 SP をここで数える。GitHub の Issue #25 のクローズは人の許可が要るので実行していない | 承認 | [Bolt 45 終了報告](bolt_45_report.md) |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 45-1 回（W9。機能。US-01 AC5。US-01 を締める） |
| 期間 | 2026-11-14 |
| SP | **2**（US-01） |
| 見積り／実績 | 見積り 180 分。実績は概算 70 分 |
| 計画 | [Bolt 45-1 計画](bolt_45-1_plan.md) |

## 成果

`TransportRequestController.newForm` の `copyFrom`、C-04 の「この見積依頼を複製して新しく作る」、UI シナリオ 1（`@US-01-AC5`）、マニュアル 3 章、ui_design の C-03、受入条件の照合の表の US-01 AC5。テスト: `TransportRequestControllerTest` に 3 行（複製の初期値、他社と形式の誤りは 404、指定なしでは案内が出ない）、UI シナリオ 1。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | 1 回目は `ReturnAfterExpirySecurityIntegrationTest` で失敗（既存のテストが、実在しないクエリ引数として `copyFrom=TR-1` を使っていて、複製の機能を足したことで「ない業務番号」の 404 になった。複製とは無関係の `stage=2` に直した。同じ形の `RecoveryCodeLoginSecurityIntegrationTest` も直した）。流し直して BUILD SUCCESSFUL（31 分 25 秒。Bolt 45 の分もここでまとめて流した） |
| SonarQube | **PASS**（新しい違反 0、カバレッジ 95.9%。Bolt 45 の分とまとめて流した） |

## 申し送り

次は Bolt 47（US-09 AC2・AC3。矛盾と停止中の表示）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
