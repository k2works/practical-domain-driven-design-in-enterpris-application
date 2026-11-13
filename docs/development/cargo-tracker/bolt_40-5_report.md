---
type: Plan
title: "Bolt 40-5 終了報告 - 有人案件の集約（ドメインだけ）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T07:00:00Z }
---

# Bolt 40-5 終了報告 - 有人案件の集約（ドメインだけ）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **種類ごとの必須項目は私が置いた仮定（台帳 D-158）**: US-20 AC1 の「原因、担当、期限、旧版・新版、手動入力出典、顧客連絡、復旧照合」を、種類ごとに具体化する文書がなかった。計画の決定 (3) の表のとおりに実装した。**顧客連絡は表に列がないので集約に持たず、40-7 の入力で扱う**。業務責任者の確認が要る | 承認（確認は業務責任者） | 計画 決定 (3) |
| 2 | **集約の流儀**: 不変（メソッドは新しい `ServiceCase` を返す）。状態に合わない操作は `IllegalStateException`、業務上の拒否（必須項目の不足、復旧照合が未完了の終結）は結果の型（`ServiceCaseOpening`・`ServiceCaseClosing`）で返す。40-7・40-8 で入力を保持して不足を示すため | 承認 | 計画 決定 (2) |
| 3 | **escalation の判定**: `escalate(now)` は受領期限を厳密に過ぎた受付済みだけを上位対応中にする（期限ちょうどは変えない）。読むときの判定か定期処理かは 40-9 で決める | 承認 | 計画 決定 (5) |
| 4 | **再開**: 顧客の回答（未解決）は最後の回答に足す（`CaseAnswer.customerResponse`）。再開の後の回答は回答番号が続く。進捗・過去の回答は保持（SC-INV-03） | 承認 | 行 11 |
| 5 | **品質ゲートで 3 回直した**: (a) 用語集（`進捗`・`回答`・`起票の入力` を足し Javadoc の書き出しを合わせた）、(b) checkstyle の循環的複雑度（必須項目の確かめを種類ごとのメソッドに分けた）、(c) SpotBugs の `EI_EXPOSE_REP`（`sources()` を防御的コピーで返す）。Sonar も 2 回目の指摘（メソッド参照）を直した。計画の段階で複雑度と用語集の規則を見ていなかった | 承認 | — |
| 6 | **流していないもの**: `uiTest`（画面は触れない）、CI・デモ環境の配備（push していない）。永続化・コマンド・画面はこの Bolt の範囲外 | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-5 回（W8 の 6 本目） |
| 期間 | 2026-11-13 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 210 分。**実績は約 130 分（概算。時刻を厳密には記録していない）。差は約 −80 分**。壁時計は `check documentationTest` 3 回・Sonar 3 回を含む |
| GitHub | 変えていない |
| 計画 | [Bolt 40-5 計画](bolt_40-5_plan.md) |

## 成果

`ServiceCase`（集約）、`CaseProgress`・`CaseAnswer`（エンティティ）、`CaseNumber`・`ServiceCaseKind`・`ServiceCaseStatus`・`MissingItem`・`ServiceCaseDraft`（値）、`ServiceCaseOpening`・`ServiceCaseClosing`（結果）を作った。`ServiceCaseTest` が計画の行 1〜12 を確かめる（必須項目の不足 9 組、7 種類の起票、営業時間外の受付の時刻、受領・escalation・進捗・回答・終結・復旧照合・再開・重複終了）。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest`（`uiTest` は流さない） | 1 回目は用語集と checkstyle、2 回目は SpotBugs で落ち、直した。**3 回目は BUILD SUCCESSFUL（8 分 7 秒）** |
| SonarQube | 1 回目はメソッド参照の指摘 2 件で FAIL。直して **2 回目は PASS**（新しいコードのカバレッジ 96.1%、重複 0.016%、違反 0） |

## 完了条件

- [x] 行 1〜12 が緑
- [x] `check`（`documentationTest` を含む）と SonarQube が緑
- [x] 見積り／実績／差を書いた

## 申し送り

- 業務責任者に確かめる: D-158（種類ごとの必須項目）。顧客連絡の持ち方。
- Bolt 40-6: 4 表（`service_case`・`service_case_source`・`service_case_progress`・`service_case_answer`）、受付番号の採番、リポジトリ。`ServiceCase` には永続化用の再構成の入口と楽観ロックの版がまだない（40-6 で足す）。
- 規律: この Bolt では標準入力につながる形を使っていない。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成（承認待ち） | anthropic/claude-sonnet-5-5 |
