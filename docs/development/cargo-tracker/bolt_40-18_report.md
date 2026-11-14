---
type: Plan
title: "Bolt 40-18 終了報告 - 詳細の認可の統一とシステム起票の分離"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T25:30:00Z }
---

# Bolt 40-18 終了報告 - 詳細の認可の統一とシステム起票の分離

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | 計画は人の指示（「W8 の指摘 D を残さない」）の範囲の内部整理。スキーマ・認証・外部連携の変更なし。**この Bolt は人の明示の指示を受けずに私が始めた**（W9 送りにしていた項目を W8 で行った）。不要なら差し戻してよい | 承認 | [計画](bolt_40-18_plan.md) |
| 2 | 利用者向けの振る舞いは変わらない（詳細は今の役割では全員が通る）。マニュアルの更新は不要 | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-18 回（W8 の追加） |
| 期間 | 2026-11-14 |
| 種別・SP | 技術・SP 0 |
| 見積り／実績 | 見積り 90 分、実績は概算 60 分 |

## 成果

- `ServiceCaseQueryService.detail` が `canAccess` を通す（行 8b）
- `ServiceCaseIntake`・`SystemServiceCaseOpener` を新設。`ServiceCaseCommandService` から `openBySystem` を除いた。冪等・既定のカレンダーのテスト 4 件を `SystemServiceCaseOpenerTest` へ移した
- `SystemOpenArchitectureTest`（interfaces からの呼び出しを禁じる）
- 結果: 関連テスト緑。品質ゲートと Sonar は下の表

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| 関連テスト（ユニット・ArchUnit） | 緑 |
| `spotlessCheck check documentationTest uiTest` | BUILD SUCCESSFUL（25 分 47 秒） |
| SonarQube | 実行結果を追記する |

## 申し送り

W9 に残す: AC1 の同時採番の統合テスト、`inquiry_record.case_number` の索引。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成 | anthropic/claude-sonnet-5-5 |
