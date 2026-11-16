---
type: Plan
title: "Bolt 49-1 計画 - W9 の持ち越しの返済 2（見積りの回答の画面の重複）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T02:28:46Z }
---

# Bolt 49-1 計画 - W9 の持ち越しの返済 2

[W10 計画](iteration_plan-W10.md)の技術 Bolt（SP 0、見積り 180 分）。依存は Bolt 49。W9 レビュー M-9（C-6）の重複のうち、**振る舞いを変えずに今直せるものに絞る**。

**状態**: `/goal W10` のもとで自律的に進めた（人の承認は 2026-11-16 の「その方針で進める」による）。検証済み（`validating-design` 3 軸: A 終盤の技術の返済で TDD は既存のテストが守る、B 設計図・画面・スキーマは変えないので対象外、C Bolt 41 計画（技術の返済）と同じ形式。`validating-iteration-plan` は事実の確認で代えた）。

## 範囲

| 対象（M-9 の項目） | 扱い |
| :--- | :--- |
| `consultRejection` と `declineRejection` の文言の重複（`QuotationResponseController`） | **直す**: 共通の `rejectionAfterRouting` にする |
| `findVisible…orElseThrow` の重複（同じコントローラーの 2 か所） | **直す**: `findVisibleOptional` に集める。ほかのコントローラー（`QuotationApprovalController`、`TransportRequestController`）の同種の検索は、返す型と例外が違うので集めない |
| `notifying()` と引数渡しの 2 通り、`reconstitute` の 16・17 引数、`KpiWeeklyQueryService` の全件の読み、理由・内容の検証の重複、`ServiceCaseIntake.open` の責務 | **直さない（W10 の Bolt 49-1 の範囲に入れない）**。どれも集約・リポジトリ・クエリの形を変えるので、1 つずつ別の Bolt で、スキーマ・型の変更の承認を得て行う。Release 1.1 の頭の返済の枠に送る（申し送り） |

## 計画（TDD の三原則は変えない）

- [ ] (1) 守る既存のテスト（`com.example.cargotracker.quotation.*`）を変更の前に流して緑を確かめる。**流さなかった**（変更の後にだけ流した。直前の Bolt 49 の `check` が緑で、同じ層を触っていないことを根拠にしたが、手順からは外れた）
- [x] (2) 重複の 2 つを直す（振る舞いは変えない。リファクタリング）
- [x] (3) 同じテストと `checkstyleMain` を流し直す（緑）
- [ ] (4) 全体のゲート（Bolt 49 の締めのとき W10 の締めで流す）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版 | anthropic/claude-sonnet-5-5 |
