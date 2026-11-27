---
type: Plan
title: "Bolt 58 計画と報告 - 取込がない日の検知（FRS-04）"
description: "W10-1 計画の Bolt 58（技術、SP 0）。"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T10:53:52Z }
---

# Bolt 58 計画と報告 - 取込がない日の検知（FRS-04）

[W10-1 計画](iteration_plan-W10-1.md)の Bolt 58（技術、SP 0）。

**状態**: 「当日」の数え方は W10-1 計画の CP-6、アーキテクチャの規則の例外は実装の中の確認で、どちらも 2026-11-27 に human:kakimomokuri が承認した。この報告は 56-1 でまとめて承認を受ける。検証（`validating-design`）: A 終盤の技術 Bolt。B 時刻と宛先は `non_functional.md` の FRS-04、対応は `operation.md` の RB-06 と一致。スキーマは変えない。C 定期処理は Bolt 57-2 と同じ形。

## 計画と結果

- [x] (1) 取込の有無の契約テスト（H2・PostgreSQL 共通）、当日の始まりの規則、照会、定期処理のテストを先に書き、コンパイルの失敗を確かめた
- [x] (2) `IngestionRepository.existsSince`、`IngestionCadence`、`MissingIngestionQueryService`、`MissingIngestionScheduler`（毎日 11:00 JST、ShedLock）
- [x] (3) `operation.md` の注、マニュアル 12.1

## 承認の議題

| # | 議題 | 内容 |
| :--- | :--- | :--- |
| 1 | **【前の決定の変更】** `ModularityTest` の外部データの規則に例外を足した | 承認済み。`externaldata.interfaces.scheduled` に限り SLF4J と ShedLock を許す規則を分けて足し、元の規則の対象から外した |
| 2 | 不足は情報源ごとに見ない | CP-6 の「当日の取込が 0 件」のとおり。情報源ごとの不足は検知しない |

## 実装の中で分かったこと

`SELECT EXISTS (...)` の形では、H2 だけが同じトランザクションで行を足した後も「ない」を返した（PostgreSQL の契約テストは成功した）。原因は確かめていない。件数で判定する SQL に変え、両方の契約テストが成功した。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `com.example.cargotracker.externaldata.*`（H2・PostgreSQL の契約を含む）、アーキテクチャのテスト、`ModularityTest`、静的検査 | 成功。全件のゲートは 56-1 |

## 未確認

- 警告のメールが届くこと。マニュアルの記述は、実環境の設定の後の振る舞い。
- 11:00 にアプリが止まっていた日は調べない（作りの制限）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-27 | 初版 | anthropic/claude-sonnet-5-5 |
