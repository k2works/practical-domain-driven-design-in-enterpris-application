---
type: Plan
title: "Bolt 43 計画 - 荷主への業務の通知の基盤（US-22。通知の公開 API・宛先の解決・本文）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T11:00:00Z }
---

# Bolt 43 計画 - 荷主への業務の通知の基盤（US-22）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-22 AC3・AC4 の土台。SP 0。見積り 200 分）。**ADR-017 が「荷主への通知（US-22）で宛先をどう渡すかは W9 の開始準備で決める」と残した点を、ここで決める**（W9 計画の確認ポイント CP-5）。スキーマの変更はない。

## 決定

| # | 決定 | 由来 |
| :--- | :--- | :--- |
| 1 | **宛先はその企業の利用中の荷主担当者全員**（台帳 D-166。仮。業務責任者に確かめる） | 推奨（「以降、推奨で自律実行」の指示による） |
| 2 | **宛先の解決は identity が担う**。identity が業務のイベント（見積り・予約・追跡）を購読し、その企業の荷主担当者を自分で引いて通知の公開 API を呼ぶ。共有カーネルにポートを足さない（ADR-019 の例外を広げない）。identity の `allowedDependencies` に予約・追跡のイベントを足す（43-1）。いまのイベントは企業 ID を持たないので、イベントに荷主企業の ID を足す（43-1） | **人の決定**（2026-11-14。AskUserQuestion の回答「identity が購読して送る」。human:kakimomokuri） |
| 3 | **送信は通知の公開 API の新しい口** `ShipperNotificationMail.send(ShipperNotificationMailRequest)`（`notification.interfaces.api`）。要求は契機の ID（`event_id`）、宛先の利用者の ID・アドレス、種類（共有カーネルの `ShipperNotificationKind`。公開 API は interface か record だけという規則のため enum を共有カーネルに置いた）、対象の参照（`target_ref`）、導線の URL、言語。結果は `Sent`・`AlreadySent`・`Failed` | 既存の 4 つの口と同じ形 |
| 4 | **本文は件名と画面への導線だけ**（料金・契約条件・書類・他の予約の情報を含めない。AC3、N-INV-01）。本文はテンプレートと種類・対象から作り、記録しない | AC3 |
| 5 | 通知の種類 `QUOTATION_PRESENTED`・`ROUTE_APPROVED`・`QUOTATION_EXPIRING`・`BOOKING_CONFIRMED`・`TRACKING_UNDER_REVIEW` を `NotificationKind` に足す（表の CHECK には先に置いてあるので**スキーマの変更はない**） | data_model |
| 6 | このBolt は口・宛先の解決（`identity` の `ShipperNotifications`・ACL `NotificationShipperMails`）・本文まで。イベントの購読と企業 ID の追加は 43-1・43-2 | 範囲を分ける |

## 計画

- [x] (1) テストを先に書き失敗を確かめた（`ShipperNotificationMailIntegrationTest`・`ShipperNotificationsTest`）
- [x] (2) 実装（通知の公開 API・サービス・テンプレート 5 種・アダプター、identity の ACL と `ShipperNotifications`）
- [ ] (3) 品質ゲート・Sonar・文書（ADR-017 の補足、台帳 D-166、domain_model N-INV）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版。宛先の解決の方式は人が決めた（identity が購読して送る） | anthropic/claude-sonnet-5-5 |
