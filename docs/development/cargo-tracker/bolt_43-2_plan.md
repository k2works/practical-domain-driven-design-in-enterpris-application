---
type: Plan
title: "Bolt 43-2 計画 - 見積りの有効期限の 24 時間前の通知（US-22 AC2）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T13:00:00Z }
---

# Bolt 43-2 計画 - 見積りの有効期限の 24 時間前の通知（US-22 AC2）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-22 AC2。SP 0。見積り 180 分）。スキーマの変更・外部ライブラリの追加はない（Spring の `@Scheduled`）。アプリに定期実行の仕組みはまだ一つもなかったので、これが最初。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | 検出: `QuotationExpiryNotificationService.detect()`。荷主の回答か承認を待つ見積り（提示済み・荷主承認待ち）のうち、有効期限が検出の時刻の 23 時間後より後で 24 時間後以前のものを、荷主企業の ID と業務番号つきの `QuotationExpiring`（新しいイベント）として発行する |
| 2 | 起動: `QuotationExpiryScheduler`（`quotation.interfaces.scheduled`）が毎時 0 分に検出を呼ぶ。`@EnableScheduling` を `QuotationConfiguration` に足した。止めるときは `cargotracker.quotation.expiry-notification.enabled=false` |
| 3 | 通知: identity の `ShipperNotificationEventHandler` が `QuotationExpiring` を受け、`QUOTATION_EXPIRING` の通知を送る（契機の ID は見積りと有効期限の組） |
| 4 | 読み出し: `QuotationRepository.findAwaitingResponseExpiringBetween`（MyBatis。窓の始まりは含まず終わりは含む） |

## 決定（推奨のとおり。人にしか決められない判断は含まない）

- **窓の方式**: 1 時間ごとの検出が「23〜24 時間後に期限がくる見積り」を拾うので、1 つの見積りはちょうど 1 回の窓に入る。**期限の印の列（スキーマ）は足さない**。同じ窓を二重に検出しても（複数のインスタンス・再実行）、宛先の側の一意制約（契機の ID と宛先）が 2 通を防ぐ。
- **弱点（記録する）**: 検出が動かなかった時間の見積りは通知されない。排他（ShedLock）と監視は W10 の運用準備（release_plan の W10 の行にすでにある）。
- 24 時間前の窓の定義（24 時間・1 時間刻み）は US-22 AC2 の文言どおり。

## 計画

- [x] (1) テストを先に書き失敗を確かめた（`QuotationExpiryNotificationServiceTest` 3、`QuotationExpirySchedulerTest` 3、ハンドラーの行 1b、`MyBatisQuotationIntegrationTest` の窓の境界）
- [x] (2) 実装
- [ ] (3) 品質ゲート・Sonar・文書

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
