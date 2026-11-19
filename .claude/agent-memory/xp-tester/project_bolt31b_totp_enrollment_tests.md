---
name: project-bolt31b-totp-enrollment-tests
description: Bolt 31b（A-07 の TOTP の登録・AAD・回復コード）のテストレビューで見つけた抜け。session の平文の確かめが空でも通る、切り替えの時刻の消去が未検証、匿名の A-07、重ねの待ちが非決定（2026-10-13）
metadata:
  type: project
---

Bolt 31b（`git diff 4831bccfa..ff93394e5`）のテスターレビューの指摘（2026-10-13）。

- `TotpEnrollmentSecurityIntegrationTest` の session に平文がない確かめは `noneMatch` で、session の行が 0 件でも通る（T-88 の空振り）。属性の名前と `v1:` の暗号文があることを先に確かめるべき
- 同じ session での利用者の切り替え（T-102）は「消去」と「AAD」の二重の守りで、テスト自身が「片方を外しても通る」と書いている。`LoginSuccessHandler` の `ENROLLMENT_STARTED_AT` の消去を守るテストはない（上限を外さない、ADR-011 の 102 行目に関わる）
- `TotpEnrollmentController` の `STARTED_AT` は null のときだけ置く（再読み込みで 15 分を延ばさない）が、未検証。登録の後も `STARTED_AT` を残すので A-02 は 15 分の上限で動く（計画の Green は「登録の後に消す」と書いていて食い違う）
- 匿名で `/login/totp/enroll` を開くテストがない。`SecretUnusable` と `DuplicateTotpEnrollmentException` の画面の層の写しも未検証
- `TotpEnrollmentServiceIntegrationTest` は `OverlappingTransactions` を写した自前の重ね。`failsWithin(500ms)` は待っている証拠にならない（遅いと AlreadyEnrolled になって落ちる。偽の成功ではなく不安定）。pg_stat_activity の Lock の待ちで確かめる案
- AAD の文字列の形（`totp-secret:` + 小文字の UUID）を直接確かめる単体テストがない（dev-data の暗号文のテストが間接に守るだけ）

**Why:** 認証の境界のテストは「通る」だけでなく「空振りしない」「どちらの守りが効いているか」を区別しないと、片方の退行を見逃す。
**How to apply:** 31c 以降の認証のレビューで、session の中身・切り替え・期限の起点のテストは非空の確認と守りごとのテストを求める。[[project-bolt20-shipper-approval-tests]] と同じく、重ねのテストは待ちの証拠を見る。
