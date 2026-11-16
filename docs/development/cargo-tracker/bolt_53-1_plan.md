---
type: Plan
title: "Bolt 53-1 計画と報告 - 定期処理の排他（ShedLock）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T04:23:12Z }
---

# Bolt 53-1 計画と報告 - 定期処理の排他（ShedLock）

[W10 計画](iteration_plan-W10.md)の Bolt 53-1（技術、SP 0、見積り 180 分）のうち、**ShedLock だけ**を終えた。

**状態**: ライブラリの導入とスキーマ変更（`platform.shedlock`）は 2026-11-16 に人が承認した（「推奨で一括承認」）。検証済み: `validating-design`（A 終盤の技術 Bolt で TDD。B データモデルの `platform.shedlock` の定義（`shedlock` の行）に一致。C Bolt 53 の Actuator と同じ承認の形）。

## 成果

- 表 `platform.shedlock`（Flyway `V20261125100000`。H2 と PostgreSQL の共通の構文）、`QuotationConfiguration` に `LockProvider`（`usingDbTime`）と `@EnableSchedulerLock`、`QuotationExpiryScheduler.run` に `@SchedulerLock`（名前 `quotation-expiry-notification`、上限 30 分、下限 1 分）。
- テスト `ShedLockIntegrationTest`（PostgreSQL）: 同じ名前のロックは同時に 1 つしか取れず、解放の後は取れる。違う名前は互いに待たない。コンパイルが通らない状態（Red）から始めた。
- 置き場所: ルートのパッケージに `@Configuration` を置くとアーキテクチャの規則（ルートは `ClockConfiguration` だけ）に拒否された。規則は変えず、すでに `@EnableScheduling` を持つ `QuotationConfiguration`（合成ルート）に移した。ほかの文脈が定期処理を持つときは、そのときに置き場所を考え直す。

## 範囲外にしたもの（未着手）

| 項目 | 扱い |
| :--- | :--- |
| 監査の二重の防ぎ（監査の表の一意制約 `event_id`） | 監査の表への制約の追加は、既存データの確認を伴う。別の Bolt（Release 1.1 の頭） |
| 毒入りのイベントを捨てた ERROR のログの通知先と AUD-04 の照合 | 通知の宛先（運用チャンネル）が未決のため送る |
| DB の文の timeout | 非機能要件の値の確認が要るため送る |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `ShedLockIntegrationTest`・アーキテクチャのテスト・`ModularityTest` | 成功 |
| 全体のゲート・Sonar | 未再実行（W10 の締めで流す） |

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版 | anthropic/claude-sonnet-5-5 |
