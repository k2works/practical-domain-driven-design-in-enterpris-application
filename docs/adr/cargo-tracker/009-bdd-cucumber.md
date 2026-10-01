---
type: ADR
title: "ADR-009: BDD を採用し、受入条件を Cucumber で実行可能な仕様にする"
description: "開発戦略として BDD を採用し、ユーザーストーリーの受入条件を日本語 Gherkin のシナリオにして Cucumber-JVM で自動実行し、生きたドキュメントとして扱う決定。"
tags: [adr, tech-stack, bdd]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-01T07:24:52Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-01T07:29:05Z }
---

# ADR-009: BDD を採用し、受入条件を Cucumber で実行可能な仕様にする

ユーザーストーリーの受入条件を、日本語 Gherkin のシナリオとして Cucumber-JVM で自動実行する。

日付: 2026-10-01

## ステータス

承認済み（BDD の採用は human:kakimomokuri が 2026-10-01 に決定。本 ADR は採用の方式を記録する）

## コンテキスト

- 開発戦略として BDD を採用することが決まった。
- [ユーザーストーリー](../../requirements/cargo-tracker/user_story.md) の受入条件は Given / When / Then で書かれており、AI-DLC では受入条件を人と AI の契約として扱う（CLAUDE.md）。
- 受入条件には、日時の境界（BR-10、BR-11）、開示範囲（BR-07）、冪等性（BR-12）など、具体例で示すと誤解が減る業務ルールが多い。
- [BDD 導入ガイド](../../reference/BDD導入ガイド.md) は、スリーアミーゴの会話、日本語 Gherkin、Cucumber によるシナリオとコードの整合性照合、生きたドキュメントとしての公開を示している。
- ADR-006 で Java 25・Spring Boot 4.1（JUnit 6、Spring Framework 7）を選んだ。Cucumber-JVM 8.0 系は JUnit 6 と Spring Framework 7 に対応している（2026-10-01 に Maven Central で確認）。

## 決定

**BDD を開発戦略とし、受入条件を Cucumber で実行可能な仕様にする。**

| 項目 | 採用 |
| :--- | :--- |
| ツール | Cucumber-JVM 8.0.x（cucumber-java、cucumber-spring、cucumber-junit-platform-engine）。版は cucumber-bom で揃える |
| 言語 | 日本語 Gherkin（`# language: ja`） |
| 置き場所 | `src/test/resources/features/<context>/`。コンテキストごとに分ける |
| トレース | シナリオにストーリー ID と業務ルール ID のタグを付ける（例: `@US-04 @BR-10`） |
| ステップ定義 | 業務ルールのシナリオは application 層の入力ポートを直接呼ぶ。画面を通す必要があるシナリオだけ Playwright で実ブラウザを操作する |
| 開発の流れ | シナリオ（受入テスト）を先に書いて失敗させ、内側を TDD で実装してシナリオを通す |
| 生きたドキュメント | 実行結果を CI の成果物として保存し、受入条件の達成状況を示す |

タグの規約、シナリオの粒度、画面を通すシナリオの選び方はテスト戦略で決める。

### 代替案

| 案 | 却下理由 |
| :--- | :--- |
| 受入条件を JUnit のテストとして直接書く | 業務担当者が読めず、受入条件の文書とテストが二重管理になり、乖離を検出できない |
| すべてのシナリオを画面経由（Playwright）で実行する | 実行が遅く壊れやすい。業務ルールの確認に画面は要らない |
| 英語の Gherkin | 業務を日本語で語るチームであり、BDD の価値である会話から離れる（BDD 導入ガイド） |

## 影響

### ポジティブ

- 受入条件が実行可能になり、ストーリーの完了を自動で判定できる。
- タグでストーリー・業務ルールとテストを双方向にたどれる。
- 実行結果が生きたドキュメントになり、受入条件の文書との乖離を検出できる。

### ネガティブ

- シナリオとステップ定義の保守が増える。ステップ定義の重複や、実装の詳細を書いたシナリオが増えると負担になる。
- Spring Boot の BOM が Cucumber を管理しないため、版を別に管理する必要がある。

## コンプライアンス

- CI で Cucumber のシナリオを実行し、失敗したら配備しない。
- 各ストーリーの受入条件に、対応するタグ付きのシナリオがあることを確認する。

## 備考

- 著者: anthropic/claude-opus-5-5（AI の提案。human:kakimomokuri が 2026-10-01 に承認）
- 関連文書: [技術スタック](../../design/cargo-tracker/tech_stack.md)、[BDD 導入ガイド](../../reference/BDD導入ガイド.md)
- 関連 ADR: ADR-006
