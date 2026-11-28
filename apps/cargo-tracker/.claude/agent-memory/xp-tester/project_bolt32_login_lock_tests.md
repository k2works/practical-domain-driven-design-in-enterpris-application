---
name: project-bolt32-login-lock-tests
description: Bolt 32（US-18 AC3 ログインのロック）のテストレビューで見つけた抜けと弱い確かめ（2026-10-13）
metadata:
  type: project
---

Bolt 32 のテストレビュー（c089b7e04..2fc2064b2）で見つけた点。

- 計画（bolt_32_plan.md の 225 行）にある監査のリスナーと LockAwareUserDetailsChecker の単体テストが無い。FakeLoginAttemptService は provider のテストにしか使われていない。リスナーの「Locked → recordLockedAttempt」を「recordFailure」に変えるミューテーションは、ロック中はリポジトリが数えず理由 LOCKED にするので統合テストでは生き残る
- AuthenticationErrorHandlingIntegrationTest の SERVICE_ERROR の確かめが表全体の count isPositive（他テストの行で空振り）。前後の差で数える形（AuthenticationSecurityIntegrationTest の unknownUserFailures）にすべき。名前の「数えない」も未検証
- 最初の失敗の同時の作成のテストはラッチだけで、競合（DuplicateKey）が起きた保証がない（Bolt 30 の T-100 と同じ罠）
- 回数の加算の後に監査の追記が失敗したら回数も戻ることを本物の DB で確かめていない（単体は偽のトランザクションマネージャーの rolledBack だけ）
- 数えない経路のうち、利用停止・無効な企業の正しい password、A-07 の登録のコードの誤りは回数を確かめていない
- RandomTotpSecretIssuerTest の QR 読み戻しの揺れ（1% ほど）を PURE_BARCODE で直した

**Why:** 次の Bolt（32c 以降）のレビューで同じ抜けが直ったかを追うため。
**How to apply:** identity の認証の入口を触るレビューでは、リスナーの単体テストの有無、表全体の count、同時実行テストの重なりの保証を最初に見る。[[project-bolt31c-recovery-code-login-tests]]
