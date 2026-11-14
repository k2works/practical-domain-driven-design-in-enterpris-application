---
type: Plan
title: "Bolt 43 終了報告 - 荷主への業務の通知の基盤（US-22）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T11:00:00Z }
---

# Bolt 43 終了報告 - 荷主への業務の通知の基盤（US-22）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **構造の決定（人が決めた）**: 宛先の解決は identity が担い、identity が業務のイベントを購読して通知の公開 API を呼ぶ。共有カーネルにポートを足さない。ADR-017 に補足を足した | 承認（人の決定） | [計画](bolt_43_plan.md)、ADR-017 |
| 2 | **宛先（台帳 D-166。仮）**: その企業の利用中の荷主担当者全員。業務責任者に確かめる | 承認（仮） | release_plan D-166 |
| 3 | **外部連携**: メール送信は既存の送信のポートと SMTP を使う。新しい外部ライブラリは入れていない。本文は件名と画面への導線だけで、料金・契約条件・書類を含まないことを統合テスト（Mailpit）で固定した | 承認 | 計画 |
| 4 | **スキーマの変更はない**（`notification.kind` の CHECK に先に置いてあった 5 値を使う） | 承認 | 計画 (5) |
| 5 | 範囲: このBolt は口・宛先の解決・本文まで。イベントの購読と、イベントへの荷主企業 ID の追加は 43-1・43-2 | 承認 | 計画 (6) |
| 6 | **作業の流儀（私の側）**: 標準入力につながる形（空の heredoc）をまた 1 回書いた（何も読まず何も変えていない）。以後は Edit/Write だけを使う | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 43 回（W9。機能。US-22 の土台） |
| 期間 | 2026-11-14 |
| SP | 0（US-22 の 3 SP は 43-3 で数える） |
| 見積り／実績 | 見積り 200 分。実績は概算 120 分 |
| 計画 | [Bolt 43 計画](bolt_43_plan.md) |

## 成果

通知の公開 API `ShipperNotificationMail`（要求・結果・アダプター・サービス・文面のポート・Thymeleaf のテンプレートと 5 種の本文）、共有カーネルの `ShipperNotificationKind`、`NotificationKind` の 5 値、identity の ACL `NotificationShipperMails` と `ShipperNotifications`（利用中の荷主担当者全員へ。失敗は残りの宛先を止めない）。テスト: `ShipperNotificationMailIntegrationTest`（Mailpit。種類ごとの件名と導線、本文に料金等を含まない、2 回目は送らない）、`ShipperNotificationsTest` 3。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | BUILD SUCCESSFUL（31 分 8 秒） |
| SonarQube | **PASS**（新しい違反 0、カバレッジ 95.9%） |

## 申し送り

43-1: 見積りの提示・経路の承認・本予約の確定・追跡が確認中のイベントを identity が購読して送る（イベントに荷主企業の ID を足す。`allowedDependencies` に予約・追跡のイベントを足す）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
