---
type: Plan
title: "Bolt 44-2 終了報告 - 基準値の登録と比較（US-21 AC5。US-21 を締める）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-15T09:00:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T10:08:31Z }
---

# Bolt 44-2 終了報告 - 基準値の登録と比較（US-21 AC5。US-21 を締める）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | 基準値は **28 日・KPI-01 は分・KPI-02 はパーセント・追記専用**。パイロット中の値は直近 12 週をまとめた値で、差は判断の材料（合否は示さない）（台帳 D-172。仮） | 承認 | [計画](bolt_44-2_plan.md) |
| 2 | スキーマ `identity.kpi_baseline` を新設した（登録者の外部キーなし。追記専用の印）。選択肢の説明のとおり | 承認 | 計画の範囲 1 |
| 3 | **Bolt 44・44-1・44-2 のゲートをまとめた**。US-21 の 3 SP をここで数える。GitHub の Issue #24 のクローズは人の許可が要るので実行していない | 承認 | [Bolt 44](bolt_44_report.md)・[44-1](bolt_44-1_report.md) |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 44-2 回（W9。機能。US-21 AC5。US-21 を締める） |
| 期間 | 2026-11-15 |
| SP | **3**（US-21） |
| 計画 | [Bolt 44-2 計画](bolt_44-2_plan.md) |

## 成果

`KpiBaseline`・`KpiKind`・リポジトリ・移行、`KpiBaselineCommandService`、`KpiComparisonQueryService`・`KpiPilotSummary`、未完了の発行の件数（`IncompleteEventPublicationCounter`）、S-22 の比較・登録・件数、マニュアル 10 章、画面のシナリオ（`@US-21-AC5`）。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | 1 回目は用語集（`KpiBaseline`・`InquiryCountSummary`）と Checkstyle（コントローラーの複雑度 12）で失敗。2 回目はセキュリティの統合テスト（監査担当者がホームを持ったので「ホームのない役割」の例をカスタマーサポートに替えた）で失敗。3 回目は SpotBugs（`KpiComparisonView` のリストの防御的コピー）で失敗。直して `--continue` を付けて流し直し、BUILD SUCCESSFUL（32 分 15 秒。Bolt 44・44-1 の分もここでまとめて流した） |
| SonarQube | 1 回目は新しい違反 20 件（引数の数・リテラルの重複・ラムダ・予約語に近い識別子・Optional の比べ方・`@Autowired`・Bug 1）で FAILED。直して流し直し、2 回目も 3 件（Bug 1・ラムダ 2）で FAILED。直して流し直し、**PASS**（新しい違反 0） |

## 申し送り

次は Bolt 48（W9 の締め）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-15 | 初版 | anthropic/claude-sonnet-5-5 |
