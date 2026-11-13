---
type: Plan
title: "Bolt 40-1 計画 - W7 週全体レビューの返済（M1・M3・M6・M7）"
description: "W8 の 2 本目（技術、SP 0、210 分）。TrackingRecord の不変条件の補強、DE-12・DE-13 の入力検証を application.internal へ、CHECK の負例の表、失敗時の巻き戻しの確認。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T02:40:00Z }
---

# Bolt 40-1 計画 - W7 週全体レビューの返済（M1・M3・M6・M7）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-1 回（W8 の 2 本目。[W8 計画](iteration_plan-W8.md)） |
| 状態 | 計画（**承認済み**。2026-11-13 に「Recommend 承認で進めて」（human:kakimomokuri）により、推奨のとおり承認として記録。CP-9 だけは人が「同時に直す」と回答） |
| 種別・SP | 技術・SP 0 |
| 見積り | **210 分**（作業のみ。M1 40、M3 60 + ExternalMilestone の分、M6 45、M7 40、準備 25）。類推元は Bolt 38h（217 分） |
| トレース | [W7 完了報告書](iteration_report-W7.md) の週全体レビュー M1・M3・M6・M7 |
| 承認ゲート | 計画と終了報告で止める。スキーマ・認可・外部連携は変えない |

## Bolt ゴール

(M1) `TrackingRecord` が `originalEta` と `schedule.finalArrival()` の一致、根拠の実績番号の存在を確かめる。(M3) DE-12・DE-13 の欠けの検査と変換が `application.internal` にあり、値オブジェクトは自分の不変条件だけを持つ。(M6) `ck_exclusion_reason_threshold` の負例が区分 × 列の表で固定される。(M7) 移し替えの失敗時の巻き戻しが PostgreSQL のカタログで確かめられる。

## 確認ポイント

| # | 確認ポイント | 決定 |
| :--- | :--- | :--- |
| CP-9 | M3 で `ExternalMilestone` も同時に直すか | **同時に直す**（人の回答。推奨は「直さない」だった。見積りは 210 分から延びる見込みを許し、超えたら M6・M7 を W10 へ送る） |
| CP-1 | 振る舞いは変えない（リファクタリングと補強のテスト）。製品コードの新しい拒否は M1 だけ | 推奨のとおり |
| CP-2 | 品質ゲートは `check` + Sonar。`uiTest` は画面に触れないので流さない | 推奨のとおり |
| CP-3 | 超えそうなときの打ち切り: ステップごとの累計が 210 分を超えたら M6・M7 を W10 へ送る | 推奨のとおり |

## ステップ計画

| ステップ | 内容 | 分 |
| :--- | :--- | ---: |
| 1 | 準備: 対象の既存テストを流して緑を記録 | 25 |
| 2 | M1: Red（`originalEta` 不一致・根拠の実績番号なしを拒むテスト）→ コンストラクターに検査を足して Green | 40 |
| 3 | M3: `VoyageArrival`・`VoyagePortCall`・`ExternalMilestone` の欠けの検査と変換を `application.internal` へ。既存テストで守り、値オブジェクトは `requireNonNull` 等の不変条件だけ | 80 |
| 4 | M6: CHECK の負例を `@MethodSource` の表（区分 × 持ってはいけない列、区分 × 必須列の NULL）にする | 45 |
| 5 | M7: PostgreSQL の `information_schema.columns` と `flyway_schema_history` の確認、H2 の負例 | 40 |
| 6 | 品質ゲート（`check` と SonarQube） | — |
| 7 | 終了報告、索引・mkdocs・log の同期 | — |

## 完了条件

- [ ] M1 の拒否が単体で緑、既存の組み立て経路が緑
- [ ] M3 の移し替え後も DE-12・DE-13 の既存テストが緑（振る舞いは同じ）
- [ ] M6 の表と M7 の確認が緑
- [ ] `check` と SonarQube が緑
- [ ] 終了報告に見積り／実績／差を書いた

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri。CP-9 は人の回答） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
