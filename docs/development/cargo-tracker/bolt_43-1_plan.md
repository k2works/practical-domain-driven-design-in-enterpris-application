---
type: Plan
title: "Bolt 43-1 計画 - 4 つの契機の通知（US-22 AC1）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T12:00:00Z }
---

# Bolt 43-1 計画 - 4 つの契機の通知（US-22 AC1）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-22 AC1。SP 0。見積り 220 分）。スキーマの変更はない。構造（モジュールの公表と依存）の変更は、[Bolt 43](bolt_43_plan.md) で人が決めた方式（identity が購読して送る）の実施で、ADR-017 の補足に記録済み。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | 契機: 見積りの提示（DE-03）、経路の割当て（DE-21。荷主の承認が必要になった）、本予約の確定、追跡が確認中になった（DE-18）。identity の `ShipperNotificationEventHandler`（`@ApplicationModuleListener`）が受け、その企業の利用中の荷主担当者全員へ送る |
| 2 | イベントに荷主企業の ID（と業務番号）を足す: DE-03・DE-21（`shipperCompanyId`・`transportRequestNumber`）、DE-18（`shipperCompanyId`）。以前の発行と既存の呼び出しのため、足す前の形の構成子を残す（足す前の発行は企業 ID が null で、通知しない） |
| 3 | 見積りの集約に通知の宛先を渡す `notifying`（保存しない）。`QuotationCommandService.present` と `RouteAssignmentService.assign`（輸送要求を読む）が渡す |
| 4 | 構造: `tracking.domain.events` を `events` の名前付きインターフェースにし、identity の `allowedDependencies` に予約・追跡のイベントを足す |
| 5 | 契機の ID は種類と事象の組から決まる名前ベースの UUID（再配信で 2 通送らない。同じ見積りの DE-03 と DE-21 のように ID を共有する別の契機と混ざらない） |

## 決定（推奨のとおり。人にしか決められない判断は含まない）

- 導線: 見積りと経路は `/customer/transport-requests/{業務番号}`、予約は `/customer/bookings`、追跡は `/customer/tracking-records/{追跡番号}`。
- 荷主企業を持たないイベントでは送らず警告に残す（通知を使わない以前の発行と、輸送要求が読めなかった割当て）。

## 計画

- [x] (1) テストを先に書き失敗を確かめた（`ShipperNotificationEventHandlerTest` 6、`QuotationTest`・`QuotationShipperApprovalTest` に各 1）
- [x] (2) 実装、既存の DE-18 の比べ（4 件）を企業 ID つきに直した、Mailpit を使う統合テスト（`ShipperNotificationIntegrationTest`）
- [ ] (3) 品質ゲート・Sonar・文書

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
