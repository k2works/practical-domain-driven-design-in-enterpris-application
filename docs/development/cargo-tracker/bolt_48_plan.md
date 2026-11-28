---
type: Plan
title: "Bolt 48 計画 - W9 の締め（照合・マニュアル・動画・週全体レビュー）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-15T04:13:23Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T10:08:31Z }
---

# Bolt 48 計画 - W9 の締め

[W9 計画](iteration_plan-W9.md)の技術 Bolt（SP 0。見積り 150 分）。スキーマ・認証・外部連携の変更はない。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | 受入条件の照合の表（Release 1.0）に W9 のストーリーがそろっていることを確かめる（`AcceptanceCriteriaCoverageTest`）。各 Bolt で足したので、ここでは抜けを見る |
| 2 | 週全体の開発レビュー（5 視点）と、高の指摘への対応（辞退の確認、二重送信の文言、マニュアル・用語集・データモデルの食い違い） |
| 3 | マニュアルの総点検（4・5・6・7・10・14・15 章、用語集、役割の表）と、画面のキャプチャの再生成（`manualScreenshots`）、W9 のデモ動画 10 本（`demoVideo`。既存の動画は残す） |
| 4 | 品質ゲートの流し直し（`--continue spotlessCheck check documentationTest uiTest`）、SonarQube、CI の確認 |
| 5 | 完了報告書（KPT を含む）、計画と進捗の実績の反映、GitHub の確認、索引・mkdocs・log |

## 決定（推奨のとおり）

- 業務の判断が要る指摘（辞退後の輸送要求の行き先ほか）は直さず、W10 の持ち越しに記録して人に諮る。
- GitHub の Issue（#22〜#27）のクローズは人の許可が要るので実行しない。

## 計画

- [x] (1) 照合の表の確認
- [x] (2) 週全体レビューと高の対応
- [x] (3) マニュアル・キャプチャ・動画
- [x] (4) 品質ゲート・Sonar・CI
- [x] (5) 完了報告書・同期

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-15 | 初版 | anthropic/claude-sonnet-5-5 |
