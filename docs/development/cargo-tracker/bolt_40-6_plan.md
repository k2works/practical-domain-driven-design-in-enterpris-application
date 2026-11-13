---
type: Plan
title: "Bolt 40-6 計画 - 有人案件の保存（4 表・受付番号の採番・リポジトリ）"
description: "W8 の 7 本目（機能、SP 0、200 分）。service_case・service_case_source・service_case_progress・service_case_answer と採番の表、リポジトリ、楽観ロック。スキーマの変更を含む。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T08:00:00Z }
---

# Bolt 40-6 計画 - 有人案件の保存（4 表・受付番号の採番・リポジトリ）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-6 回（W8 の 7 本目。[W8 計画](iteration_plan-W8.md)） |
| 状態 | 計画（**承認済み**。2026-11-13 にスキーマの内容を人に示し、「この内容で承認」を得た。human:kakimomokuri） |
| 種別・SP | 機能・SP 0 |
| 見積り | **200 分**（作業のみ）。類推元は Bolt 39a2（145 分。1 表）で、4 表なので大きい（推測） |
| トレース | data_model の `service_case` 系、SC-INV-01〜03、Bolt 40-5 の `ServiceCase` |
| 承認ゲート | **スキーマ**（承認済み）。認可・外部連携はない |

## Bolt ゴール

`ServiceCase` を保存・読み出し・更新できる。受付番号は年ごとの連番で採番し、作成が失敗すれば戻る（欠番なし）。更新は楽観ロックで、同時の更新の負けは競合として拾う。

## スキーマ（承認済み。V20261118100000）

- `tracking.service_case_number_counter`（`number_year` 主キー、`last_no` 0 以上）。Bolt 17 の案件番号の採番と同じ方式（呼び元のトランザクションの中で行ロックして増やし、年の行は別のトランザクションで用意する）。
- `tracking.service_case`: data_model のとおり。`kind` 7 種・`status` 6 種・`priority` 2 種の CHECK。対応中・回答済み・終結は担当者と受領時刻を持つ CHECK、終結かつ復旧照合が必要なら照合 ID を持つ CHECK（SC-INV-02）、照合 ID があれば復旧照合が必要の CHECK。索引（`status`, `receive_due_at`）と（`shipper_company_id`）。予約・追跡番号・荷主企業は他のコンテキストの参照なので外部キーを持たない（登録拒否の案件は知らない追跡番号を持ち得る）。
- `service_case_source`・`service_case_progress`・`service_case_answer`: 案件への外部キーつきの子の表。**data_model との差: `service_case_source` に `acquired_at`（NOT NULL）を足す**（共有カーネルの `Source` が取得時刻を必須にするため）。回答は顧客の回答とその時刻をそろえる CHECK。

## 決定

(1) **集約の追加**: `version`（楽観ロックの版）と `reconstitute(...)`（保存された状態から組み立てる入口）を足す。リポジトリは `ServiceCaseRepository`（`save(ServiceCase, UserId)`・`update(ServiceCase, UserId)`・`findByNumber`）。作成者・更新者は集約に持たず、呼び出しの操作者を渡す（表の `created_by`・`updated_by`）。

(2) **子の更新**: 進捗と回答は追記専用。更新では、表にない番号の行だけを足し、既存の回答には顧客の回答（未設定のときだけ）を書く。過去の回答・進捗は消さない。

(3) **受付番号**: `SC-YYYY-NNNN`（年と 4 桁の連番）。`CaseNumber.of(year, serial)`。

## 状態の組み合わせ

| 行 | 状況 | 期待 |
| :--- | :--- | :--- |
| 1 | 受付番号の採番 | 同じ年は 1 から連番。年が変われば 1 から。作成が失敗すれば戻る（呼び元のトランザクションの中） |
| 2 | 起票した案件の往復 | 種類ごとに、保存して読み直すと同じ（出典・任意の項目を含む） |
| 3 | 受領・進捗・回答・終結・再開の更新の往復 | 進捗・回答が追記され、過去の分は変わらず、再開で顧客の回答が最後の回答に付く |
| 4 | 古い版で更新 | `ConcurrentServiceCaseUpdateException`。表は変わらない |
| 5 | CHECK の負例 | 種類・状態・優先度の範囲外、担当者なしの対応中、照合なしの復旧照合必要の終結、照合 ID だけある、顧客の回答だけある回答は拒む |
| 6 | ない受付番号 | 空 |

## ステップ

| ステップ | 内容 | 分 |
| :--- | :--- | ---: |
| 1 | Red: 行 1〜6（PostgreSQL の統合テスト）と集約の追加分 | 60 |
| 2 | Green: マイグレーション、集約の追加、リポジトリ、採番 | 90 |
| 3 | 設計文書、品質ゲート | 50 |

## 完了条件

- [ ] 行 1〜6 が緑（H2 は dev プロファイルの起動で移し替えが通る）
- [ ] `check documentationTest` と SonarQube が緑
- [ ] 終了報告に見積り／実績／差を書いた

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成。スキーマの内容を人に示して承認（human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
