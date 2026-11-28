---
type: Plan
title: "Bolt 40-6 終了報告 - 有人案件の保存（4 表・受付番号の採番・リポジトリ）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T08:30:00Z }
---

# Bolt 40-6 終了報告 - 有人案件の保存（4 表・受付番号の採番・リポジトリ）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **スキーマの変更**（計画の段階で内容を示して承認済み）: `V20261118100000` で `tracking.service_case_number_counter`・`service_case`・`service_case_source`・`service_case_progress`・`service_case_answer` を足した。data_model との差は `service_case_source.acquired_at`（NOT NULL）だけ（承認済み。data_model も同じコミットで直した） | 承認 | マイグレーション |
| 2 | **計画から足した制約と印（承認時の内容を超える部分）**: 計画のスキーマの範囲に、次を足した。(a) 表の印: `service_case_source`・`service_case_progress` は `[append-only]`、`service_case_answer` は `[no-delete]`（顧客の回答を後から書くので更新は許す）。**これでアプリケーション利用者は出典・進捗の更新と削除ができず、回答の削除ができない**（`afterMigrate` の権限の定め）。(b) 子の 3 表の `case_number` の列のコメント。どちらも既存の規約（`AppendOnlyGrantIntegrationTest`・`SchemaCommentIntegrationTest`）が最初の `check` で落ちて気づいた。テストの表の一覧にも足した | 承認 | 計画の見落とし |
| 3 | **集約の追加**: `ServiceCase` に `aggregateVersion`（楽観ロックの版）と `reconstitute(...)`（保存された状態からの組み立て）を足した。作成者・更新者は集約に持たず、リポジトリの呼び出しの操作者を表の `created_by`・`updated_by` に書く | 承認 | 計画 決定 (1) |
| 4 | **子の更新**: 進捗と回答は追記専用で、表にない番号の行だけを足す。既存の回答には顧客の回答（未設定のときだけ）を書く。過去の進捗・回答は消さない。出典は起票のときだけ書く | 承認 | 計画 決定 (2) |
| 5 | **受付番号**: `SC-YYYY-NNNN`（`CaseNumber.of`）。採番は経路設計の案件番号と同じ方式（呼び元のトランザクションの中で年の行を行ロックして増やし、年の行は別のトランザクションで用意する）。起票が失敗すれば番号も戻る | 承認 | `MyBatisServiceCaseNumberIssuer` |
| 6 | **規律の違反（私の側）**: `cat > … <<EOF` という禁止の形を 1 回書いた（空のファイルを作って消しただけで実害なし）。W8 でこれまでに計 4 回 | 承認 | — |
| 7 | **流していないもの**: `uiTest`（画面は触れない）。CI・デモ環境の配備は、あなたの指示「区切りごとに push する」で Bolt 40-5 の承認までを push した。この Bolt はこの報告の承認後に push する | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-6 回（W8 の 7 本目） |
| 期間 | 2026-11-13 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 200 分。**実績は約 140 分（概算。時刻を厳密には記録していない）。差は約 −60 分**。壁時計は `check documentationTest` 2 回・Sonar 3 回を含む |
| GitHub | 変えていない |
| 計画 | [Bolt 40-6 計画](bolt_40-6_plan.md) |

## 成果

`ServiceCaseRepository`（`save`・`update`・`findByNumber`）、`ServiceCaseNumberIssuer`、`ConcurrentServiceCaseUpdateException`、MyBatis の実装（マッパー・行・採番）。`MyBatisServiceCaseRepositoryIntegrationTest`（PostgreSQL）が計画の行 1〜6 を確かめる（採番の連番と年の切り替え、7 種類の起票の往復、受領から再開までの 5 回の更新の往復と更新者・作成者、古い版の更新の競合、CHECK の負例 7 件、ない受付番号）。`CaseNumberTest` が受付番号の形を確かめる。H2 は dev プロファイルの起動を含む `check` の中で移し替えが通った。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest`（`uiTest` は流さない） | 1 回目は追記専用の印と列のコメントの規約で落ち、直した。**2 回目は BUILD SUCCESSFUL（7 分 29 秒）** |
| SonarQube | 1 回目はテストのラムダの指摘 4 件で FAIL。直して **2 回目は PASS**（新しいコードのカバレッジ 96.2%、重複 0.016%、違反 0） |

## 完了条件

- [x] 行 1〜6 が緑（H2 は dev プロファイルの起動で移し替えが通る）
- [x] `check documentationTest` と SonarQube が緑
- [x] 見積り／実績／差を書いた

## 申し送り

- Bolt 40-7: 起票と受領のコマンド（US-20 AC1・AC2）。認可（誰が起票・受領できるか）を計画の段階で示して承認を取る。`BusinessCalendarRepository` と `ServiceDeadlinePolicy`（Bolt 40-4）、`ServiceCaseNumberIssuer` と `ServiceCaseRepository` をアプリケーションサービスで束ねる。
- 荷主の照会範囲（`shipper_company_id` の索引）は 40-10・40-15 で使う。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成（承認待ち） | anthropic/claude-sonnet-5-5 |
| 2026-11-13 | 議題をすべて推奨のとおり承認として記録した（human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
