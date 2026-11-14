---
type: Plan
title: "Bolt 41 終了報告 - 有人案件の操作の拒否をドメインの専用の例外で区別する"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T04:30:00Z }
---

# Bolt 41 終了報告 - 有人案件の操作の拒否をドメインの専用の例外で区別する

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | 専用の例外 2 つ（`ServiceCaseStateException`・`ServiceCaseInputException`）を `valueobjects` に置いた。`run` はこの 2 つだけを業務上の拒否にする。バグの例外は隠さず失敗させる。利用者に見える振る舞いは変わらない | 承認 | [計画](bolt_41_plan.md) |
| 2 | **手順の逸脱（私の側）**: テストを実装の後に足した。先に失敗を見ていない（TDD の三原則から外れた）。以後は先に失敗を見る | 承認 | 計画 (2) |
| 3 | 決定は人の指示「以降 Recommended で自律実行して」による推奨のとおりの記録で、計画の承認を別に待たずに実装した | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 41 回（W9。技術。W9 計画の最初の Bolt） |
| 期間 | 2026-11-14 |
| SP | 0 |
| 見積り／実績 | 見積り 120 分。実績は概算 40 分 |
| 計画 | [Bolt 41 計画](bolt_41_plan.md) |

## 成果

`ServiceCase`・`CaseAnswer`・`CaseProgress` の状態違反と入力の誤りを専用の例外にし、`ServiceCaseActionService.run` が専用の例外だけを捕まえる。テスト: `ServiceCaseActionServiceTest` に 2（行 15・16）。`tracking` のテストは緑。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | BUILD SUCCESSFUL（30 分 31 秒） |
| SonarQube | **PASS**（新しい違反 0。1 回目は新しいテストの 1 件を指摘され、直して流し直した。カバレッジ 96.1%） |

## 申し送り

W9 の次は Bolt 42（US-23）。範囲は、足りない分（他社の閲覧の試行の記録、URL の規則との一致）に縮む見込み。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
