---
type: Plan
title: "Bolt 42-2 計画 - 荷主の問い合わせ一覧（C-14。US-23 AC1・AC4）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T09:00:00Z }
---

# Bolt 42-2 計画 - 荷主の問い合わせ一覧（C-14）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-23 AC1・AC4。SP 0。見積り 200 分）。スキーマの変更・外部連携・認証の変更はない。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | 荷主企業の問い合わせの読み出し `ServiceCaseRepository.findByShipper`（起票の新しい順。既存の索引 `ix_service_case_shipper` を使う） |
| 2 | `ServiceCaseQueryService.customerList`（受付番号・状態・次回更新日時・最新の回答。上限 200 件、打ち切りを示す） |
| 3 | 画面 C-14 `GET /customer/inquiries`（既存の顧客レイアウトのナビの「問い合わせ」）。受付時間（AC4）を荷主の日時表示と同じ時間帯で示す |
| 4 | マニュアル 14 章（新規。14.1 一覧）。ui_design の C-14、台帳 D-164（受付時間。仮） |

## 決定（推奨のとおり。「以降、推奨で自律実行」の指示による記録）

- 認可は既存の URL の規則（`/customer/**` は荷主担当者だけ）で足りる。サービスも荷主担当者でなければ空の一覧を返す。
- 受付時間は「平日 09:00〜18:00」（既定の営業カレンダーと同じ）を固定で示す。祝日・年末年始は示さない（営業カレンダーの登録は W10）。業務責任者に確かめる（台帳 D-164。仮）。
- **スクリーンキャプチャと画面の受入シナリオ（`@ui`）は 42-3 で、一覧と詳細をまとめて撮る**（詳細ができてから一連の流れで通すため）。マニュアル 14 章は文章を先に入れた。

## 計画

- [x] (1) テストを先に書き、失敗を確かめた（`ServiceCaseQueryServiceTest` 行 8e・8f と `CustomerServiceCaseControllerTest` 5 件）
- [x] (2) 実装、統合テスト（`MyBatisServiceCaseRepositoryIntegrationTest` 行 7）
- [x] (3) 品質ゲート・Sonar・文書

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
