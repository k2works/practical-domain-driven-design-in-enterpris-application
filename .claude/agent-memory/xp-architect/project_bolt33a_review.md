---
name: project-bolt33a-review
description: Bolt 33a（password の再設定 A-06）のアーキテクトレビューの指摘。今の session の捨て方の順序が人の決定と食い違い、コミット後の失効が呼び出し側の tx に依存
metadata:
  type: project
---

Bolt 33a のレビュー（2026-10-15）の指摘。

- 人の決定（開始準備の決定 2(6)、ADR-012 決定 3、ui_design A-06 の表）は「完了の前に今の認証と session を捨てる」。実装（PasswordResetController の Completed 分岐）は完了の後に捨てる。前に捨てると方針の誤りの再送でトークンの ID を失うので実装の方が筋がよいが、決定を黙って変えている → 人に諮って文書を直す。
- コミットの後の失効は TransactionTemplate（REQUIRED）の戻りの後に同期で呼ぶだけ。外側の tx の中で呼ばれるとコミット前に失効する。ガード（実 tx の不在の確認）か afterCommit の登録を提案。
- 手動の SecurityContextLogoutHandler は LogoutSuccessEvent を出さず、LOGOUT の監査が残らない（別の利用者のブラウザでも）。
- 失効は principal_name の索引だけ。コミットの直前に旧 password で通った password の段の session は残りうる（資格の版を session に持たせる案は後回し）。
- 「暗号の部品は crypto にある」ArchUnit 規則に BcryptPasswordHasher・BundledCommonPasswordList が入っていない。

**Why:** 決定と実装の食い違いは AI-DLC の承認ゲートを迂回する。**How to apply:** 次の Bolt の開始準備で、上の扱いが決まったかを確かめる。[[project-bolt32c-review]]
