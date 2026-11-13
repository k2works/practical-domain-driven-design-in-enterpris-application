---
type: Plan
title: "Bolt 40-11 終了報告 - 有人案件の一覧（S-14）と詳細・受領（S-15）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T03:30:00Z }
---

# Bolt 40-11 終了報告 - 有人案件の一覧（S-14）と詳細・受領（S-15）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **認可の追加（`SecurityConfiguration`）**: `/staff/service-cases/**`（一覧・詳細・受領）を社内の運用役割 5 つ（カスタマーサポート・営業担当者・経路設計者・追跡管理者・データ責任者。台帳 D-159 の集合と同じ）に開いた。システム管理者・監査担当者・荷主担当者は 403。担当企業の範囲は掛けない（D-159）。統合テスト（本物のフィルター）で確かめた | 承認 | 計画 決定 (2) |
| 2 | **スキーマの変更なし**。リポジトリに `findByStatuses`（状態の集合で引く）を足しただけ | 承認 | 計画 決定 (3) |
| 3 | **担当者の表示**: 利用者の表示名を引く公開 API がまだないので、自分は「あなた」、ほかは「利用者 ID: …」。表示名の解決は後の Bolt の課題 | 承認 | ui_design S-15 |
| 4 | **マニュアルに 13 章「有人案件」を新規に追加**（同じ Bolt で更新。キャプチャは操作が出そろう 40-12 で撮る。文章と表のみ） | 承認 | docs/manual/13 |
| 5 | **ナビゲーション**: 運用役割に「有人案件」を出した。**カスタマーサポートには「追跡」のナビがまだない**（40-10 の URL だけで開ける状態）。出すかは次の Bolt で諮る | 確認 | 申し送り |
| 6 | **作業の流儀（私の側）**: 標準入力につながる形（`python3 - <<EOF`）をこの Bolt でも 1 回誤って実行した（何も変えていない。止めた）。`git commit -a` で無関係な `.claude/agent-memory` の変更を巻き込んだが、未 push のうちに切り直した | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-11 回（W8 の 12 本目） |
| 期間 | 2026-11-13〜11-14 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 200 分。**実績は約 230 分（概算）、差は約 +30 分**（UI シナリオのロケーターと名前フィルターの調整、Sonar の指摘 4 件）。`check documentationTest uiTest` は 27 分 58 秒 |
| 計画 | [Bolt 40-11 計画](bolt_40-11_plan.md) |

## 成果

`ServiceCaseQueryService`（一覧・詳細）、`ServiceCaseListFilter`、`ServiceCaseController`・`ServiceCaseViews`、`list.html`・`show.html`、`findByStatuses`（リポジトリ・MyBatis）、認可とナビ、マニュアル 13 章。テスト: `ServiceCaseQueryServiceTest` 9、`ServiceCaseControllerTest` 13、セキュリティ統合 3、リポジトリ統合 1、画面のシナリオ 2（`service_case_receive_ui.feature`）。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest uiTest` | BUILD SUCCESSFUL（27 分 58 秒） |
| SonarQube | 初回は新しい違反 4 件（record パターン 2・Optional・未使用定数）で FAILED → 直して再実行し **PASS**（新しい違反 0） |

## 完了条件

- [x] 上のテストが緑
- [x] `check documentationTest uiTest` が緑
- [x] マニュアルと設計文書を同じ Bolt で更新した
- [x] 見積り／実績／差を書いた

## 申し送り

- Bolt 40-12: S-15 の進捗・回答・終結・再開の操作と escalation の表示。マニュアル 13 章のキャプチャを撮る。
- カスタマーサポートの「追跡」ナビ、担当者の表示名の解決。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri。「以降 Recommended で自律実行して」の指示） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
