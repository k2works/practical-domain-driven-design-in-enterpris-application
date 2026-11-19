---
name: project-bolt35-booking-special-cargo-tests
description: Bolt 35（US-04 AC3・AC5、B-INV-04 特殊貨物の確定の拒否）のテストのテスターのレビューで見つけた未解消点（2026-10-19）
metadata:
  type: project
---

Bolt 35 のテスターのレビュー（2026-10-19、読むだけ）で挙げた点。

- POST の特殊貨物の枝（BookingController:101）は結果を画面に渡さず、照会し直した page.specialCargo() に頼る。page 側の判定が規則と分かれると、送っても理由なしで確定のボタンが出直す。BookingConfirmationPage.specialCargo に知らない値のテストがない（拒否リストの変異が生き残る）
- S-09 は特殊貨物のとき確定の領域だけ消し、表の「営業担当者の確認: 下のチェックで示します」と期限の注を残す（テストなし）
- S-09 のエラー要約にフォーカスを移すスクリプトがない（layout の scripts が ~{}）。他の画面は isFocused を確かめる（QuotationUiSteps:312 など）。AC3 の @ui のステップはフォーカスを見ない
- 「開発環境で特殊貨物の見積りを作れない」は事実と違う見込み: db/dev-data の SQL で READY_TO_BOOK を GENERAL で置いている（CHECK は DANGEROUS 等を許す）。UI テストにも JdbcTemplate の前例（PasswordResetUiSteps）
- 業務ルール層の特殊貨物のステップ（replaceCargoCategory）は承認の後に置き換えるので、経路設計の案件の cargo_category の写しと食い違う世界になる
- 否定の断言 `not(containsString("<a href=\"#"))` はページ全体・書式依存。フォームとチェックの不在は見ていない

**Why:** #43 の完了の定義（@ui と axe、支援技術の手動確認）が AC5 の画面の状態に届いていない。
**How to apply:** 35b 以降で S-09 や予約の確定に触れるとき、上の点が解消されたかを先に grep で確かめる。関連 [[project-bolt34f-s20-controller-split-tests]]
