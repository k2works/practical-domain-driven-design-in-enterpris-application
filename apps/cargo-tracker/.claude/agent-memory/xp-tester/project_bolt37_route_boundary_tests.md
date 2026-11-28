---
name: project-bolt37-route-boundary-tests
description: Bolt 37（区間 3 の候補探索・D-90 規則を次の区間の出発予定で選ぶ）のテストのレビューで見つけた弱点（2026-10-21）
metadata:
  type: project
---

Bolt 37 のテスターのレビュー（2026-10-21）。「ないこと」と規則の境界は概ね航海の上で成り立つデータで書かれ、変異 6 つは落ちた。残った弱点:

- `RouteCandidateFinder.visitedBefore` の「前の区間の揚地に戻る」枝は MAX_LEGS=3 では届かない（出発地に戻る枝だけが効く）。枝を消す変異は生き残る
- 確定（`RoutingCaseConfirmationTest`）に「確定の時刻には有効だが接続の時刻には切れていて後継がない → 確定しない」がない（評価器の単体だけ）
- `CONNECTION_DEPARTURE` が `presented()` の V-CONN の出発予定の文字列の写し。`SINGAPORE_12H_FROM_0330` の 03:30 は D-90 後は意味のない値
- 規則の境界の受入シナリオは SGSIN の接続を余裕 0（同値の境界）に置き、別の境界と結びついている
- 計画外: S-06 の NOT_CONNECTABLE の文言を変え、ui_design.md :174 が古いまま。`RoutingCase.java:243` の拒否のメッセージと `RouteConfirmationRejectionReason:21` に「確定の時刻で判定し直す」が残る

**Why:** 境界のテストを別の境界（同値は適合）に乗せると、無関係な変更で落ちる。届かない枝は変異で見えない。
**How to apply:** 経路設計の後の Bolt（37b・37f の Golden dataset）で、境界は 1 つのシナリオに 1 つ、写しの定数は fixture と共有するよう勧める。[[project-bolt32-login-lock-tests]]
