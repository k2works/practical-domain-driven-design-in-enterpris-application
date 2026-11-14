---
type: Plan
title: "Bolt 42 終了報告 - 荷主向けの問い合わせの認可（他社の閲覧の試行の記録）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T06:00:00Z }
---

# Bolt 42 終了報告 - 荷主向けの問い合わせの認可（他社の閲覧の試行の記録）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **認可の確認ポイント**: 荷主担当者が他社の問い合わせを開こうとすると、内容を返さず 404 にし、試行を監査記録に残す（SERVICE_CASE の種類）。ない案件と区別せずに 404 を返す。スキーマは変えない（種類は名前で書く） | 承認 | [計画](bolt_42_plan.md) |
| 2 | 荷主向けの URL の規則は足していない（`/customer/**` が荷主担当者だけのため）。W9 計画の Bolt 42 の範囲を縮めた（URL の規則と `canAccess` の荷主の分は W8 で済み） | 承認 | 計画の「開始準備で分かったこと」 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 42 回（W9。機能。US-23 AC3 の下ごしらえ） |
| 期間 | 2026-11-14 |
| SP | 0（US-23 の 3 SP は 42-3 で数える） |
| 見積り／実績 | 見積り 180 分。実績は概算 50 分（範囲を縮めたため） |
| 計画 | [Bolt 42 計画](bolt_42_plan.md) |

## 成果

`ScopedResourceKind.SERVICE_CASE`、`ServiceCaseQueryService.customerDetail`（`CustomerServiceCaseLookup`）、`TrackingOutOfScopeResponder.recordServiceCaseAndNotFound`。テスト 3 件（`ServiceCaseQueryServiceTest` 行 8c・8d、`TrackingOutOfScopeResponderTest`）。先に失敗（コンパイルエラー）を確かめてから実装した。画面から呼ぶのは 42-3。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | BUILD SUCCESSFUL（30 分 58 秒） |
| SonarQube | **PASS**（新しい違反 0、カバレッジ 96.1%） |

## 申し送り

42-1（照会記録の自動記録と `case_number` の索引。スキーマ）、42-2（C-14）、42-3（C-15）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
