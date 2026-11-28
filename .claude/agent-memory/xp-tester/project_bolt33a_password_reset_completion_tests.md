---
name: project-bolt33a-password-reset-completion-tests
description: Bolt 33a（A-06 password の再設定の完了）のテストレビューの抜け。コミットの後の失効が未固定、/password-reset/x/y の負例の消失、password の段の別端末、誤りの文言 2 種（2026-10-15）
metadata:
  type: project
---

Bolt 33a（`git diff d06dacaa8..HEAD -- apps`）のテスターレビュー（2026-10-15）。

- 「コミットの後に失効・ロールバックしたら失効しない」は未固定。単体テストは markUsed=false の早期 return だけで、revokeAll をトランザクションの中へ移す変異が生き残る（Spring Session JDBC は REQUIRES_NEW なので統合テストも通る）
- 仮の A-06 のテスト削除と一緒に `/password-reset/{token}/x` が公開されない負例が消え、代わりがない（TOKEN_URL_PATTERN は `/*`）
- ほかの端末が password の段（A-02）にいる場合、再設定の後に TOTP を通せないことは未検証（主体の名前の索引が段の session に付くか不明）
- 誤りの文言は TOO_SHORT・COMMON・確認の不一致だけ。TOO_LONG・TOO_MANY_BYTES（全角の数え方の文言）は画面で未検証
- session の表の平文の確かめは LIKE の陽性の対照がない（31b と同じ型）
- 良い点: 期限ちょうど（サービスで 29 分発行 + 60 秒）、markUsed と isUsable の契約、同時の送信の T-100、ログイン中・別の利用者・失効した Cookie

**Why:** 認証の境界の「順序」（確定の後）と「消えた負例」は通るテストでは見えない。
**How to apply:** 33b 以降、テストを消す変更では消えた検証が移ったかを grep で確かめ、外部の自分のトランザクションで動く副作用はスパイで呼び出し時点の確定状態を見る。[[project-bolt31b-totp-enrollment-tests]]
