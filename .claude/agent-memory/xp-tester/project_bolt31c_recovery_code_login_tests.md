---
name: project-bolt31c-recovery-code-login-tests
description: Bolt 31c（回復コードによるログイン、PasswordVerifiedUser）のテストレビューで見つけた空振りと抜け。31d 以降で直ったか確かめる観点
metadata:
  type: project
---

Bolt 31c（2026-10-13 レビュー、d78f0638e..b5813bdc9）のテストの未解消点。

- `RecoveryCodeVerificationServiceTest` の `entry.toString().doesNotContain(PLAIN)` は空振り（`AuditRecord` は toString を持たない）
- 同時の使用で負けた枝（`use` が false）で監査を書かないことを、単体でも PostgreSQL の統合テストでも確かめていない。統合テストは `OverlappingTransactions` の外側のトランザクションに包むので、照合（bcrypt）がトランザクションの外という確認ポイント 8 も検証されない
- 監査の追記の失敗のテストはロールバックの印だけで、`use` がトランザクションの中にあることを区別しない
- `RecoveryCodeAuthenticationProvider` の単体テストなし（IllegalArgumentException → 拒否とログ、主体なし、両方の欄）。入力トークンの `eraseCredentials` は ProviderManager が結果にしか呼ばない見込みで未検証
- 失敗の session は無効化されて消えるので、session に平文がないことの確かめは成功の session に「あるべきもの」を並べないと空振りしうる（T-106）
- 引数の解決は `FACTOR_TOTP` だけを見る。TOTP だけ・password なしの行がデシジョンテーブルから抜けている
- 「どちらの欄もない送信」と空白だけの回復コードのテストなし（確認ポイント 7）
- T-102 のテストで 2 人目は回復コードを持たない。拒否の理由と 2 人目のコードが通ることを並べていない
- 正規化は `\s` なので全角スペース・全角ハイフンを受けない（日本語の利用者の同値クラス）

**Why:** 31b と同じく「ないこと」の確かめの空振りと、境界の枝の未検証が繰り返している（[[project-bolt31b-totp-enrollment-tests]]）。
**How to apply:** 31d・32・33b のレビューで、上の点が直ったか、失敗の回数のロック（32）で同じ空振りが出ていないかを先に見る。
