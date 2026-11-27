---
type: Plan
title: "Bolt 46-1 計画 - 見積りの条件の相談（US-24 AC3。US-24 を締める）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-15T03:00:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T10:08:31Z }
---

# Bolt 46-1 計画 - 見積りの条件の相談（US-24 AC3）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-24 AC3。**US-24 の 2 SP を数える**。見積り 180 分）。スキーマの変更はない。追跡の許可する依存に `quotation :: events` を足す（モジュール構造。台帳 D-169）。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | 見積り: `Quotation.requestConsultation`（提示済み・期限前だけ。内容 1〜500 文字。状態は変えず DE-33 を生成する）、`QuotationConsultationRequested` |
| 2 | 応用: `ConsultQuotationCommand`・`ConsultOutcome`・`QuotationResponseService.consult`（内容の検証。保存はせずイベントだけ発行） |
| 3 | 追跡: `QuotationConsultationRequestedEventHandler`（相談を購読して有人案件（見積り相談・通常・営業部）を起票。起票元キーで再配信の二重起票を防ぐ）。`package-info` の許可する依存に `quotation :: events` |
| 4 | 画面: C-05 に相談の欄（`POST …/response/consult`）。起票した案件は C-14 の問い合わせ一覧に載る |
| 5 | テスト・文書: 集約・サービス・ハンドラー・コントローラー・業務ルール（`consult_quotation.feature`）・画面（`@US-24-AC3`）、マニュアル 4 章、ui_design（C-05）、domain_model（DE-33）、user_story、test_strategy、台帳 D-169 |

## 決定（推奨のとおり。人にしか決められない判断は含まない）

- 相談は **DE-32 と同じ形（上流のイベントを下流の追跡が購読して案件を起票）**にする。見積りは追跡に依存しない（ADR-014 の方向）。人に諮る構造の判断は、追跡の依存の追加だけで、既存の購読（経路設計・外部データ）と同じ向きなので、「推奨のとおり承認」として台帳に記録した（D-169。仮）。
- 担当部門は「営業部」、優先度は通常（期限は既定のカレンダー）。業務責任者に確かめる。

## 計画

- [x] (1) テストを先に書き失敗を確かめた（集約・サービス・ハンドラー・コントローラー）
- [x] (2) 実装、画面の受入シナリオ 1、文書
- [x] (3) 品質ゲート・Sonar（Bolt 46 とまとめて流す）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-15 | 初版 | anthropic/claude-sonnet-5-5 |
