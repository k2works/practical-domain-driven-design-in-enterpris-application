---
type: Plan
title: "Bolt 47 終了報告 - 追跡の照会の確認中・鮮度超過のお知らせ（US-09 AC2・AC3）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T17:00:00Z }
---

# Bolt 47 終了報告 - 追跡の照会の確認中・鮮度超過のお知らせ（US-09 AC2・AC3）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | AC3 の「停止中」を社内と同じ鮮度超過（次の予定の実績の予定時刻から 36 時間）に当て、「次回の更新」を次の予定の実績の予定時刻（なければ未定）にした。Bolt 39g4 の後送りの変更（台帳 D-167。仮） | 承認 | [計画](bolt_47_plan.md) |
| 2 | 47 と 47-1 を 1 つにまとめた（画面の変更が C-10 の 1 画面で、スキーマ等の変更がないため）。US-09 の 2 SP をここで数える。GitHub の Issue のクローズは人の許可が要るので実行していない | 承認 | 計画 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 47 回（W9。機能。US-09 AC2・AC3） |
| 期間 | 2026-11-14 |
| SP | **2**（US-09） |
| 計画 | [Bolt 47 計画](bolt_47_plan.md) |

## 成果

`CustomerTrackingView`（次の予定の実績の予定時刻・鮮度超過）、`CustomerTrackingQueryService`（鮮度の判定）、C-10 の「確認中のお知らせ」「最新性についてのお知らせ」、UI シナリオ 2（`customer_tracking_notice_ui.feature`）、マニュアル 5.3、ui_design の C-10、受入条件の照合の表。画面の検査（axe）で、アラート内のリンクの色のコントラスト違反が出たので `alert-link` にした。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | 1 回目は `TrackingRecordCustomerViewTest`（荷主向けの項目の見張り）で失敗。足した 2 項目（次の予定の実績の予定時刻・鮮度超過）は開示してよいと判断して見張りを直した。流し直して BUILD SUCCESSFUL（31 分 34 秒） |
| SonarQube | 1 回目は新しい違反 3 件（制限された識別子 `record`）で FAILED。名前を直して流し直し、**PASS**（新しい違反 0、カバレッジ 95.8%） |

## 申し送り

次は Bolt 46・46-1（US-24 AC2 辞退・AC3 相談）と Bolt 44 系（US-21）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
