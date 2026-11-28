---
name: project-bolt34f-review
description: Bolt 34f（S-20 の controller の 3 分割と UserDetailPage）の開発レビューで出した指摘と、W6 で持ち越す論点
metadata:
  type: project
---

Bolt 34f の開発レビュー（2026-10-18、読むだけ）で出した主な指摘。

- 範囲外の記録の「書き手は UserDetailPage の 1 か所」は grep の上では事実だが、LayerArchitectureTest の規則は `identity.interfaces.web` 全体を許すので構造では守られていない。W6 の 4 つ目の controller が直接 `AdministrationScopeDenialService` を受けても落ちない（規則を狭めるなら人に諮る）
- `UserDetailPage.refuse` は section を引数と onRejected の DetailForm の両方で渡す（2 か所の一致は規約頼み）。DetailForm の (section, error) は両方 null か両方非 null の不変条件が未強制
- H1 は controller については成り立つが、詳細に新しい領域（TOTP の解除）を足すと Section の定数と render のモデル属性（`error`/`statusError` の直書き）と detail.html を開く
- Javadoc の取り残し: `UserAdministrationRefusal:8`・`UserAdministrationQueryService:18` が「controller が書く」のまま（計画の注の一覧にもなかった）
- 役割の確定 POST は `@RequestParam long version` のままで、版の欠けは範囲の判定より前に 400（利用状態は 34d で直した。既存の非対称）

**Why:** W6（利用者の登録・TOTP の解除）で 4 つ目の操作を足すときの前提になる。
**How to apply:** W6 の計画・レビューで、規則の範囲を狭めたか、Section の足し方、記録の書き手を確かめる。[[project-bolt34d-review]] [[project-bolt34e-review]]
