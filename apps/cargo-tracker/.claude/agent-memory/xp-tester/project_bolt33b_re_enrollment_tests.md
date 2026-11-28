---
name: bolt33b-re-enrollment-tests
description: Bolt 33b（A-08 認証アプリの再登録、US-18 AC9）のテストのレビューで見つけた空振りと抜け（2026-10-15）
metadata:
  type: project
---

Bolt 33b のテストのレビュー（2026-10-15）で見つけた傾向:

- 同時のテストの「待つ」の確かめ（`failsWithin(500ms)`）は、後の操作が行ロックの前に bcrypt（強さ 10）を 10 回回すと、ロックでなく時間で通る。待つことの確かめは、後の操作が重い処理を終えてから止まる形にするか、ロックの待ちを pg_locks で見る。
- 「2 つの再登録の同時」が、先をリポジトリの `replace` だけで模していた。先の再登録の回復コードが残ることは確かめていない。
- 二重の守り（成功のハンドラーで印を消す ＋ 画面が利用者の ID を比べる。T-102）は、外からの 1 本のテストではどちらかを外しても通る。守りごとにテストを置く。
- 全 URL の見張り（`AuthenticationSecurityIntegrationTest`）は新しい URL を自動で含むが、`checked` の `contains` に新しい URL を名指ししないと、除外の一覧に足しても気付かない。test_strategy.md の 33b の行は「除外に足す」と誤って書いていた。
- コントローラーの結果の分岐（Expired・SecretUnusable・Superseded・Unavailable）は、サービスの単体テストだけで、画面の層の確かめがなかった。

**Why:** 同じ形（同時のテスト・二重の守り・見張りの一覧）は認証の Bolt で繰り返し出る。
**How to apply:** 認証・同時の操作のレビューでは、待ちの確かめが時間で通らないか、守りごとにテストがあるか、見張りが新しい URL を名指ししているかを最初に見る。[[bolt32-login-lock-tests]]
