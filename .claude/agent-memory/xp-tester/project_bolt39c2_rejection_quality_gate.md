---
name: project-bolt39c2-rejection-quality-gate
description: Bolt 39c2（取り込めない送信）の品質ゲート。V1〜V6 全部落ちた、V5 は行 22 ではなく 39c の UI シナリオが拾う、行 8 は V1 を拾わない（2026-11-04）
metadata:
  type: project
---

Bolt 39c2 の品質ゲート（2026-11-04）。変異 V1〜V6 はすべてどれかのテストで落ちた。check: test 4,140・documentationTest 5、失敗 0。uiTest 181 シナリオ（XML の testcase 362 件中 skipped 181 は 2 つ目の XML の重複）、失敗 0。Sonar PASS（新カバレッジ 95.7、重複 0.02、違反 0）。

- V1（FileTooLarge を情報源の欄に）は行 8 の網羅のテストでは落ちない（正規表現が欄と表示名の組を縛らない）。行 1・2・22 が拾う
- V5（説明の上限を 1,000 行に）は計画の見込み「行 22」ではなく、39c の UI シナリオ 2 本（通しと 320 px）が拾う。行 22 は欄の誤りの文言だけを見る

**Why:** 計画の「落ちる見込み」と実際の検知の差は次の計画の変異表を正す材料。
**How to apply:** 39c3 以降の変異表で、行 8 の正規表現を href と表示名の対応まで縛るか、見込みのテスト名を実際に合わせる。
