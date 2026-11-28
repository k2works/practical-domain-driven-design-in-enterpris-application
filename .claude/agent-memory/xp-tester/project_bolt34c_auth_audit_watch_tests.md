---
name: project-bolt34c-auth-audit-watch-tests
description: Bolt 34c（認証・監査の経路と見張り）のテストレビューの未解消点。特性テストの 3xx が段を区別しない、回数の戻し未固定、AT-11 の 2 本目の変異なし、AT-09 は漏れを捕まえない
metadata:
  type: project
---

Bolt 34c の開発レビュー（2026-10-17、読むだけ・テストは流さない）で出した指摘。

- 特性テスト（AuthenticationErrorHandlingIntegrationTest の回復コードで LOGIN_SUCCEEDED を書けない）は `is3xxRedirection` だけで、TOTP の段に留まる（`/login/totp?factor.type=totp`）か session を捨てた（`/session-expired`・`/login`）かを区別しない。既存の `redirectsToTotp()` が AuthenticationSecurityIntegrationTest:1717 にある
- 同テストは失敗の回数が 0 に戻らないこと・LOGIN_FAILED が出ないことを固定していない（前に 1 回誤らせて回数 1 を作れば固定できる）。recordSuccess は append が先なので順序を入れ替える変異が素通りする
- AT-11 の 2 本目「ルートの Configuration は ClockConfiguration だけ」は 1 本目と同時に落ちる変異しか試していない。2 本目だけを落とす変異（1 本目の例外をルートのパッケージに広げる、ClockConfiguration を動かす）が要る
- AT-09 の名前ごとの規則は改名を捕まえるが、一覧に載らない新しい暗号の部品（漏れ）は捕まえない。archunit.properties がなく failOnEmptyShould は既定 true なので `.allowEmptyShould(false)` は等価な変異（明示の文書の役）
- AT-10 はフィールドの `@MockitoSpyBean` だけ。`@IdentityIntegrationTest` のクラスの `@MockitoBean` もコンテキストを分けるが見ていない
- 回復コードのログインのテストの多くは RecoveryCodeLoginSecurityIntegrationTest（spy の基底を継がない）にある。spy を要るテストは基底を継ぐクラスに置く

**Why:** 後の Bolt で回復コードのトランザクションの分かれ目を変える議題がある。特性テストが段を区別しないと、変えたときの影響が見えない。
**How to apply:** 次の identity の Bolt やトランザクションの見直しで、特性テストの強さと AT-11 の 2 本目の変異を確かめる。関連 [[project-bolt32d-shared-context-tests]]、[[project-bolt31c-recovery-code-login-tests]]。
