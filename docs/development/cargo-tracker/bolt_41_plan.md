---
type: Plan
title: "Bolt 41 計画 - 有人案件の操作の拒否をドメインの専用の例外で区別する（W8 の持ち越しの返済）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T04:00:00Z }
---

# Bolt 41 計画 - 有人案件の操作の拒否をドメインの専用の例外で区別する

[W9 計画](iteration_plan-W9.md)の技術 Bolt（種別: 技術、SP 0、見積り 120 分）。

## 課題

`ServiceCaseActionService.run` は、ドメインの `IllegalStateException` と `IllegalArgumentException` をそのまま捕まえて「状態が違う」「入力が違う」と返す。ほかの原因（バグ）の同じ型の例外まで、業務上の拒否に見せかけて隠す。

## 決定（推奨のとおり。人の指示「以降 Recommended で自律実行して」による）

| # | 決定 | 理由 |
| :--- | :--- | :--- |
| 1 | 専用の例外 `ServiceCaseStateException`（`IllegalStateException` を継承）と `ServiceCaseInputException`（`IllegalArgumentException` を継承）を `domain.model.valueobjects` に置く（`BusinessCalendarNotRegisteredException` と同じ置き場所） | 既存の呼び手が親の型で捕まえても壊れない。エンティティ（`CaseAnswer`・`CaseProgress`）からも参照できる |
| 2 | `ServiceCase` の状態違反と、回答・進捗の内容の誤り、回答期限の変更の誤りを専用の例外にする。番号が 1 未満などの「呼び出しの誤り」は `IllegalArgumentException` のまま | 業務上の拒否と、プログラムの誤りを分ける |
| 3 | `run` は専用の例外だけを捕まえる | バグを隠さない |

スキーマ・認証・外部連携の変更はない。画面の振る舞いも変わらない（マニュアルの更新は不要）。

## 計画

- [x] (1) 例外 2 つの追加と置き換え、`run` の捕まえ方を狭める
- [x] (2) テスト: 行 15（拒否ではない `IllegalStateException` は失敗させる）、行 16（空の回答は入力の誤り）。**実装の後に足した。先に失敗を見ていないので TDD の手順から外れた**（行 15 は元の実装なら失敗することを、読んで確かめただけ）
- [x] (4) 品質ゲート・Sonar・文書

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
