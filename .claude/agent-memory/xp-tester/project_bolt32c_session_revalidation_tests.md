---
name: project-bolt32c-session-revalidation-tests
description: Bolt 32c request ごとの利用者の確かめ（US-18 AC6）のテストレビューで見つけた未解消点（2026-10-14）
metadata:
  type: project
---

Bolt 32c（793185201..08a10dc0f）のテストレビューの指摘。

- 「公開の URL では読まない」の確かめが cookie なしの /login だけ。SessionRevalidationFilter は URL を見ないので、ログイン中の /css/** /js/** でも読む（計画 L239 と食い違い）
- 同じ session の並行 request（画面 + css/js）で SESSION_REVOKED が複数行になりうる。順次の 1 行しか確かめていない
- 役割 0 件の fail-closed にログの確かめがない（計画 L240 は求める）。LogCapture の確かめは利用者 ID の部分一致だけでレベルを見ない
- 役割を外したときのナビの doesNotContain に、外す前にリンクがあることの確かめがない（T-106）
- A-07（回復コード）の password 段の主体の取消しは未検証。A-02 だけ
- 契約テスト「役割の変更は次の読み出しに出る」は事前の読み出しがない。@Transactional の中で読むと MyBatis の 1 次キャッシュで古い値になる点に注意
- 監査の書き込みの誤りの統合テストは spy の doThrow なので「ロールバック」は単体テストの偽 TM だけで確かめている

**Why:** 次の Bolt（32d・34 の利用停止の操作）で同じ確かめを再利用するため。
**How to apply:** 32d／34 のレビューで、これらが直ったか、同じ形が繰り返されていないかを見る。[[project-bolt31c-recovery-code-login-tests]]
