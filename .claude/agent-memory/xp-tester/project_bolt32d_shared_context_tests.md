---
name: project-bolt32d-shared-context-tests
description: Bolt 32d のテストの共通化のレビュー。Base32 の長さの規則の変異が生き残る、埋め草の重ねの受理、コンテキスト共有で DB も共有になった（2026-10-14）
metadata:
  type: project
---

Bolt 32d（cd44efa4a..afb791423）のレビューで見つけた未解消点。

- Base32Test の「余りが 1・3・6 の長さ」の入力（M・MZX・MZXW6Y・MZXW6YTBO）はどれも最後の余りのビットが 0 でないので、`INVALID_REMAINDERS` の確かめを消しても余りのビットの規則で落ち、テストが通る。`A`・`AAA`・`AAAAAA` のような 0 のビットの入力が要る
- `MY==============`（16 文字）や `========` は 8 の倍数なので受理される。埋め草の数の誤りは `MY=` しか見ていない
- TestcontainersConfiguration の PostgreSQL はコンテキストごとの bean なので、IdentityTestClock・IdentitySpyIntegrationTestBase でコンテキストを共有すると DB も共有になる（認証 4 クラス・spy 2 クラス）。`unknownUserFailures`・`serviceErrors` は前後の差なので直列なら安全だが、並列化すると壊れる
- OverlappingTransactions の待ちの判定は「先の pid に止められた接続が 1 つ以上」で、止められたのが後の操作の接続かは見ていない

**Why:** 共通化でコンテキストが 50→42 に減った代わりに、クラスをまたぐデータの結合が新しくできた。
**How to apply:** 以後の Bolt で並列実行を入れる・共有 DB の件数を数えるテストを足すときは、利用者で絞るか差で見るかを確かめる。Base32 の規則を触るときは変異を 1 つずつ入れて落ちるかを見る。関連 [[project-bolt31b-totp-enrollment-tests]]
