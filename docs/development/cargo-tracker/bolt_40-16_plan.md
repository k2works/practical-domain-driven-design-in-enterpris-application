---
type: Plan
title: "Bolt 40-16 計画 - W8 の受入条件の照合とマニュアル・動画の総点検（US-11・US-20 を締める）"
description: "W8 の 17 本目（機能、SP 5（US-20）、120 分）。Release 1.0 の受入条件の照合の表に US-11 AC1〜AC3・US-20 AC1〜AC8 を足し、各条件にシナリオを付け、マニュアルと動画を点検する。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T18:00:00Z }
---

# Bolt 40-16 計画 - W8 の受入条件の照合とマニュアル・動画の総点検（US-11・US-20 を締める）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-16 回（W8 の 17 本目。[W8 計画](iteration_plan-W8.md)） |
| 状態 | 計画（**承認済み**。「以降 Recommended で自律実行して」（human:kakimomokuri）により、推奨のとおり承認として記録） |
| 種別・SP | 機能・**SP 5（US-20 を閉じる）** |
| 見積り | **120 分**（作業のみ）。類推元は Bolt 39h4（計画 225 分、実績 72 分）の小さい側 |
| トレース | US-11 AC1〜AC3、US-20 AC1〜AC8、W8 計画の「受入条件との対応」 |
| 承認ゲート | なし（認可・スキーマ・外部連携の変更はない） |

## 決定

(1) **照合の表に足す**（test_strategy「Release 1.0 の受入条件の照合の表」）: US-11 AC1〜AC3、US-20 AC1〜AC8 の 11 行（タグ `@US-11-ACn`・`@US-20-ACn`）。詳細の対応表（「Release 1.0 の照合の表の前の対応」）に、Bolt とテストの名前を足す。`AcceptanceCriteriaCoverageTest` が、各タグに `@wip` でないシナリオがあることを照合する。

(2) **シナリオを付ける**: 画面の層の既存のシナリオにタグを付ける（受領 → AC2、期限超過 → AC3、進捗・回答・終結・再開 → AC4・AC5・AC6、引き継ぎ → US-11 AC2）。足りない分は新しく書く: 業務ルール層の `service_case.feature`（AC1 一意な受付番号、AC7 復旧照合が未完了の終結の拒否、AC8 営業時間外の優先度高の即時の受付番号と次回更新日時）、画面の層の `customer_support_tracking_ui.feature`（US-11 AC1 顧客への表示の区別、AC3 登録・採用の操作がなく登録の画面は 403）。

(3) **マニュアルの総点検**: 13 章に画面のキャプチャ 5 枚を足す（`@manual` のシナリオ。`manualScreenshots`。ほかの章の画像は撮り直さない）。マニュアルの索引・2 章・8 章の整合を確かめる。

(4) **動画**: 受領（40-11）と引き継ぎ（40-15）の 2 本を録画する（`@demo-bolt-40-11/receive-service-case`、`@demo-bolt-40-15/inquiry-handoff`）。`demoVideo` は既存の動画を先に消すので、録画の後に既存の動画を復元し、新しい 2 本だけを足す。

(5) **GitHub の Issue（#）のクローズはしない**（外部の操作。W8 の締めで確認する。前の Bolt と同じく GitHub は変えない）。

## 状態の組み合わせ

`AcceptanceCriteriaCoverageTest`（11 行の照合）、業務ルール層のシナリオ 3、画面の層のシナリオ 2 + タグ付け 4、撮影 5 枚。

## ステップ

| ステップ | 内容 | 分 |
| :--- | :--- | ---: |
| 1 | シナリオの追加とタグ付け（業務ルール層・画面の層） | 50 |
| 2 | 照合の表・詳細の対応表、マニュアルのキャプチャ、動画 | 40 |
| 3 | 品質ゲート（`uiTest` を含む）、終了報告 | 30 |

## 完了条件

- [ ] 上のシナリオとテストが緑
- [ ] `check documentationTest uiTest` と SonarQube が緑
- [ ] マニュアルと設計文書を同じ Bolt で更新した
- [ ] 終了報告に見積り／実績／差を書いた

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
