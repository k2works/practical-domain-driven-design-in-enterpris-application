---
type: Plan
title: "Bolt 43-1 終了報告 - 4 つの契機の通知（US-22 AC1）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T12:00:00Z }
---

# Bolt 43-1 終了報告 - 4 つの契機の通知（US-22 AC1）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **構造の変更（実施）**: Bolt 43 で人が決めた方式（identity が購読して送る）の実施として、`tracking.domain.events` を名前付きインターフェース `events` として公表し、identity の `allowedDependencies` に `booking :: events`・`tracking :: events` を足した。ModularityTest・ArchUnit は通っている | 承認 | [計画](bolt_43-1_plan.md)、ADR-017 の補足 |
| 2 | **イベントの契約の変更**: DE-03・DE-21 に荷主企業の ID と業務番号、DE-18 に荷主企業の ID を足した（足す前の形の構成子を残した。足す前の発行は null で通知しない。発行記録は JSON なので、新しい項目は以後の発行から入る） | 承認 | 計画 (2) |
| 3 | 通知の導線は見積依頼の詳細・予約一覧・追跡の照会の結果。宛先は台帳 D-166（仮） | 承認（仮） | release_plan D-166 |
| 4 | **範囲**: 期限の 24 時間前の通知（AC2）は 43-2、通知一覧（C-16。AC4 の「画面から確認」）は 43-3 | 承認 | 計画 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 43-1 回（W9。機能。US-22 AC1） |
| 期間 | 2026-11-14 |
| SP | 0（US-22 の 3 SP は 43-3 で数える） |
| 見積り／実績 | 見積り 220 分。実績は概算 130 分 |
| 計画 | [Bolt 43-1 計画](bolt_43-1_plan.md) |

## 成果

`ShipperNotificationEventHandler`（4 契機。契機の ID は名前ベースの UUID）、DE-03・DE-21・DE-18 の荷主企業の ID の追加、`Quotation.notifying`、`QuotationCommandService.present` と `RouteAssignmentService.assign` の宛先の受け渡し、tracking のイベントの公表、identity の依存。テスト: `ShipperNotificationEventHandlerTest` 6、`ShipperNotificationIntegrationTest`（本物の PostgreSQL と Mailpit。利用中の荷主担当者だけに届き、同じイベントは 1 通）、`QuotationTest`・`QuotationShipperApprovalTest` に各 1。DE-18 の比べ 4 件を直した。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | 1 回目は `RouteConfirmedAssignmentIntegrationTest` で失敗（発行記録は listener ごとに 1 件なので、DE-21 に identity の通知の listener が加わって完了の件数が 3 から 4 になった。期待を直した）。流し直して BUILD SUCCESSFUL（31 分 7 秒） |
| SonarQube | 実行結果を追記する |

## 申し送り

- 43-2: 見積りの有効期限の 24 時間前の通知（検出の仕組み。二重に送らない）。
- 43-3: 通知一覧（C-16）、マニュアル、US-22 を締める。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
