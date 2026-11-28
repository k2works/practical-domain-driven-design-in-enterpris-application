---
type: Plan
title: "Bolt 40-7 終了報告 - 有人案件の起票と受領のコマンド（認可）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T09:30:00Z }
---

# Bolt 40-7 終了報告 - 有人案件の起票と受領のコマンド（認可）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **認可の仮の定め（台帳 D-159。計画の段階で内容を示して承認済み）**: 起票は社内の運用役割 5 つがどの種類も、荷主担当者は自社の顧客照会・見積り相談だけ。受領は社内の運用役割 5 つだけ。ui_design の権限表に役割ごとの定めがなかったので私が置いた。業務責任者の確認が要る。役割の集合は `ServiceCaseAuthorization` の 1 か所 | 承認（確認は業務責任者） | 計画 認可の定め |
| 2 | **起票の順**: 認可 → 必須項目 → 重複 → 期限の計算 → 採番 → 保存。先の段階で止まれば受付番号を使わない（テストで確かめた） | 承認 | 計画 決定 (1) |
| 3 | **重複の定義は私の解釈**: 同じ荷主企業・予約・内容（前後の空白を除く）で、終結・重複終了でない案件。荷主企業・予約が空の入力は空の案件と比べる（NULL の比べ方を PostgreSQL で確かめた）。US-20 の文は「同じ顧客・予約・照会内容」だけで、この定義は文書にない | 承認 | 計画 決定 (2) |
| 4 | **範囲を超えたもの・足りないもの**: 認可は「他社の案件は存在を明かさない」を、荷主担当者が自社の分しか起票・重複判定できないこと（他社を指すと `NotPermitted`）で守った。**荷主担当者の照会・再開（SC-INV-05）は照会の Bolt（40-10・40-15）で扱う**。監査の記録（起票・受領の監査）はこの Bolt で足していない。要るかは別に諮る | 承認 | 申し送り |
| 5 | **スキーマ・外部連携の変更はない**（重複の検索は既存の表の読み出し）。`uiTest` は流していない（画面は触れない） | 承認 | — |
| 6 | **規律**: この Bolt では標準入力につながる形を使っていない | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-7 回（W8 の 8 本目） |
| 期間 | 2026-11-13 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 200 分。**実績は約 90 分（概算。時刻を厳密には記録していない）。差は約 −110 分**。品質ゲートは 1 回目で通った（`check documentationTest` と Sonar） |
| GitHub | 変えていない |
| 計画 | [Bolt 40-7 計画](bolt_40-7_plan.md) |

## 成果

`ServiceCaseCommandService`（`open`・`receive`）、`OpenServiceCaseCommand`・`ReceiveServiceCaseCommand`、結果の型 `OpenServiceCaseOutcome`（`Opened`・`Incomplete`・`Duplicate`・`NotPermitted`・`CalendarNotRegistered`）・`ReceiveServiceCaseOutcome`（`Received`・`NotPermitted`・`NotFound`・`NotReceivable`・`Conflict`）、`ServiceCaseAuthorization`、`ServiceCase.missingItems`、`ServiceCaseRepository.findUnclosedDuplicate`（MyBatis 実装）、`TrackingConfiguration` の Bean。`ServiceCaseCommandServiceTest` が 14 行（認可、必須項目の不足と番号の未使用、重複と他社・終結済み、期限と営業時間外の優先度高、登録漏れ、受領の認可・見つからない・状態・競合）、`MyBatisServiceCaseRepositoryIntegrationTest` が重複の検索 3 件を確かめる。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest`（`uiTest` は流さない） | BUILD SUCCESSFUL（7 分 26 秒） |
| SonarQube | **PASS**（新しいコードのカバレッジ 96.2%、重複 0.016%、違反 0） |

## 完了条件

- [x] 行 1〜14 と重複の検索が緑
- [x] `check documentationTest` と SonarQube が緑
- [x] 見積り／実績／差を書いた

## 申し送り

- Bolt 40-8: 進捗・回答・終結・再開のコマンド（US-20 AC4〜AC7）。認可を計画の段階で示して承認を取る。`ServiceCaseAuthorization` を使い回す。
- 荷主担当者の自社案件の照会・再開（SC-INV-05。US-23）は 40-10・40-15。
- 起票・受領の監査記録は足していない（要るかを諮る）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成（承認待ち） | anthropic/claude-sonnet-5-5 |
| 2026-11-13 | 議題をすべて推奨のとおり承認として記録した（human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
