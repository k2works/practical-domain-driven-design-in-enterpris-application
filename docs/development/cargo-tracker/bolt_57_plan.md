---
type: Plan
title: "Bolt 57・57-1 計画と報告 - 運用の警告の宛先とログの印"
description: "W10-1 計画の Bolt 57・57-1（技術、SP 0）。"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T10:24:58Z }
---

# Bolt 57・57-1 計画と報告 - 運用の警告の宛先とログの印

[W10-1 計画](iteration_plan-W10-1.md)の Bolt 57・57-1（技術、SP 0）。

**状態**: 範囲と方式は W10-1 計画の CP-1・CP-2 で承認済み（2026-11-27）。この報告は 56-1 でまとめて承認を受ける（CP-7）。検証（`validating-design`）: A 終盤の技術 Bolt。B 閾値は `non_functional.md` の OBS-01・02・10、通知の経路は ADR-023 と一致。C Bolt 51 の Terraform のモジュールの形（`stack` が各モジュールを呼び、未決の値は既定値のない変数）にそろえた。

## 計画と結果

- [x] (1) 57: `ops/terraform/modules/alerts`（SNS 3 トピックとメールの購読、ログのメトリクスフィルターと警報 5 種、OBS-01・02・10 の警報）
- [x] (2) 57-1: `OperationsAlertTest`（印の形、Terraform のキーとの一致）を先に書き、コンパイルの失敗を確かめてから `OperationsAlert` を作った
- [x] (3) 57-1: 受け手のテストに印の検証を足し、失敗を確かめてから、毒入りのイベントを捨てる ERROR（5 か所）と主要実績の拒否の WARN に印を入れた

## 決めたこと

| 項目 | 内容 |
| :--- | :--- |
| 印の定数の置き場所 | `shared.domain` の enum。共有カーネルで制限されるのは振る舞いを持つインターフェースだけで、新しい依存と規則の例外はない |
| OBS-02 の数え方 | アプリの 5xx と ALB 自身の 5xx を足して要求の数で割る |
| OBS-10 | 失敗するタスクが 1 つ以上で警告、通るタスクがないと重大（データがないときも重大） |
| 主要実績の拒否の宛先 | 追跡のトピック。ほかの印は運用、取込なし（Bolt 58）はデータと追跡 |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `terraform fmt`・`validate`（staging・production） | 成功。`plan`・`apply` は未実施 |
| `OperationsAlertTest`、`*EventHandlerTest`、アーキテクチャのテスト、`ModularityTest`、`checkstyleMain`・`checkstyleTest`・`spotbugsMain`・`spotlessCheck` | 成功。全件のゲートは 56-1 |

## 未確認

- **警告のメールが届くこと。** 実環境がないので確かめていない。メトリクスフィルターの文字列が CloudWatch で印に一致することも未確認（テストが確かめるのは Terraform のファイルと enum の一致まで）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-27 | 初版 | anthropic/claude-sonnet-5-5 |
