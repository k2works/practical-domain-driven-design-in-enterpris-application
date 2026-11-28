---
name: project-bolt39b3b-external-audit-quality-gate
description: Bolt 39b3b 外部原本の採否の監査の品質ゲート（2026-11-04）。計画の変異 V1〜V9 は全部落ちた、書き手の表の先頭行の読み落としは生存、DE-13 の時刻は単体で無判別
metadata:
  type: project
---

Bolt 39b3b ステップ 7 の品質ゲート（2026-11-04、コミット 9b374557c）。計画の V1〜V9 はすべて見込みのテストで落ちた。check 緑（test 4058・documentationTest 5）、SonarQube PASS（新カバレッジ 95.7、重複 0.02、違反 0）。

追加で当てた変異の穴:
- LayerArchitectureTest の SOLE_WRITERS の **先頭の行** を読み落とす（`skip(1)`）変異は生存。行 14 の負例は最後の行（外部原本の採否）しか確かめないので、V8 は「末尾の 1 行」の読み落としだけを捕まえる。
- DE-13 だけ発生時刻を listener の時刻にすると、単体の行 2 は落ちず（行 15 の単体と行 8 の統合が落ちた）。行 2・4 の単体は発生時刻を確かめていない。

**Why:** 表駆動のヘルパーの変異を 1 行だけで確かめると、ほかの行の読み落としが見えない。
**How to apply:** 表とヘルパーの規則（[[project-bolt39b2-format-error-quality-gate]] 参照）では、表の全行に対して負例を回すか、行の数を固定するテストを提案する。
