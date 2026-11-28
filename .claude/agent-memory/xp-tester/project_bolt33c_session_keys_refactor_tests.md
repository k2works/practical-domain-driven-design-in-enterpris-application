---
name: project-bolt33c-session-keys-refactor-tests
description: Bolt 33c（SessionKeys・SessionPolicy への移動）のテストレビューで見つけた未解消点。SAVED_REQUEST の固定が文字列だけ、tracking の空振りの余地、RecoveryLoginTest の同語反復
metadata:
  type: project
---

Bolt 33c（2026-10-15）のテストレビューで確かめた未解消点。

- `SessionKeysTest` の `SAVED_REQUEST` は文字列の固定だけで、Spring Security の `HttpSessionRequestCache` の既定と一致することは確かめていない（持ち越しの統合テストが間接に守るだけ）
- `SchemaCommentIntegrationTest` の `isNotEmpty` は全体に効くだけで、スキーマごとに表があることを見ない（`tracking` の綴りの誤りや表 0 件でも緑）
- `RecoveryLoginTest` は期待値を `SessionPolicy.RECOVERY_LOGIN_LIFETIME` から作る同語反復（文字の 15 分は統合テスト :556 が守る）
- T-128 の ArchUnit 規則は `Pattern.quote` だけを見る。エスケープなしの連結（33c で直した 3 か所の元の形）は見張れない
- 期間の文言（customer/staff の「15 分を過ぎた」、A-07/A-08 の「15 分」、session-warning の「8 時間」）はテンプレートに直書きのまま。A-01 だけ LockPolicy から描いた
- `TotpReEnrollmentController` の画面だけの属性の名前 2 つは値の固定テストがない

**Why:** 振る舞いの変わらないリファクタリングで一致テストを消した代わりの守りが十分かを見た結果。
**How to apply:** 33d 以降で session の属性・期間・Playwright の照合に触れるときに、上の穴を先に塞ぐ提案をする。関連 [[project-bolt32e-return-after-expiry-tests]]
