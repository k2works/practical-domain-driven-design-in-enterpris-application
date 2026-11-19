---
name: project-bolt34d-user-status-change-tests
description: Bolt 34d（利用停止と再開）のテストレビューで見つけた未解消点（2026-10-17）
metadata:
  type: project
---

Bolt 34d（US-16 AC5）のテストレビューの指摘（2026-10-17）。関連テストは全部緑（統合 7・セキュリティ 11・ロールバック 1・単体）。

- updated_at の検証が空振り: users.add も MutableClock の NOW で updated_at を書くので、SQL の `updated_at = #{now}` を外しても通る
- 「過去の監査の行は残る」が件数の isGreaterThan で、行が消えても新しい行が多ければ通る（行の ID で確かめるべき）
- controller の OutOfScope 枝（`if (change)` で確定だけ記録）は 34b に続き未テスト。controller の単体テストがない
- 拒否の文言（NO_CHANGE の「利用状態は既に…」、エラー要約の自分・監督）はどの層でも未検証（T-145 で MockMvc から外し、UI は理由なしだけ）
- NO_CHANGE の拒否で詳細が古い版のまま（同時の更新では規則が版より先なので、次の操作で必ず Stale になる二度手間）
- セキュリティテストの login が毎回時計を 30 秒進める（6 つ目の login の写し。resetTotpStep の前例と食い違う）
- 計画の「確定する」ボタンが「停止する」「再開する」に変わった（ui_design は直した）

**Why:** 版・時刻・監査の「書く／書かない」の検証は、準備のデータが同じ値を持つと空振りしやすい。
**How to apply:** 34c（changeRoles・changeStatus の判定を通さない形の直し）や W6 の S-20 のレビューで、上の抜けが埋まったかを確かめる。[[project-bolt34b-admin-scope-tests]]
