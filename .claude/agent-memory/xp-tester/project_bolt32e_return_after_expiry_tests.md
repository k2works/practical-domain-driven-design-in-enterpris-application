---
name: project-bolt32e-return-after-expiry-tests
description: Bolt 32e（失効の後に開いていた画面へ戻す）のテストのレビューで見つけた空振り・未検証の点（2026-10-14）
metadata:
  type: project
---

Bolt 32e のテストレビュー（2026-10-14）の主な指摘。

- 取消しのテスト（ReturnAfterExpirySecurityIntegrationTest の「利用を止められた後は…」）は、取消しの応答の Set-Cookie を捨てて古い Cookie で A-03 を開くため、SessionRevalidationFilter が覚えるように変わっても通る（T-119 の空振り）
- 既存 UI ステップ「警告が閉じ … が示される」は href="/session-expired" を固定。32e の JS が GET の画面で href を戻り先に替えるので、既存 3 シナリオと食い違う（build の uiTest 結果は JS の変更より前の時刻だった）
- returnablePages は SecurityConfiguration の private static。`/staff/` と `/documents/` の条件は外しても落ちるテストがない（T-117 の未達）
- 別の利用者のテストは同じ会社の荷主で、計画にある「認可で拒まれる」を確かめていない
- `?continue` の GET を辿って覚えた要求が消えることまでは確かめていない。query 付きの戻り先、8 時間の POST、回復コードの誤り、登録の途中の 15 分の持ち越しも未検証
- SessionLifetimeFilterTest は `request -> false` を渡すだけで、新しい分岐（remember・持ち越し・RETURN_PATH）の単体テストがない

**Why:** 次の Bolt（32f は同じ戻り先の鍵と失効の扱いを使う）で同じ穴を引き継がないため。
**How to apply:** 32f のレビューで、取消しの負例が応答の Cookie を辿っているか、案内のリンクの href の確かめが戻り先に追従したか、許す一覧の条件ごとの負例があるかを先に見る。関連: [[project-bolt32b-session-warning-tests]]、[[project-bolt32c-session-revalidation-tests]]
