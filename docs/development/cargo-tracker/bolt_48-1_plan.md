---
type: Plan
title: "Bolt 48-1 計画 - 辞退した見積りの輸送要求を辞退で終了にする（週全体レビューの高 H-2）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-15T06:38:28Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T10:08:31Z }
---

# Bolt 48-1 計画 - 辞退した見積りの輸送要求を辞退で終了にする

W9 の週全体レビュー（[レポート](../../review/cargo-tracker/w9_review_20261115.md)）の高 H-2 への対応。[Bolt 48](bolt_48_plan.md) の枝番の追加の Bolt（SP 0。見積り 120 分）。**スキーマの変更はない**（DB の CHECK に `WITHDRAWN` が初めからある）。

## 人の決定（2026-11-15。AskUserQuestion の選択。human:kakimomokuri）

選択肢は 2 つ（辞退で終了にする／再見積り待ちにして営業の対応待ちに載せる）。**「辞退で終了にする」（推奨）を選んだ**。台帳 D-173。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | 見積りが辞退した事実 DE-17 `QuotationDeclined` を発行する（`Quotation.decline`・`QuotationResponseService.decline`） |
| 2 | 輸送要求に状態 `WITHDRAWN`（辞退で終了）と `markWithdrawn`・`isWithdrawn`。見積り作成中・見積提示済みで同じ版のときだけ。冪等 |
| 3 | 見積りの listener `QuotationDeclinedEventHandler`（別のトランザクション。`TransportRequestProgression` を使う） |
| 4 | 表示: 状態の名前「辞退で終了」、荷主の詳細の案内（複製）、受付一覧の見積提示済みの表から外れる（状態が QUOTED でなくなるので SQL の変更なし） |
| 5 | テスト・文書: 集約・listener・業務ルール（`decline_quotation.feature`）・画面、domain_model・ui_design・マニュアル 4・6 章・台帳 D-173 |

## 計画

- [x] (1) テストを先に書き失敗を確かめた
- [x] (2) 実装・文書
- [x] (3) 品質ゲート・Sonar・CI

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-15 | 初版 | anthropic/claude-sonnet-5-5 |
