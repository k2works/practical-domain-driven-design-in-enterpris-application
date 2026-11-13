---
type: Plan
title: "Bolt 40-16 終了報告 - W8 の受入条件の照合とマニュアル・動画の総点検（US-11・US-20 を締める）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T19:00:00Z }
---

# Bolt 40-16 終了報告 - W8 の受入条件の照合とマニュアル・動画の総点検（US-11・US-20 を締める）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **US-11・US-20 の受入条件の照合を足した**: 照合の表に 11 行（US-11 AC1〜AC3、US-20 AC1〜AC8）。各タグに `@wip` でないシナリオがあることを `AcceptanceCriteriaCoverageTest` が照合する（緑）。詳細の対応表に Bolt とテストの名前を足した | 承認 | test_strategy |
| 2 | **部分的な受入条件の読み方**: US-20 AC3（escalation）は、読むときの判定（期限超過の印・絞り込み）と上位対応中の表示までを確かめる。**状態を上位対応中にする自動の書き込みと上位の運用責任者への通知は W10**（40-9 の決定。照合の表のタグは「画面の振る舞い」を確かめる） | 承認 | 詳細の対応表 |
| 3 | **新しいシナリオ**: 業務ルール層 `service_case.feature` 3（AC1・AC7・AC8）、画面の層 `customer_support_tracking_ui.feature` 2（US-11 AC1・AC3）。既存の画面の層のシナリオにタグを付けた | 承認 | features |
| 4 | **マニュアルのキャプチャ 5 枚を撮った**（13 章。`manualScreenshots` を名前で絞って流し、ほかの章の画像は変えていない）。撮ったキャプチャで「進捗」の見出しが直前のボタンに詰まっていたので、余白を足した（画面の小さな直し） | 承認 | docs/manual/13 |
| 5 | **動画 2 本を足した**（受領 `bolt-40-11/receive-service-case.webm`、引き継ぎ `bolt-40-15/inquiry-handoff.webm`）。**`demoVideo` は既存の動画を先に消す**ため、録画の後に既存の動画を復元した（git の差分は新しい 2 本だけ）。動画の再録画の手順の注意として記録する | 承認 | index.md |
| 6 | **GitHub の Issue のクローズはしていない**（外部の操作。W8 の締めで確認する）。**SP の数え直しは W8 の締め**: US-11 は 3 SP（40-15）、US-20 は 5 SP（本 Bolt）で、両ストーリーの受入条件はすべて満たした | 承認 | 申し送り |
| 7 | **作業の流儀（私の側）**: 標準入力につながる形は今回は使っていない | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-16 回（W8 の 17 本目） |
| 期間 | 2026-11-14 |
| 種別・SP | 機能・SP 5（US-20） |
| 見積り／実績 | 見積り 120 分。**実績は約 140 分（概算）、差は約 +20 分**（画面の層の追跡記録の準備（版の扱い）、動画の削除の復元） |
| 計画 | [Bolt 40-16 計画](bolt_40-16_plan.md) |

## 成果

照合の表 11 行と詳細の対応表（test_strategy）、`service_case.feature`・`ServiceCaseSteps`、`customer_support_tracking_ui.feature`・`CustomerSupportTrackingUiSteps`、既存の画面の層のシナリオへのタグ付け、マニュアル 13 章のキャプチャ 5 枚、動画 2 本、S-15 の見出しの余白。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest uiTest` | （結果は更新履歴） |
| SonarQube | （結果は更新履歴） |

## 完了条件

- [x] 上のシナリオとテストが緑
- [ ] `check documentationTest uiTest` と SonarQube が緑
- [x] マニュアルと設計文書を同じ Bolt で更新した
- [x] 見積り／実績／差を書いた

## 申し送り

- **W8 の締め**: 完了報告書（iteration_report-W8）、週全体のレビュー、品質ゲートの流し直し、CI の性能ジョブの再実行（PERF-06 の行 21）、GitHub の Issue のクローズと SP の数え直し。
- **Bolt 40-3**: 確認の場の回答待ちでスキップしたまま（W8 計画の確認の場 ×3 の結果で決める）。
- 業務責任者に確かめる仮の決定: D-156〜D-162。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri。「以降 Recommended で自律実行して」の指示） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
