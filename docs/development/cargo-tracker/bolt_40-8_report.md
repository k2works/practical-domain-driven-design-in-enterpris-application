---
type: Plan
title: "Bolt 40-8 終了報告 - 有人案件の進捗・回答・終結・再開のコマンド（認可）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T10:40:00Z }
---

# Bolt 40-8 終了報告 - 有人案件の進捗・回答・終結・再開のコマンド（認可）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **認可の仮の定め（台帳 D-159 の拡張。計画の段階で内容を示して承認済み）**: 進捗の更新・回答の記録・終結・回答期限の変更は社内の運用役割 5 つ（**担当者だけには限らない**。確かめる点）。再開は社内の運用役割 5 つがどの案件も、荷主担当者は自社の案件だけ（US-23 AC2。他社・ない受付番号は同じ「見つからない」）。システム管理者・監査担当者は不可 | 承認（確認は業務責任者） | 計画 認可の定め |
| 2 | **回答期限の変更を足した**: W8 計画の「回答期限を超える場合の理由・新しい期限の記録（UC-20 6a）」を、`ServiceCase.reviseAnswerDue`（理由必須、新しい期限は今の期限より後、進捗として追記）にした。**使用例の「6a」は docs の system_usecase に見つからず、W8 計画の注記に従った仮の解釈**。スキーマの変更はない（`answer_due_at` の更新を SQL に足した） | 承認 | 計画 決定 (3) |
| 3 | **結果の型を 1 つに共有**: `ServiceCaseActionOutcome`（`Done`・`NotPermitted`・`NotFound`・`InvalidState`・`InvalidInput`・`ReconciliationIncomplete`・`Conflict`）。状態に合わない操作は `InvalidState`、空の内容や期限の誤りは `InvalidInput`。サービスは `IllegalStateException`・`IllegalArgumentException` を結果に変える | 承認 | `ServiceCaseActionService` |
| 4 | **作業の流儀**: 回答期限の変更は、テストと実装を同じ手番で書いた（Red を先に確かめなかった）。TDD の三原則から外れた。テストは通っているが、手順として記録する | 承認 | — |
| 5 | **品質ゲートで直したもの**: ArchUnit の規約（コマンドは 1 クラスごとに `@Command`）で入れ物のクラスをやめ、コマンド 5 つを個別のファイルにした。Sonar の `record` パターン。テストの誤り（同じ内容の照会を 2 件起票して重複になった） | 承認 | — |
| 6 | **自律実行の指示**: 「以降 Recommended で自律実行して」（human:kakimomokuri）に従い、この報告は推奨のとおり承認として記録し、区切りで push する | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-8 回（W8 の 9 本目） |
| 期間 | 2026-11-13 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 200 分。**実績は約 100 分（概算。時刻を厳密には記録していない）。差は約 −100 分** |
| GitHub | 変えていない |
| 計画 | [Bolt 40-8 計画](bolt_40-8_plan.md) |

## 成果

`ServiceCaseActionService`（`updateProgress`・`recordAnswer`・`close`・`reopen`・`reviseAnswerDue`）、コマンド 5 つ、`ServiceCaseActionOutcome`、`ServiceCaseAuthorization` の `canOperate`・`canReopen`・`canAccess`、`ServiceCase.reviseAnswerDue`、更新 SQL の `answer_due_at`、`TrackingConfiguration` の Bean。`ServiceCaseActionServiceTest` が行 1〜14、`ServiceCaseTest` が行 13 を確かめる。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest`（`uiTest` は流さない） | BUILD SUCCESSFUL（8 分 49 秒） |
| SonarQube | 1 回目は `record` パターンの指摘 3 件で FAIL。直して **2 回目は PASS**（新しいコードのカバレッジ 96.3%、重複 0.016%、違反 0） |

## 完了条件

- [x] 行 1〜14 と集約の行 13 が緑
- [x] `check documentationTest` と SonarQube が緑
- [x] 見積り／実績／差を書いた

## 申し送り

- 起票・受領・進捗・回答・終結・再開の監査記録は足していない（要るかを諮る。W8 計画の CP-4 は監査の一覧の画面を W11 にした）。
- Bolt 40-9: escalation（受領期限超過の検知、上位の運用責任者への通知、依頼元への担当未確定の表示）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri。「以降 Recommended で自律実行して」の指示） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
