---
type: Plan
title: "Bolt 40-18 計画 - 詳細の認可の統一とシステム起票の分離（W8 週全体レビュー D の残り）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T25:30:00Z }
---

# Bolt 40-18 計画 - 詳細の認可の統一とシステム起票の分離

W8 の週全体レビュー指摘 D のうち、Bolt 40-17 で W9 に残した 2 件を実施する。スキーマ・外部連携・認証の変更はない（内部の構造の整理）。

## 計画

- [x] (1) 詳細の照会（`ServiceCaseQueryService.detail`）に案件ごとの認可（`ServiceCaseAuthorization.canAccess`）を通す。役割の述語だけを広げても他社の案件が見えないことをテストで固定する（行 8b）
- [x] (2) システムの起票を利用者の起票から分ける。共通の手順を `ServiceCaseIntake`（パッケージ内）に抜き、利用者の起票は `ServiceCaseCommandService`、システムの起票は `SystemServiceCaseOpener`（起票元キーの冪等・既定のカレンダー）にする。`ServiceCaseCommandService.openBySystem` は廃止
- [x] (3) ArchUnit `SystemOpenArchitectureTest`: 画面（`interfaces`）から `SystemServiceCaseOpener` を呼ばない
- [x] (4) 品質ゲート・SonarQube・設計文書の更新

## 見積り

90 分。
