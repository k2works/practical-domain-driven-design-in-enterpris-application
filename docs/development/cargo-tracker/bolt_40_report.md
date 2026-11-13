---
type: Plan
title: "Bolt 40 終了報告 - H2TrackingRecordLockTest のばらつきと PERF-06 の行 21 の競合を調べて直す"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T02:15:00Z }
---

# Bolt 40 終了報告 - H2TrackingRecordLockTest のばらつきと PERF-06 の行 21 の競合を調べて直す

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **`H2TrackingRecordLockTest` は現状で 10 回連続緑**（`ObjectOptimisticLocking`・`CannotAcquireLock`・`DuplicateKey` の件数は全回 0）。再現しなかったので、仮説 H1（メモリ DB の共有と再配信）の切り分け（計画の行 5・6）は流さず、テストも変えていない（「効くと分かった直しだけ入れる」。決定 (4)）。#50 への起票もしていない（原因を取れなかったのではなく、落ちなかったため） | 承認 | 下の「実測」 |
| 2 | **行 21 の直し**: 一覧が空のときだけ、却下の完了を 5 秒まで待ち、完了していれば通す・完了しなければ失敗にした。判定は `KeylessRejectionListCheck`（時刻にも Spring にも依存しない）に取り出し、単体 5 件で確かめた。**製品コードの差分は 0** | 承認 | 下の「成果」 |
| 3 | **仮説 H3 は未検証のまま**: 行 21 の本体は重いので流していない（計画のステップ 4 のとおり単体を正とする）。run 43318310748 の失敗が H3（テスト側の競合）だったかは、CI の性能ジョブの再実行で確かめる（W8 の締め。push の後） | 承認 | 完了条件 2 |
| 4 | **H2（固定 300ms の待ち）は直していない**: 落ちる原因ではなく通ってしまう側の欠陥で、実測で効くと分かったものだけ入れる規則に従った。台帳の技術的負債に残す（下の「申し送り」） | 承認 | 申し送り |
| 5 | **変異 M2・M4 は流していない**: M1（空なら常に通す）は行 3・4 が、M3（待ちの上限 0）は上限の確かめが落ちた。M2 は元の挙動で、行 2 の対象。M4 は行 7（ロックテストの直し）を入れていないので対象がない | 承認 | 下の「変異」 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40 回（W8 の 1 本目） |
| 期間 | 2026-11-13 |
| 種別・SP | 技術・SP 0 |
| 見積り／実績 | 見積り 150 分（作業のみ）。**実績は約 50 分（概算。時刻を厳密には記録していない）。差は約 −100 分**。打ち切り条件には該当しない。壁時計は `check` 7 分 40 秒・Sonar 11 分・10 回連続の実測を含み約 80 分（概算） |
| GitHub | 変えていない（#50 への起票なし） |
| 計画 | [Bolt 40 計画](bolt_40_plan.md) |

## 実測

`./gradlew cleanTest test --tests '*H2TrackingRecordLockTest'` を 10 回連続: **10/10 緑**。標準出力の `ObjectOptimisticLocking`・`CannotAcquireLock`・`DuplicateKey` は全回 0 件。

## 成果

| ステップ | 結果 |
| :--- | :--- |
| 1. 準備 | `H2TrackingRecordLockTest`・`MyBatisTrackingRecordLockIntegrationTest` が緑 |
| 2. 現状の 10 回連続 | 10/10 緑（上の「実測」） |
| 3. 切り分け | 落ちなかったので流さなかった（議題 1） |
| 4. 行 21 の直し | Red（実装なしでコンパイルが落ちる）→ `KeylessRejectionListCheck` で Green（単体 5 件）。`ExternalDataIngestionPerformanceTest` の `assertThat(page.items()).isNotEmpty()` を判定の呼びに替えた |
| 5. ロックテストの直し | 変更なし（議題 1・4） |
| 6. 品質ゲート | 下の表 |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check`（テストだけの変更なので `uiTest` は流さない。CP-2） | BUILD SUCCESSFUL（7 分 40 秒） |
| SonarQube（`sonar-local:check`） | Quality Gate **PASS**（新しいコードのカバレッジ 96.3%、重複 0.017%、違反 0） |

## 変異

| 変異 | 結果 |
| :--- | :--- |
| M1 空なら常に通す | 行 3・4 が落ちた |
| M3 待ちの上限を 0 | 上限の確かめ（`待つ上限は5秒`）が落ちた |

M3 を捕まえるのは定数の確かめだけで、待ちの長さそのものは測っていない。

## 完了条件

- [x] `H2TrackingRecordLockTest` が 10 回連続で緑
- [x] 行 21 の直しが単体で緑（CI の性能ジョブの再実行は push の後。W8 の締めで確かめる）
- [x] `@Disabled`・再試行・タイムアウトの延長がない
- [x] 製品コードの差分が 0
- [x] `check` と SonarQube が緑
- [x] 見積り／実績／差を書いた

## 申し送り

- W7 の報告書の `application-dev.properties:54` は、正しくは `application.properties:54`（`spring.modulith.events.republish-outstanding-events-on-restart=true`）。メモリ DB の URL は `application-dev.properties:3`。
- 技術的負債の表: `Bolt39d2b_行21` は「直した。CI の性能ジョブの再実行で確かめる」に更新する。行 57（PostgreSQL のラッチ）は残す。`H2TrackingRecordLockTest` の固定 300ms の待ち（H2）は通ってしまう側の欠陥として新しい行に足す。
- `H2TrackingRecordLockTest` は 10 回では落ちなかった。次に CI で落ちたら、落ちた回の標準出力から例外名を数え、そのとき #50 に起票する。

## 規律

標準入力につながる形は使っていない。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成（承認待ち） | anthropic/claude-sonnet-5-5 |
