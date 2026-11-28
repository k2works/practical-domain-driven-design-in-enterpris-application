---
type: Plan
title: "Bolt 57-2 計画と報告 - 未完了のイベント配信の検知（OBS-03）"
description: "W10-1 計画の Bolt 57-2（技術、SP 0）。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T10:41:36Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T20:59:05Z }
---

# Bolt 57-2 計画と報告 - 未完了のイベント配信の検知（OBS-03）

[W10-1 計画](iteration_plan-W10-1.md)の Bolt 57-2（技術、SP 0）。

**状態**: 置き場所は W10-1 計画の CP-3、本体の依存の追加は実装の前の確認で、どちらも 2026-11-27 に human:kakimomokuri が承認した。この報告は 56-1 でまとめて承認を受ける。検証（`validating-design`）: A 終盤の技術 Bolt。B 閾値は `non_functional.md` の OBS-03、対応は `operation.md` の RB-04 と一致。C 定期処理は Bolt 43-2・53-1 の形（`interfaces.scheduled`、ShedLock、無効化の設定）にそろえた。

## 計画と結果

- [x] (1) 判定の規則・定期処理・発行記録の読み取りのテストを先に書き、コンパイルの失敗を確かめた
- [x] (2) `operations` モジュール（規則 `EventBacklogPolicy`、ポート `IncompletePublications` と Modulith の実装、照会、5 分ごとの `EventBacklogMonitor`）
- [x] (3) `architecture_backend.md` にモジュールを足した

## 承認の議題

| # | 議題 | 内容 |
| :--- | :--- | :--- |
| 1 | `spring-modulith-events-core` を `testImplementation` から `implementation` にした | 承認済み。実行時にはもともと含まれていた。使うのは内部向けに近い SPI なので、Modulith の更新で直しが要る可能性がある |
| 2 | アーキテクチャのテストの「業務のコンテキストでないもの」の一覧（`BusinessContexts`）に `operations` を足した | CP-3 の「業務のコンテキストではない」の反映。この一覧を使う規則（他のコンテキストは公開 API だけ、など）は `operations` を対象にしない。`operations` の依存は Modulith の許可リスト（共有カーネルだけ）で守り、層の規則はそのまま適用される |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `com.example.cargotracker.operations.*`（H2 の統合テストを含む）、アーキテクチャのテスト、`ModularityTest`、`checkstyleMain`・`checkstyleTest`・`spotbugsMain`・`spotlessCheck` | 成功。全件のゲートは 56-1 |

統合テストは、実装の途中で合成ルートの `@Bean` がないことを 1 回捕まえた（アプリケーションサービスは部品探索の外）。

## 未確認

- PostgreSQL の発行記録の読み取り（統合テストの DB は H2）。警告のメールが届くこと。
- 滞留が続く間は 5 分ごとにログを出す。通知の量は実環境で確かめる。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-27 | 初版 | anthropic/claude-sonnet-5-5 |
