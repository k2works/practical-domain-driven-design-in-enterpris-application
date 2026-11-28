---
type: Plan
title: "Bolt 44-1 終了報告 - 週次の KPI の照会（US-21 AC4）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-15T09:00:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T10:08:31Z }
---

# Bolt 44-1 終了報告 - 週次の KPI の照会（US-21 AC4）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | S-22 を**監査担当者だけ**にした（人が選んだ）。営業担当者は KPI の画面を開けず、ナビからも外れた。監査担当者のホームは S-22 | 承認 | [計画](bolt_44-1_plan.md) |
| 2 | 週は UTC の月曜始まり、中央値は偶数件なら真ん中 2 つの平均、P90 は最近順位法。**PV-01 の除外は未実装で全件を母集団**にした（台帳 D-171。仮。除外の記録は W10 以降） | 承認 | 計画の決定 |
| 3 | identity の許可する依存に `tracking :: api` を足した（追跡の公開 API 経由。ADR-017 の ACL） | 承認 | 計画の範囲 2 |

## 成果

追跡の公開 API `InquiryStatistics`、`KpiStatistics`・`KpiWeeklyQueryService`、S-22 の週次の KPI、認可（`/staff/kpi-observations/**` は監査担当者）、ナビとホーム、画面のシナリオ（`@US-21-AC2〜AC4`）。`.table-scroll` の `position: relative`（横スクロールの修正）、横スクロールの失敗の文に原因の要素を載せる診断。

## 品質ゲート

[Bolt 44-2 終了報告](bolt_44-2_report.md) にまとめて記す。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-15 | 初版 | anthropic/claude-sonnet-5-5 |
