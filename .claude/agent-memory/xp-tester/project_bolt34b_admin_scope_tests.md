---
name: project-bolt34b-admin-scope-tests
description: Bolt 34b（管理範囲・ADMIN_SCOPE_DENIED）のテストレビューで見つけた未解消点（2026-10-16）
metadata:
  type: project
---

Bolt 34b（US-16 AC2、IA-INV-14）のテストレビューの指摘（2026-10-16）。

- 「範囲外とない利用者は同じ 404」の MockMvc テストは本文が両方空で空振り。区別できないことは画面の層でも未比較（範囲外の 404 だけ見る）
- controller の `RoleChangeResult.OutOfScope` の枝（詳細を読んだ後に範囲から外れた）は未テスト。1 request 1 行の約束の要所
- 一覧の負例に陽性の対照（範囲内の利用者が出る）がない。findSummariesIn の 2 社以上の IN が未検証
- 割り当てのない管理者の自分の詳細 404 で試行の記録を未確認。確認ポイント 5 の「同じ企業のほかのシステム管理者は開ける」が未テスト
- テストの `user(…, SYSTEM_ADMIN)` と UiUsers が割り当てを暗黙に入れ、負例は DELETE で外す（隠れた準備）

**Why:** 範囲外と不在の区別不能（存在を漏らさない）が AC2 の核心で、MockMvc ではエラー画面を描かないため検証が空になりやすい。
**How to apply:** 34d 以降の S-20 の変更や担当範囲（W6）のレビューで、上の抜けが埋まったかを確かめる。[[project-bolt34-user-role-change-tests]]
