---
type: Plan
title: "Bolt 40-4 終了報告 - 営業日カレンダーと受領・回答期限の計算（SC-INV-04）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T05:30:00Z }
---

# Bolt 40-4 終了報告 - 営業日カレンダーと受領・回答期限の計算（SC-INV-04）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **スキーマの変更**: `tracking.business_calendar` を足した（`V20261117100000`。`business_day` が真なら `open_time < close_time`、偽なら時刻なしの CHECK `ck_business_calendar_hours`、主キー `calendar_date`）。2026・2027 年の全日 730 行の見本を入れた。H2 と PostgreSQL の共通の構文 | 承認 | 計画 決定 (4) |
| 2 | **祝日の見本は業務責任者の確認が要る（台帳 D-157。仮）**: 日本の祝日は内閣府の定めから私が作った（2026-05-06 と 2027-03-22 の振替休日、2026-09-22 の国民の休日を含む）。誤りがあれば翌年の登録（W10 の `calendar:register`）の前に直す。出どころの外部ライブラリは入れていない（CP-6） | 承認（確認は業務責任者） | `V20261117100000` |
| 3 | **期限の値は仮（台帳 D-156）**: 優先度高は受領 1 営業時間・回答は営業日の終了、通常は受領 4 営業時間・回答は同じ時刻の 2 営業日後。non_functional の SVC-01・02 は状態が「提案」。`ServiceDeadlinePolicy.provisional()` の 1 か所に置いた（CP-8）。**数え方の細部（営業時間は終了未満、通常の回答は同じ時刻の N 営業日後）は私が決めた仮定**で、SVC-01〜03 の文には書かれていない | 承認 | 計画 決定 (2) |
| 4 | **次回更新日時 = 受領期限**とした。営業時間外の優先度高は翌営業日の 10:00 で SVC-05 と一致する。営業時間内の受付の次回更新日時の定めは文書になかったので、受領期限を使う仮定 | 承認 | `ServiceDeadlines.nextUpdateAt` |
| 5 | **カレンダーにない日は失敗**: 黙って平日にせず `BusinessCalendarNotRegisteredException`（SVC-07: 翌年分の登録漏れに気づくため） | 承認 | 行 13 |
| 6 | **用語集**: `営業日`（`BusinessDay`）と `受領・回答期限`（`ServiceDeadlines`）を足し、Javadoc を用語集の名前で始めた（`LivingGlossaryConsistencyTest` が最初の `check` で落ちて気づいた。`documentationTest` は `check` に入らないので、`check documentationTest` で確かめ直した） | 承認 | domain_model |
| 7 | **流していないもの**: `uiTest`（画面は触れない）、CI・デモ環境の配備（push していない） | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-4 回（W8 の 5 本目。40-3 は確認の場の回答待ちで飛ばした） |
| 期間 | 2026-11-13 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 180 分。**実績は約 150 分（概算。時刻を厳密には記録していない）。差は約 −30 分**。壁時計は `check` 2 回・Sonar 3 回（約 10 分ずつ）を含む |
| GitHub | 変えていない |
| 計画 | [Bolt 40-4 計画](bolt_40-4_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1・2. 規則 | Red（コンパイルで落ちる）→ `BusinessCalendar`・`BusinessDay`・`ServicePriority`・`ServiceDeadlines`・`ServiceDeadlinePolicy` で Green。デシジョンテーブル 13 行（12 行 + 登録漏れ）と型の不変条件が緑 | `94ba6a6e2` |
| 3. 表と見本 | 表・見本・`BusinessCalendarRepository`・MyBatis の実装。PostgreSQL の統合テスト 9 件（全日 730 行、祝日・年末年始・振替、2026 年 11 月の営業日 19、CHECK 3 種と二重登録の拒否、読んだ見本での期限） | `74f982192` |
| 4. 文書・台帳・品質ゲート | data_model・domain_model・用語集、台帳 D-156・D-157 | 文書のコミットと修正のコミット |

MyBatis の `resultMap` で、コンストラクターの引数は boolean を `_boolean`（プリミティブ）にし、名前を全部に付ける必要があった（前例 `IdentityMapper.xml`）。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest`（`uiTest` は流さない） | BUILD SUCCESSFUL（7 分 56 秒） |
| SonarQube | **1 回目は FAIL**（新しい違反 3 件: テストのラムダに例外を投げ得る呼びが複数）。引数を先に作って直し、**2 回目は PASS**（新しいコードのカバレッジ 96.2%、重複 0.017%、違反 0） |

## 完了条件

- [x] 行 1〜13 と型の不変条件が緑
- [x] 表の往復と見本の全日がそろう確かめが PostgreSQL で緑（H2 は dev プロファイルの起動を含む `check` の中で移し替えが通った）
- [x] `check` と SonarQube が緑
- [x] 見積り／実績／差を書いた

## 申し送り

- 業務責任者への確認: D-156（期限の値と数え方）、D-157（祝日の見本）。
- W10: 運用タスク `calendar:register`（翌年分の登録。SVC-07）。
- この Bolt の規則は有人案件の集約（Bolt 40-5）が使う。`BusinessCalendarRepository` は読むだけで、アプリケーションサービスからの利用は 40-7 で組む。
- 規律: 今回の Bolt では標準入力につながる形を使っていない。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成（承認待ち） | anthropic/claude-sonnet-5-5 |
