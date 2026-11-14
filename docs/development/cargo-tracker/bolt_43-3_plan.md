---
type: Plan
title: "Bolt 43-3 計画 - 荷主の通知一覧（C-16。US-22 を締める）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T14:00:00Z }
---

# Bolt 43-3 計画 - 荷主の通知一覧（C-16。US-22 を締める）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-22 AC4。**US-22 の 3 SP を数える**。見積り 180 分）。外部ライブラリ・認証の変更はない。スキーマは索引 1 本だけ（下）。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | 画面 C-16 `GET /customer/notifications`（既存の顧客レイアウトのナビの「通知」）。本人に宛てた荷主への業務の通知を新しい順に、通知の文・対象と導線・送信の結果・日時で示す。失敗した通知は宛先を示し、技術的な理由は示さない |
| 2 | 読み出し: `NotificationRepository.findRecentByRecipient`、`NotificationQueryService`（荷主担当者本人の通知。種類は荷主への業務の通知の 5 つに絞る。上限 100 件）。通知モジュールが日時表示の部品を使うため、`allowedDependencies` に `platform :: web` を足す |
| 3 | **スキーマ**: 索引 `ix_notification_recipient`（`recipient_user_id`、`created_at`。移行 `V20261122100000`）。新しい列・表はない。宛先で引くため（既存の一意制約は契機の ID が先頭） |
| 4 | 画面の受入シナリオ（`@ui`。US-22 の AC1〜AC4）、マニュアル 15 章、受入条件の照合の表に US-22 の 4 行、ui_design の C-16 |

## 決定（推奨のとおり。「以降、推奨で自律実行」の指示による記録。人にしか決められない判断は含まない）

- 一覧に出すのは本人宛ての通知だけ（自社のほかの荷主担当者の通知は出さない）。再設定・招待・TOTP のメールは出さない。
- 失敗した通知の技術的な理由（調査用）は荷主に出さない（公開 API の注と同じ方針）。
- 索引の移行は、42-1 の索引（人が承認した種類の変更）と同じ種類の最小のスキーマの変更として足す。業務責任者でなく人（あなた）に確かめる項目として終了報告に載せる。

## 計画

- [x] (1) テストを先に書き失敗を確かめた（`ShipperNotificationControllerTest` 4、`NotificationQueryServiceTest` 3）
- [x] (2) 実装、統合テスト（PostgreSQL。新しい順・種類の絞り込み・上限・索引の存在）、UI シナリオ 4、キャプチャ
- [ ] (3) 品質ゲート・Sonar・文書

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
