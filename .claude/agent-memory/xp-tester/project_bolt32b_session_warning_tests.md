---
name: project-bolt32b-session-warning-tests
description: Bolt 32b（session の警告と延長、ACC-06）のテストレビューで見つけた未検証の分岐と揺れの元（2026-10-14）
metadata:
  type: project
---

Bolt 32b のテストレビュー（2026-10-14）で指摘したこと。次の Bolt（32e の入力の保持など）で同じ部品に触るときに確かめる。

- 延長の失敗（403・500・通信の失敗）と失効の応答（302 → opaqueredirect）の JS の分岐は、どの層のテストも通らない。画面の層はサーバーの session を失効させない方針なので、`page.route("**/session/extend")` で応答を差し替えるしかない
- 「閉じる」・Esc・閉じた後のフォーカスの戻り・延長の後の 8 時間の警告への切り替え・「1 分未満」・localStorage を使えないときは未検証
- 社内業務 Web（layout/staff）の警告は統合テストにも UI にもない（顧客 Web だけ）
- SessionLifetimeFilterTest の「password の段に残りを置かない」は AUTHENTICATED_AT のない session で確かめるので、FACTOR_TOTP の判定を消しても落ちない。前の利用者の時刻が残った password の段で確かめる必要がある
- Playwright の clock.install の後は時刻が実時間で流れる（pauseAt していない）。「あと 5 分」の文言は ceil で丸めるので δ < 1 分なら安定するが、決定的ではない

**Why:** 警告の JS はブラウザの時計とタイマーで動き、サーバーの失効と分けてテストしているため、両者をつなぐ応答の分岐が抜けやすい。
**How to apply:** session の警告・延長・32e の入力の保持のテストを見るときは、上の抜けが埋まったかをまず確かめる。関連: [[project-bolt32c-session-revalidation-tests]]
