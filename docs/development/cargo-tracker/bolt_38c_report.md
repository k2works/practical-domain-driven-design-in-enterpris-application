---
type: Plan
title: "Bolt 38c 終了報告 - S-20 のログインの資格（ロックの解除と管理者による TOTP の解除）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-27T03:48:59Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-27T03:49:00Z }
---

# Bolt 38c 終了報告 - S-20 のログインの資格（ロックの解除と管理者による TOTP の解除）

## 承認の議題（T-36。先に確かめてほしいこと）

システム管理者が S-20 の詳細の「ログインの資格」の領域から、ロック中の利用者のロックを外せるようにしました。認証アプリを失った利用者の TOTP の登録も、本人確認の記録とともに解除できます。解除した利用者は、次のログインで A-07 から登録し直します。本人への通知は 38d で足します。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **セキュリティ**: <br>・TOTP の解除は、確認の画面で読んだ登録（`enrolled_at`）のときだけ、資格と回復コードを 1 つのトランザクションで消す。確定の後に、その利用者の session をすべて消す。ロックは触らない<br>・ロックの解除は、ロック中の行だけを条件付きの更新で外す。session は消さない<br>・解除の前に発行した回復コードでは A-02 を通れない（本物のハッシュで確かめた） | `UserLoginCredentialService`、`AuthenticationFactorMapper.xml`、`UserLoginCredentialSecurityIntegrationTest` |
| 2 | **監査**: <br>・`ACCOUNT_UNLOCKED`（`LOCKED` から `UNLOCKED`、理由なし）<br>・`TOTP_RESET_BY_ADMIN`（理由 `IDENTITY_VERIFIED`、`after_state` は `method=IN_PERSON;verifiedAt=2026-11-09T01:30:00Z` の形。個人情報を含まない）<br>・監査を書けなければ、資格・回復コード・ロックの更新が戻り、session も消さない | `AuditRecord`、`UserLoginCredentialRollbackIntegrationTest` |
| 3 | **A-03 の文言（計画から変えた）**: 計画は 1 文目を「システム管理者が認証アプリの登録を解除したため、ログインし直してください。」に差し替える形でした。A-03 は session を消した理由を受け取らないので、差し替えるとほかの理由で失効した利用者にも誤った理由を示します。そこで前例（33a・33b）と同じく、1 文目の理由の並びに「またはシステム管理者が認証アプリの登録を解除したため」を足しました。そのうえで「システム管理者が認証アプリの登録を解除した場合は、次のログインで認証アプリを登録し直してください。」を続けています。開発レビューはこの形を妥当としました | キャプチャ `02-session-expired.png`、`SessionTimerSecurityIntegrationTest`（全文） |
| 4 | **決めてほしいこと: ロックの期限の表示**: 共通の `DateTimeDisplay.staff`（社内の形）を使ったので、UTC も並び、「ロック中（2026-11-09 10:45 Asia/Tokyo（UTC+09:00）（UTC 2026-11-09 01:45） まで）」と括弧が 3 重になります。<br>・推奨: 38c はこのまま受け入れ、形を 38c2 で「ロック中: 2026-11-09 10:45 Asia/Tokyo（UTC+09:00）（UTC 2026-11-09 01:45） まで」に直す（38c2 も S-20 の詳細を変えるため）<br>・表示は分で切り捨てるので、期限が 10:45:40 なら「10:45 まで」と示し、10:45 に試すとまだロックされています。推奨は、マニュアルに「1 分ほどずれることがあります」と添えること（38c2 で） | キャプチャ `11-user-detail.png`、`11-unlock-confirmation.png` |
| 5 | **決めてほしいこと: #49 への書き足し（GitHub）**: TOTP の解除の後に session の失効が失敗すると、警告のログだけを残し、古い端末の session が生き残ります。#49 に送った扱いのまま（計画どおり）ですが、TOTP の解除は「端末を失った」場面で使うので、ほかの場面より影響が大きいです。推奨は、#49 にこの場面を重い場面として書き足すこと | #49 |
| 6 | **動画の添付（GitHub）**: 承認の後に、2 本の動画を #47 へ添付する（`issue:attach-demo`。dry run は済み） | `docs/assets/demo/bolt-38c/unlock.webm`・`totp-reset.webm` |

計画は 2026-10-27 に承認を受けた（`b71b1a1fe`）。計画から変えたのは次の点です。

- **A-03 の文言**: 上の議題 3。計画の文言の表の最後の行は、実装の形に読み替える。
- **ロックの期限の表示**: 上の議題 4。計画の決定 (12) の例には UTC の併記がなかった。
- **CSRF の行 12**: 計画は「案内へ移り」と書いていた。固定した結果は、3xx で A-03（`SessionPaths.EXPIRED_URL`）へ移り、何も書かないこと。前例（Bolt 32f）と同じく、CSRF の拒否はその session を終える。実装の途中では 403 に見えたが、2 本目の POST で終わった session を使い回していたためだった。URL ごとにログインし直す前例の形に直した。
- **ロールバックのテストの置き場所**: `infrastructure.security` に置いた（計画は commandservices）。spy の基底 `IdentitySpyIntegrationTestBase` を継ぐため。前例 `UserStatusChangeRollbackIntegrationTest` と同じ。
- **詳細の SQL**: 詳細の select を `selectUserDetailRowsById`（`UserDetailRow` を返す）に置き換えた。一覧の SQL と行は変えていない。
- **UI テストの部品**: `BrowserSession` に `cookies()`・`restoreCookies()` を足した（別の端末に見立てる）。`UserAdministrationUiSteps` の要約のリンクは、詳細の場面で `hasCount(1)` を明示した。確認の画面で誤りが 2 つ出る場面は、既にある「最初の誤りのリンクを押すと」のステップを使う。
- **動画とキャプチャ**: 動画は 2 つのタグから撮るので、計画の 1 本（`unlock-and-totp-reset.webm`）ではなく `unlock.webm`・`totp-reset.webm` の 2 本になった。キャプチャは計画の 3 枚に加え、A-03 の文言を変えたので `02-session-expired.png` も撮り直した（T-197）。ほかの Bolt の撮り直しの差分 112 件は戻した。
- **開発レビューで変えた順**: TOTP の解除の確定は、入力に誤りがあるときだけ確かめ（`checkTotpReset`）を呼ぶ。サービスは、判定 → 登録の確かめ → 日時の規則の順に見る（拒否を日時の誤りより先に返す）。登録の時刻の hidden の解釈は `IdentityVerificationFormConverter` に移した。

### Red の確かめ

| テスト | 落ちた理由（中身を作る前） |
| :--- | :--- |
| 受入シナリオ（行 15。最初に流した。6 シナリオ） | 6 本とも、詳細に「ログインの資格」の領域がない。ほかの 310 本は緑 |
| ドメイン・リポジトリ・サービス・画面（行 1〜14） | テストを先に書き、新しい型と口がないことのコンパイルの誤りで落ちた。アサーションの段は、変異 V1〜V10 で補った（下の品質ゲート） |
| 開発レビューで足したテスト | 拒否が日時の誤りより先（`expected Refused[OVERSIGHT_ROLE] but was Unverifiable[FUTURE]`）、確定で確かめを呼ばない（`NeverWantedButInvoked`）、`enrolledAt` の解釈（メソッドがないことのコンパイルの誤り）。ほかは変異 R1〜R4 で落ちることを確かめた |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38c 回（W6 の 26 本目） |
| 期間 | 2026-10-27（計画の承認の記録 `b71b1a1fe` から、終了報告の承認の依頼まで） |
| 対象 | `UserDetail`・`UserDetailRow` と詳細の SQL、照会のサービスの `Clock`、`LoginFailure.unlock`、3 つのリポジトリの口と mapper と偽物、`UserLoginCredentialService` と結果 4 つ、`LoginCredentialRejection`・`IdentityVerificationMethod`・`IdentityVerificationPolicy`、`User` の判定、`AuditAction`・`AuditRecord`、`UserDetailPage`、`UserLoginCredentialController`・`IdentityVerificationFormConverter`、テンプレート（詳細の領域、確認 2 つ、A-03）、テスト、受入シナリオと Steps、動画、マニュアル 11 章・2 章・付録 B とキャプチャ、設計の文書、添付先 |
| GitHub | [#47](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/47)（前半。クローズは 38d）、[#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14)（ロックの解除） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 38c 計画](bolt_38c_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | ui_design、domain_model、data_model、non_functional、user_story、ADR-011・ADR-012、architecture_backend、operation、test_strategy | `72c779bcc` |
| 2. 受入シナリオの Red | `manage_user_login_credentials_ui.feature`（6 シナリオ） | `72c779bcc` |
| 3〜4. Red と Green | 上の議題 1〜3。TOTP の解除は打ち切りの 100 分に届く前に緑になったので、分けていない | `72c779bcc` |
| 5. マニュアル・キャプチャ・動画 | 11 章に 11.6・11.7 とログインの資格の領域、2 章（2.1・2.4・2.6）、付録 B（Q1-1・Q1-9）、キャプチャ 4 枚、動画 2 本 | `72c779bcc` |
| 6. 品質ゲートと開発レビュー | 変異 11 個と 4 個、レビューの指摘の反映 | `72c779bcc` |
| 7. 終了報告と配備 | この報告、CI と配備 | この報告のコミット |

### 行とテスト（完了条件の「行 1〜15 に名乗るテスト」）

行 1〜15 は、次のテストが名乗っている。

- `UserAdministrationControllerTest`・`UserAdministrationQueryServiceTest`・`MyBatisIdentityRepositoriesIntegrationTest`（行 1）
- `UserLoginCredentialControllerTest`・`UserLoginCredentialSecurityIntegrationTest`（行 2〜7・9・11・12）
- `UserLoginCredentialServiceTest`・`UserLoginCredentialIntegrationTest`（行 4・6・8a・8b・9・10）
- `IdentityVerificationPolicyTest`・`IdentityVerificationFormConverterTest`（行 7）
- `UserLoginCredentialRollbackIntegrationTest`（行 13）
- `AuthenticationFactorRepositoriesContract`（行 14。偽物・H2・PostgreSQL）
- `manage_user_login_credentials_ui.feature`（行 15。幅 320 px と axe を含む）
- `SessionTimerSecurityIntegrationTest`（A-03 の全文）

### #47 と受入条件の照合（38c の時点）

| 受入条件・完了の定義 | 38c で満たしたもの | 38d に残すもの |
| :--- | :--- | :--- |
| US-18 AC9: 別経路で本人確認して登録を解除する | 本人確認の手段と日時の記録、資格と回復コードの削除、session の消去、`TOTP_RESET_BY_ADMIN` | — |
| US-18 AC9: TOTP を再登録できる | 次のログインで A-07（`@US-18-AC9`、`@demo-bolt-38c/totp-reset`） | — |
| US-18 AC9: 本人へ通知される | — | 本人への通知と、その確かめを `@US-18-AC9` に足す |
| #47 の完了の定義: `@US-18-AC9` が `@wip` なしで通る | 解除と再登録の分は `@wip` なしで通る | 通知の確かめを足して満たす |
| #47 の完了の定義: セキュリティの統合テスト、アーキテクチャテスト、`@ui` と axe、マニュアル | 満たした | 通知の分 |
| US-18 AC3 の運用（ロックの解除。#14 で追う） | `@US-18-AC3`（`@demo-bolt-38c/unlock`） | — |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` 3218 件、失敗 0（前の 3125 件から 93 件増えた。`test` の XML で数えた）。`uiTest` 316 件、失敗 0（前の 304 件から 12 件増えた）。`manualScreenshots` 26 件、失敗 0。`demoVideo` 成功 |
| SpotBugs | 0 件 |
| 変異（計画の V1〜V10） | 11 個すべてが落ちた（V7 は 2 つに分けた。バイトのまま読み書きした。T-200）。<br>・V1 解除の条件を外す（行 4・10）<br>・V2 回復コードを消さない（行 6）<br>・V3 session を消さない（行 6）<br>・V4 未来を受け付ける（行 7）<br>・V5 自分と監督の役割を拒まない（行 9）<br>・V6 範囲より先に値を解釈する（行 11）<br>・V7a・V7b 監査を書かない（行 3・6）<br>・V8 ロックがなくても解除を出す（行 1）<br>・V9 TOTP の解除でロックも外す（行 6）<br>・V10 登録の時刻の条件なしで消す（行 14。最初の一括の実行では対象に契約テストを入れておらず生き残った。対象に入れて流し直すと落ちた。サービスは削除の前に登録の時刻を確かめるので、SQL の条件が効くのは競合のときだけ） |
| 変異（レビューの直し） | R1 A-03 の文言、R2 荷受人の企業の利用者に操作を出さない、R3 監督の役割にロックの解除を出す、R4 回復コードを消さない（古い回復コードでの A-02）。4 つとも落ちた |
| SonarQube | PASS（網羅率 95.5%、重複 0.1%、違反 0。1 回目はテストのラムダの中の 2 つの呼び出し（S5778）で落ちたので直した） |
| `okf:check` | ERROR 0 |
| CI | `72c779bcc` で `check`・`ui`・`deploy-demo` が 1 回で成功した（デモ環境は v462 で動いている） |

### 開発レビューの指摘と扱い

| 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- |
| 中 | CSRF の行 12 が 302 と 403 の両方を許し、session を使い回していた | 前例の形に直し、A-03 への 3xx に固定した |
| 中 | 古い回復コードで A-02 を通れないことを、ログインを試して確かめていない | 本物のハッシュで 1 本足した（変異 R4 で落ちる） |
| 中 | TOTP の解除の確定で判定の読み出しが 3 回 | 入力に誤りがあるときだけ確かめを呼ぶ。サービスは拒否を日時の誤りより先に返す |
| 低 | A-03 の文言の変更、1 文目の全文のテストがない | 議題 3。全文のテストを足した |
| 低 | ロックの期限の括弧の重なり、分の切り捨て | 議題 4 で諮る |
| 低 | 三項の入れ子（S3358）、監査の工場の重複、hidden の解釈の置き場所、`.first()` で strict mode の確かめが消えた、テストの網羅の抜け（荷受人の企業・監督の役割のロック・`locked_until` のロールバック・実行者の全文・UI の日時）、マニュアル 2.1 と付録 B の「担当営業に」 | 直した |
| 低 | 確認の画面の要約が `UserRegistrationPage.Summary` に依存する | 見送る。3 つ目の確認の画面で共通の部品に寄せる |
| 低 | 計画の資産の一覧と実物の食い違い（動画 2 本、`02-session-expired.png`） | この報告に記録した |
| 低 | TOTP の解除の後の失効の失敗 | 議題 5 で諮る |

## デモ項目

- 動画: [ロックの解除とすぐのログイン](../../assets/demo/bolt-38c/unlock.webm)、[TOTP の解除と A-03 と A-07 の再登録](../../assets/demo/bolt-38c/totp-reset.webm)（承認の後に #47 へ添付する）
- マニュアル: 11 章（11.2 のログインの資格、11.6 ロックの解除、11.7 認証アプリの登録の解除）、2 章（2.1・2.4・2.6）、付録 B（Q1-1・Q1-9）

## 判断と学び

- 理由を受け取らない共通の案内の画面（A-03）に理由を足すときは、文を差し替えず、理由を並べて条件付きの 1 文を足す。差し替えると、ほかの理由で来た利用者に誤った理由を示す
- 競合のときだけ効く SQL の条件（登録の時刻）は、サービスの単体や統合では落ちない。変異の対象のテストに契約テストを入れて、初めて確かめられる
- CSRF の拒否が session を終える仕組みでは、1 つのテストで同じ session の POST を続けると、2 本目は別の場面を見る。URL ごとにログインし直す

### ふりかえり（KPT）

- Keep: 開始準備と 2 つの整合性検証で、確認した登録だけを消す（`enrolled_at`）、日時の欄の前例、A-03 の理由を計画の段階で決められた。変異 15 個がすべて落ちた
- Problem:
  - 変異の対象のテストを選ぶとき、契約テストを外していて、V10 が一度生き残った
  - 変異の担当と親が同じ build のディレクトリで Gradle を並べて流し、結果の XML が消え合った。途中で中断した実行で V1 の変異が XML に残った（照合で気づいて戻した）
- Try:
  - T-214: 変異の対象のテストには、変異するファイル（mapper の XML など）を使う契約テストを必ず入れる
  - T-215: 変異やゲートを流している間は、ほかの担当に同じアプリの Gradle を流させない。流す担当を 1 つに決める

### 既知の課題（後に持ち込む）

- エラー要約の二重の読み上げ・chromium だけ、ACC-06、hidden の重複、見分けにくい名前（38b4 までの既知の課題）
- ロックの期限の表示の形と分の切り捨て（議題 4）
- 確認の画面の要約の共通の部品（3 つ目で寄せる）
- TOTP の解除の後の失効の失敗（#49。議題 5）
- 本人への通知（38d）。#47 を閉じるのは 38d、US-16 の照合を閉じるのは 38g

## 次の Bolt

Bolt 38c2（停止の理由・停止した人・時刻を S-20 の詳細に示す。#50。開始準備は `opening-iteration` で）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-27 | 終了報告を承認した（議題 1〜6。議題 4 は推奨のとおり 38c2 で表示の形を直し分のずれを添える、議題 5 は #49 に書き足す）。動画を #47 へ添付した | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-27 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38c 計画](bolt_38c_plan.md)、[リリース計画](release_plan.md)（W6 節の 38c の行）
- [Bolt 38b4 終了報告](bolt_38b4_report.md)、[Bolt 34d 計画](bolt_34d_plan.md)（停止と再開の前例）
- [UI 設計](../../design/cargo-tracker/ui_design.md)（S-20・A-03）、[ユーザーマニュアル 11 章](../../manual/11-利用者の管理.md)、[ユーザーマニュアル 2 章](../../manual/02-ログインと共通の画面.md)
