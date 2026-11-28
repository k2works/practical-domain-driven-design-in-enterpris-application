---
name: bolt32f-saved-input-tests
description: Bolt 32f（失効の時の入力の控え、session-input.js）のテストのレビューで見つけた空振りと未検証の振る舞い（2026-10-14）
metadata:
  type: project
---

Bolt 32f の入力の控えは JS の単体テストの仕組みがなく、12 本の Playwright シナリオ（session_timeout_ui.feature）だけで確かめている。

- 「送信が届いたら捨てる」シナリオは規則を外しても最後の else（戻り先が違えば捨てる）で消えるので落ちない（T-117 の空振り）。送信の後に同じ作成画面を開き直して戻らないことで確かめるべき
- 「CSRF のトークンが写らない」は hidden の除外を外しても changed() で弾かれるため空振り。トークンの値を変えてから写す必要がある
- sessionStorage の読み取りは単発の evaluate で再試行がない。別のタブの storage イベント待ちは競合しうる
- 案内の「消す」、radio・checkbox・select、同じ名前の欄、GET のフォーム、変えていない欄、8 時間ちょうど、A-03 の一文が出ないことは未検証

**Why:** 否定の確かめ（T-119）と規則の外し（T-117）は Bolt 32b・32d の Try で、今回も同じ型の空振りが残った。
**How to apply:** 控えの規則を変える Bolt のレビューでは、各「捨てる」規則を 1 つ外したときに落ちるシナリオがあるかを先に見る。関連 [[bolt32b-session-warning-tests]]
