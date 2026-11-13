---
type: Plan
title: "Bolt 40-15 終了報告 - 照会の記録（S-23）と有人案件への引き継ぎ（US-11 AC2）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T15:00:00Z }
---

# Bolt 40-15 終了報告 - 照会の記録（S-23）と有人案件への引き継ぎ（US-11 AC2）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **スキーマの変更**: 表 `tracking.inquiry_record` を足した（`V20261119100000`）。data_model の定義どおり、**差は 2 つ**: `created_at`（NOT NULL）を足し、`escalated` が真なら `case_number` が要る CHECK を付けた。`case_number` は `service_case` への外部キー（荷主企業・追跡番号・利用者は他のコンテキストの参照なので外部キーなし）。**追記専用の印は付けていない**（W9 の IR-INV-01 が `escalated` を更新するため）。列の注釈・CHECK の統合テストは緑 | 承認 | 計画 決定 (1) |
| 2 | **認可の追加**: `/staff/inquiries/**`（S-23）は**カスタマーサポートだけ**（ui_design の定め）。ほかの運用役割（営業担当者・経路設計者・追跡管理者・データ責任者）とシステム管理者は 403。統合テスト（本物のフィルター）で確かめた。起票は 40-7 の認可（D-159）のまま | 承認 | 計画 決定 (4) |
| 3 | **追跡番号を必須にした**: 照会は追跡番号で受け、荷主企業は追跡記録から決めて担当企業の範囲で確かめる。**荷主企業を選ぶ欄は作らない**（企業名を引く公開 API がないため）。範囲の外とない追跡番号は同じ文 | 承認 | 計画 決定 (2) |
| 4 | **引き継ぎの規則（台帳 D-162。仮）**: 同じトランザクションで有人案件（種類 顧客照会、優先度は通常に固定、担当部門は入力・既定 CS）を起票し、受付番号と期限を結果に示す。**未終結の同じ内容の案件があれば新しく起票せずその受付番号に引き継ぐ**。営業カレンダーが未登録なら何も書かない。業務責任者に確かめる（優先度の固定、カスタマーサポートだけが記録できること） | 承認（仮） | release_plan D-162 |
| 5 | **US-11 の受入条件は AC1・AC2・AC3 のすべてを満たした**（AC1: 40-10、AC3: 40-10、AC2: 本 Bolt）。SP 3 を数える（40-16 の受入条件の照合で数え直す） | 承認 | 申し送り |
| 6 | **作業の流儀（私の側）**: 標準入力につながる形（空の heredoc）を 1 回また誤って実行した。何も読まず何も変えていない | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-15 回（W8 の 16 本目） |
| 期間 | 2026-11-14 |
| 種別・SP | 機能・SP 3（US-11） |
| 見積り／実績 | 見積り 210 分。**実績は約 200 分（概算）、差は約 −10 分**（画面と移行が新規で、画面の層のラジオの操作に手間取った） |
| 計画 | [Bolt 40-15 計画](bolt_40-15_plan.md) |

## 成果

移行 `V20261119100000__create_tracking_inquiry_record.sql`、`InquiryRecord`（集約）・`InquiryRecordRepository`・`MyBatisInquiryRecordRepository`、`InquiryRecordingService`（記録と引き継ぎ）、S-23（`InquiryRecordController`・`InquiryForm`・`InquiryFormConverter`・`new.html`）、認可とナビ、マニュアル 13.3。テスト: `InquiryRecordingServiceTest` 7、`InquiryRecordControllerTest` 9、リポジトリの統合 4、セキュリティの統合 1、画面のシナリオ 3（`inquiry_record_ui.feature`。実 PostgreSQL で外部キーまで）。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest uiTest` | （結果は更新履歴） |
| SonarQube | （結果は更新履歴） |

## 完了条件

- [x] 上のテストが緑
- [ ] `check documentationTest uiTest` と SonarQube が緑
- [x] 設計文書とマニュアルを同じ Bolt で更新した
- [x] 見積り／実績／差を書いた

## 申し送り

- Bolt 40-16: W8 の受入条件の照合（US-11・US-20 の `@US-11`・`@US-20` のタグと受入条件）、マニュアル・動画の監査（13 章のキャプチャ、S-06 の引き継ぎの文）、UC-06 3a の画面の層のシナリオ。
- 照会記録の一覧・KPI-02 の集計・顧客 Web の照会の記録（IR-INV-01）は W9・W10。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri。「以降 Recommended で自律実行して」の指示） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
