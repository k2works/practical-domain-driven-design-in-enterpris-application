---
name: project-bolt39b-ingestion-quality-gate
description: Bolt 39b 外部原本の取込の品質ゲート（2026-11-03）。変異 V1〜V10 は全部落ちたが見込みとのずれ、spotbugs の EI_EXPOSE_REP2、Sonar の restricted identifier
metadata:
  type: project
---

Bolt 39b ステップ 6 の品質ゲート（2026-11-03）。

- 変異 V1〜V10 はすべて落ちた。見込みとのずれ: V2 の外部版の比較は IngestionServiceTest の行 1 では落ちない（IdempotencyKeyTest と契約の行 7 だけ）。V4 は PostgreSQL の契約だけで落ち、統合の行 6 はない。V9 は DB の列の値を変える変異が作れない（取込の行を最初に書くため）ので結果の値で当てた
- `check` は spotbugsMain の EI_EXPOSE_REP2（IngestionService のリポジトリの保持 3 件）で失敗。`externaldata.application.internal` が exclude.xml のパッケージ指定（identity・notification）にない。ステップ 3〜5 で check を流していなかった
- Sonar は new_violations 12（テストコードの変数名 `record` の restricted identifier など）で Gate ERROR。カバレッジ・重複は合格

**Why:** 新しいコンテキストの application.internal を足すたびに spotbugs の除外とテストの変数名 `record` が引っかかる前例。
**How to apply:** 新しいコンテキストの最初の Bolt では、ステップの途中でも `spotbugsMain` を流し、テストで `record` という変数名を使わないよう伝える。関連 [[project-bolt34e-user-admin-refactor-tests]]
