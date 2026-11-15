---
type: Plan
title: "Bolt 44-1 計画 - 週次の KPI の照会（US-21 AC4。認可の変更を含む）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-15T09:00:00Z }
---

# Bolt 44-1 計画 - 週次の KPI の照会（US-21 AC4）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（見積り 200 分）。**認可の変更**（S-22 を監査担当者だけに）と、**モジュールの依存の追加**（identity → tracking の公開 API）を含む。スキーマの変更はない。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | 追跡の公開 API `tracking.interfaces.api`（`InquiryStatistics`・`InquiryCounts`。実装は `interfaces.api.internal` のアダプター）と、期間の照会の件数 `InquiryRecordRepository.countBetween` |
| 2 | identity: ACL `TrackingInquiryStatistics`、ドメインの規則 `KpiStatistics`（週の始まり・中央値・P90・自己照会率）、`KpiWeeklyQueryService`。identity の許可する依存に `tracking :: api` |
| 3 | S-22 に週次の KPI（直近 12 週）を足す。認可: `/staff/kpi-observations/**` は監査担当者だけ、監査担当者のホームと KPI のナビ（営業担当者のナビから外す） |
| 4 | テスト: `KpiStatisticsTest`、`KpiWeeklyQueryServiceTest`、コントローラー、セキュリティ（監査担当者だけが開ける）、画面（`@US-21-AC2〜AC4`。320px の横スクロール） |

## 決定（推奨のとおり。人にしか決められない判断は含まない）

- 週は UTC の月曜始まり、中央値は偶数件なら真ん中 2 つの平均、P90 は最近順位法、母集団の除外は未実装で全件（D-171。仮）。
- 横スクロールの原因は、表の中の `visually-hidden`（絶対配置）が囲みの外へ出ること。`.table-scroll` に `position: relative` を足して閉じ込めた。

## 計画

- [x] (1) テストを先に書き失敗を確かめた
- [x] (2) 実装・認可・文書
- [x] (3) 品質ゲート・Sonar（44-2 でまとめて流す）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-15 | 初版 | anthropic/claude-sonnet-5-5 |
