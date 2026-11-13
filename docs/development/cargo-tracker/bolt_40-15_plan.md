---
type: Plan
title: "Bolt 40-15 計画 - 照会の記録（S-23）と有人案件への引き継ぎ（US-11 AC2）"
description: "W8 の 16 本目（機能、SP 3（US-11）、210 分）。カスタマーサポートが電話・メールで受けた照会を記録し（IR-INV-02）、必要なら有人案件へ引き継ぐ（受付番号を発行）。表 inquiry_record を足す（スキーマの確認）。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T12:00:00Z }
---

# Bolt 40-15 計画 - 照会の記録（S-23）と有人案件への引き継ぎ（US-11 AC2）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-15 回（W8 の 16 本目。[W8 計画](iteration_plan-W8.md)） |
| 状態 | 計画（**承認済み**。「以降 Recommended で自律実行して」（human:kakimomokuri）により、推奨のとおり承認として記録。**スキーマの変更（表 `inquiry_record`）と認可（S-23）を含む**） |
| 種別・SP | 機能・**SP 3（US-11 を閉じる）** |
| 見積り | **210 分**（作業のみ）。類推元は Bolt 39d3（210 分） |
| トレース | US-11 AC2、IR-INV-02、KPI-02（分母の記録）、UC-09、ui_design S-23 |
| 承認ゲート | **スキーマ**（表 `tracking.inquiry_record` を足す。data_model :774 の定義どおり）、**認可**（`/staff/inquiries/**` はカスタマーサポートだけ）。いずれも承認済み |

## 決定

(1) **スキーマ**（data_model の定義どおり。外部キーは `case_number` → `tracking.service_case` だけ。荷主企業・追跡番号・利用者は他のコンテキストの参照なので外部キーを持たない）: `id`（PK）、`channel`（`WEB_SELF`・`WEB_INQUIRY`・`PHONE`・`EMAIL`。S-23 は `PHONE`・`EMAIL` だけを書く。残りは US-21〜23 の W9）、`shipper_company_id`（NOT NULL）、`tracking_number`、`inquirer_kind`（`SHIPPER`・`CONSIGNEE`）、`inquirer_user_id`、`received_at`、`escalated`（有人対応へ移ったか。CHECK: 真なら `case_number` がある）、`case_number`、`recorded_by`、`created_at`。索引は `(shipper_company_id, received_at)`（KPI-02 の週次集計）。追記専用の印・権限の剥奪は付けない（W9 の IR-INV-01 が `escalated` を更新するため）。列にすべて日本語の注釈を付ける。H2 と PostgreSQL の共通の構文（ADR-007）。

(2) **S-23 の入力**: 経路（電話・メール）、追跡番号（必須。照会は追跡番号で受ける。荷主企業は追跡記録から決め、**担当企業の範囲**で確かめる。範囲の外とない追跡番号は同じ文）、照会者の種別（荷主・荷受人）、受付時刻（日本時間 `yyyy-MM-dd HH:mm`。空なら今）、照会内容、参照した出典（任意）、担当部門（引き継ぐとき。既定 CS）、**有人案件へ引き継ぐ**（チェック）。

(3) **記録と引き継ぎ**（`InquiryRecordingService.record`）: 引き継がないときは照会記録だけを書く（IR-INV-02: 引継ぎが不要でも記録する）。引き継ぐときは、同じトランザクションで有人案件（種類 顧客照会、優先度 通常、対象の荷主企業・追跡番号、内容、参照した出典、担当部門）を起票し（受付番号・回答期限・次回更新日時は起票の結果。`ServiceCaseCommandService.open`）、照会記録に `escalated` と受付番号を書く。**重複（未終結の同じ内容）の案件があるときは新しく起票せず、既存の受付番号に引き継ぐ**。営業カレンダーが未登録なら何も書かず、その旨を示す。引き継ぐのに照会内容が空ならスキーマ以前の入力の誤り（有人案件の必須の原因）として示す。

(4) **認可**: S-23 はカスタマーサポートだけ（ui_design の定め。`SecurityConfiguration` に `/staff/inquiries/**` を足す）。起票は 40-7 の認可（D-159）のとおり。

(5) **結果**: 引き継ぎなし「照会を記録しました。」、引き継ぎあり「照会を記録し、有人案件 SC-… として引き継ぎました。回答期限は …、次回更新は … です。」（S-15 へのリンク）。重複は「未終結の同じ内容の有人案件 SC-… があるため、その案件に引き継ぎました。」。

(6) **画面の URL**: `GET /staff/inquiries/new`（フォーム）、`POST /staff/inquiries`（PRG で S-23 へ戻る）。ナビに「照会の記録」をカスタマーサポートに出す。S-23 は照会記録の一覧を持たない（KPI の集計は W9・W10）。

## 状態の組み合わせ

`InquiryRecordingServiceTest`（記録のみ、引き継ぎ、重複、範囲の外・ない追跡番号、内容が空、カレンダー未登録、権限）、`InquiryRecordControllerTest`、`MyBatisInquiryRecordRepositoryIntegrationTest`（保存と読み直し、CHECK）、セキュリティの統合テスト（カスタマーサポートだけ）、画面のシナリオ（S-23 で記録して引き継ぎ → S-15 で受領できる。`@US-11`）。

## ステップ

| ステップ | 内容 | 分 |
| :--- | :--- | ---: |
| 1 | テスト（サービス、コントローラー、リポジトリ、セキュリティ、画面のシナリオ） | 70 |
| 2 | 移行、集約・ポート・MyBatis、サービス、コントローラー、テンプレート、認可、ナビ | 90 |
| 3 | 設計文書・マニュアル・台帳、品質ゲート（`uiTest` を含む） | 50 |

## 完了条件

- [ ] 上のテストが緑
- [ ] `check documentationTest uiTest` と SonarQube が緑
- [ ] 設計文書とマニュアルを同じ Bolt で更新した
- [ ] 終了報告に見積り／実績／差を書いた

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
