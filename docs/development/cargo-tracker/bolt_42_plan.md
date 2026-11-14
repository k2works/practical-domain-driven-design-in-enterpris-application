---
type: Plan
title: "Bolt 42 計画 - 荷主向けの問い合わせの認可（他社の閲覧の試行の記録）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T06:00:00Z }
---

# Bolt 42 計画 - 荷主向けの問い合わせの認可（他社の閲覧の試行の記録）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-23 AC3。SP 0。見積り 180 分 → 範囲を縮めた。下の「開始準備で分かったこと」）。

## 開始準備で分かったこと

| 項目 | 結果 |
| :--- | :--- |
| URL の規則 | `/customer/**` は荷主担当者だけ（`SecurityConfiguration`）。荷主向けの問い合わせの URL は `/customer/service-cases/**` に置けば、規則は足さずに守られる |
| 案件ごとの認可 | `ServiceCaseAuthorization.canAccess` が荷主の自社の分に対応済み（W8 の Bolt 40-18）。再開（`reopen`）も荷主に対応済み |
| 試行の記録 | 既存の `OutOfScopeAttemptRecorder`（共有）を使う。記録の種類は監査の `target_type`（`VARCHAR(50)`）に名前で書くので、**スキーマは変えない** |
| 足りないもの | (1) 記録の種類 `SERVICE_CASE`、(2) 他社の案件とない案件を区別して返す照会、(3) 試行を記録して 404 を返す部品の入口 |

## 決定（推奨のとおり。人の指示「以降 Recommended で自律実行して」による記録。人にしか決められない判断は含まない）

1. `ScopedResourceKind.SERVICE_CASE` を足す（ID は受付番号）。
2. `ServiceCaseQueryService.customerDetail` を足し、結果を `CustomerServiceCaseLookup`（自社の案件・範囲外・見つからない）で返す。荷主担当者でない利用者は見つからない扱い。
3. `TrackingOutOfScopeResponder.recordServiceCaseAndNotFound` を足す（既存の部品の入口を増やす。ArchUnit の名前の規則と閉じる規則はそのまま守られる）。
4. 荷主向けの URL の規則は足さない（既存の `/customer/**` で足りる）。画面は 42-2・42-3。

## 計画

- [x] (1) テストを先に書き、失敗（コンパイルエラー）を確かめた: `ServiceCaseQueryServiceTest` 行 8c・8d、`TrackingOutOfScopeResponderTest` 1 件
- [x] (2) 実装（上の 1〜3）
- [x] (3) 品質ゲート・Sonar・文書

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
