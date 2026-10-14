---
type: ADR
title: "ADR-012: 認証の主体は共有カーネルの型にし、password の段を Spring Security の form login と Spring Session JDBC で作る"
description: "US-18 の password によるログインと session を、Spring Security 7 の form login と Spring Session JDBC で作る。"
tags: [adr, authentication, session]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-14T13:20:30Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-06T06:51:29Z }
---

# ADR-012: 認証の主体は共有カーネルの型にし、password の段を Spring Security の form login と Spring Session JDBC で作る

US-18 の password によるログインと session を、Spring Security 7 の form login と Spring Session JDBC で作る。ほかのコンテキストは、共有カーネルに置く認証の主体の型だけを受け取り、Spring Security の型にも `identity` にも依存しない。

日付: 2026-10-06

## ステータス

承認（2026-10-06、human:kakimomokuri が Bolt 14 の終了報告の承認で採用した。Bolt 14 の確認ポイント 2・3 の方針）

## コンテキスト

- US-18 の password の段（AC1 の password と session、AC2、AC4、AC5）を Bolt 14 で作る。TOTP・ロック・再設定・権限の取消し（AC6）は W5（[Bolt 14 計画](../../development/cargo-tracker/bolt_14_plan.md)）。
- これまで、提出者・回答者・審査者・承認者は、設定の仮の主体（`ProvisionalActorProperties`）で決めていた。`quotation` の 4 つの controller が使う。
- `identity` は `quotation :: events` に依存する（KPI 計測記録が DE-01・DE-03 を購読する）。`quotation` が `identity` に依存すると循環する（Bolt 13 レビュー D-48）。
- アクセス・監査は単純なモデルで、認証の仕組みは実績のある部品に任せる。認証の成功・失敗は、同じ request の中で同期に監査記録に書く（IA-INV-08）。
- TOTP の方式は [ADR-011](011-mfa-totp.md)（提案）で、W5 で採否を決める。password の段は、TOTP の採否と独立に決まる。
- 開発環境では A-01 を入力済みにする（2026-10-06 の人の決定）。

## 決定（案）

### 1. 認証の主体の型

- 共有カーネル（`shared`）に、認証された利用者 `AuthenticatedActor`（利用者 ID・企業 ID・役割（1 つ以上）・表示名・企業名）と役割 `Role`（BR-15 の 8 役割）を置く。
- `quotation` の controller は、引数に `AuthenticatedActor` を書くだけでこの型を受け取る。`identity` の引数の解決（`AuthenticatedActorArgumentResolver`）が Spring Security の主体から作って渡すため、`@AuthenticationPrincipal` も使わない。Spring Security の型（`UserDetails`、`Authentication`）と `identity` には依存しない（ArchUnit で確かめる。Bolt 14 の実装で、注釈も Spring Security の型であるため引数の解決にした）。
- Spring Security の主体は `identity` の型（`CargoUserDetails`。`UserDetails` を実装する）で、session（Spring Session JDBC）に直列化できる値だけを持ち、`actor()` で `AuthenticatedActor` を作る。
- 置き換えの順は ADR-011 の決定 4 に従う（型を足す → 認証を入れる → controller を 1 つずつ置き換える → `ProvisionalActorProperties` を消す）。

### 2. 認証の構成

- `SecurityFilterChain` は `identity.infrastructure.config` に置く。`/customer/**` は `SHIPPER`、`/staff/**` は `SALES` の役割を求める。静的な資源と `/login`・`/session-expired` は認証なしで開ける。
- form login（`GET`・`POST /login`）と `DaoAuthenticationProvider` を使う。利用者の読み出し（`UserDetailsService`）は `identity` に置き、利用停止の利用者と無効な企業の利用者を、理由付きで認証の失敗にする（IA-INV-09）。画面の文言はすべて同じにする（SEC-11）。
- password は DelegatingPasswordEncoder の既定（bcrypt、`{bcrypt}` の接頭辞。SEC-07）。
- session の固定化を防ぐため、ログインで session ID を付け替える（Spring Security の既定の `ChangeSessionIdAuthenticationStrategy`）。CSRF は同期トークン（既定）。
- 認可は request ごとの今の役割で判定する（Bolt 32c で改めた。Bolt 14 から 32 まではログインの時点の役割）。認証済みの request ごとに利用者・役割・企業を DB から読み、主体を作り直して差し替える（SEC-12・IA-INV-04・AC6。下の「W5 で見直すこと」の 3）。取り消した役割の URL は A-04（権限なし）になる。
- 応答のヘッダーは `X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、CSP `default-src 'self'`。Cookie は HttpOnly・SameSite=Lax、`dev` の外で Secure。

### 3. session

- Spring Session JDBC で、`platform` スキーマの `spring_session`・`spring_session_attributes` に置く（DDL はベンダーごと）。
- 無操作 30 分は session の期限（`MAX_INACTIVE_INTERVAL`）で判定する。期限は `spring.session.timeout` で決める（`server.servlet.session.timeout` は Spring Session に効かないことを、既定と違う値のテストで確かめた）。
- 発行から 8 時間の上限は、ログインのときに session の属性（固定の名前）に置いた認証時刻と、アプリケーションの `Clock` で比べるフィルターで判定する。Bolt 31 から、8 時間の起点（`AUTHENTICATED_AT`）はその session で初めて TOTP が通った時刻にし、password の段だけの session は password を確かめた時刻（`PASSWORD_VERIFIED_AT`）から 5 分で失効させる（[ADR-011](011-mfa-totp.md) の決定 2）。フィルターは要素の数で上限を選ぶ。このフィルターは認証の処理より前に置く（Bolt 13 の Try T-40）。認証済みなのに認証時刻がない session も失効させる（fail-closed。ログインの時刻を置かない認証の経路を足しても上限が外れない）。
- 役割とホームの対応はルートのコントローラーの 1 か所に置き、ログインの後もルートを通す。画面のまだない役割は A-04 にする。
- 失効した session の request は、業務データを出さずに A-03（`/session-expired`）へ移す。Bolt 32c から、利用停止・所属企業の無効化・利用者の削除の後の次の request も、session を捨てて A-03 へ移す（AC6。止められたことは画面に示さない）。この確かめのフィルター（`SessionRevalidationFilter`）は期限のフィルター（`SessionLifetimeFilter`）の直後、認証のフィルター（`UsernamePasswordAuthenticationFilter`・`TotpAuthenticationFilter`）より前に置く。期限の切れた session は先に A-03 に送られ、DB を読まない。公開の URL（ログイン・再認証の案内・エラー・静的な資源）では確かめない（1 画面の表示で資源の数だけ読まず、止められた利用者の並行の資源の request で取消しを重ねて書かないため。Bolt 32c の開発レビュー）。ログアウトの送信は確かめるので、止められた利用者のログアウトは A-03 で終わり、監査記録は `LOGOUT` でなく `SESSION_REVOKED` になる。並行の業務の request が同時に取り消されると、`SESSION_REVOKED` が 2 行以上になることがあり、受け入れる。フィルターの順序の値は `addFilterAfter` で `UsernamePasswordAuthenticationFilter` と同じになり、登録の順で前に来る（統合テストで並びを見張る）。上限を過ぎた session のままのログインの送信も A-03 へ移し、ログインの送信で session を延命させない。session に CSRF のトークンがない古いフォームの送信も、Spring Security が無効な session として A-03 へ移す。
- Bolt 32b から、ログインした画面で失効の 5 分前に警告し、無操作の期限を画面を移らずに延長できる（ACC-06）。延長の URL は `POST /session/extend`（`identity.interfaces.web.SessionController`）で、公開の URL にせず、今のフィルター（CSRF・`SessionLifetimeFilter`・`SessionRevalidationFilter`・認証）をすべて通す。Spring Session がその request で session の最終アクセスを新しくするので、204 を返すだけにする。8 時間の上限は延ばさず、監査記録は書かない（延長はログインでも権限の変更でもない）。`CsrfFilter` は `SessionLifetimeFilter` より前に動く。無操作で失効した session では CSRF のトークンも消えているので、延長は CSRF のトークンがない拒否（`MissingCsrfTokenException`）になり、失効の扱い（Bolt 32b では `invalidSessionUrl`、Bolt 32e から `invalidSessionStrategy`。下の項目）によって A-03 への 302 になる（上の古いフォームの送信と同じ経路）。session が生きていてトークンが誤っているときだけ 403 になる。画面は 302 を失効、403 を延長の失敗として分けて示す。
- 8 時間の上限までの残りの秒数は、`SessionLifetimeFilter` が上限の判定と同じ起点と時計で算出し、2 つの要素のそろった session でだけ request の属性 `cargotracker.request.sessionLifetimeRemainingSeconds` に置く。画面の層（`SessionTimerAdvice`）は名前で読み、画面に出す（Bolt 32b）。画面の層は `infrastructure` に依存できないので、request の属性で渡し、層の規則に例外を足さない。名前は 2 つの定数で持ち、一致をテストで守る（Bolt 31c のレビュー 14 と同じ形で、Bolt 33b でまとめて直す。[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)）。
- Bolt 32e から、無操作の失効と 8 時間の上限で、開いていた業務の画面の要求を覚え、ログインし直した後にそこへ戻す（ACC-06。US の横断受入条件の「再認証後に再開できる」の画面の分。Bolt 31 のレビュー 5）。
  - 無操作の失効の扱いを `invalidSessionUrl`（A-03）から `invalidSessionStrategy` に替え、入口の部品（`identity.infrastructure.security`）を渡す。CSRF のトークンがない拒否（`MissingCsrfTokenException`）も、Spring Security の `InvalidSessionAccessDeniedHandler` を通して同じ部品に来る。8 時間の上限では、`SessionLifetimeFilter` が 2 つの要素のそろった session を捨てたときに同じ部品を使う。
  - 部品は、覚えるかに関わらず新しい session を作ってから A-03 へ移す（古い Cookie のまま A-03 を開いて 302 を繰り返さないため）。
  - 覚えるのは、GET で path が許す一覧（`/customer/**`・`/staff/**`）に当たる要求だけにする。送信（POST。延長・ログアウト・業務の送信）・公開の URL・ログインの手続き・静的な資源・`/`・書類のダウンロードは覚えない。GET の parameter は query だけなので、送信の本体は session に入らない。Referer は使わない。戻り先はサーバーが受けた request から作るので、ほかのサイトへは移らない（オープンリダイレクトにしない）。覚えるのは Spring Security の `HttpSessionRequestCache` に `RequestMatcher`（GET かつ許す一覧）を渡したもので、TOTP の成功の扱い（`SavedRequestAwareAuthenticationSuccessHandler` を継ぐ）と同じ属性の名前と `continue` の引数名を使い、TOTP が通ると `?continue` を付けて戻す。許す一覧の `RequestMatcher` は合成ルートが渡す（認証の入口から画面の層を参照しない）。
  - 持ち越し: password の段の 5 分・登録の途中の 15 分の失効と、認証コード・回復コードの誤りで session を捨てるときは、捨てる前の session に覚えた要求があれば新しい session に移す（ログインの手続きをやり直すだけなので、戻る先を失わない）。
  - 取消し（利用停止・所属企業の無効化・利用者の削除。`SessionRevalidationFilter`）では覚えず、持ち越さない（止められた利用者には復帰しない。Bolt 32e の開始準備の決定）。入力の控え（Bolt 32f）は、ブラウザでは取消しと失効を見分けられないので、取消しでもそのタブに残るが、戻るのは同じ利用者がログインし直せたときだけである（Bolt 32f の開始準備で「止められた利用者には入力の保持もしない」を変えた。下の Bolt 32f の項目）。
  - 取消しで覚えないのは、止めた request の分である。A-03 の後に新しい匿名の session で業務の画面を開けば、通常の未認証の入口（`ExceptionTranslationFilter`）がその画面を覚える（利用者が自分で開いた画面なので受け入れる）。覚えた要求（`DefaultSavedRequest`）には URL のほかに要求のヘッダーと Cookie も入り、Spring Session の表に置かれる（失効した session の ID の Cookie を含む。署名付きの Cookie や認証のヘッダーを足すときは見直す）。
  - 覚えた要求は利用者に結び付けない。無操作の失効では、失効した session から利用者が分からない。8 時間の上限だけ結び付けると、2 つの失効で振る舞いが分かれる。別の利用者が同じブラウザでログインすると前の URL（業務番号を含みうる）へ移るが、役割が違えば認可で拒まれて A-04、同じ役割の他社の業務番号なら所有の確かめで見つからない扱い（BR-07 の 404）になり、データは見えない（URL の path は見える）。共用の端末ではログアウトしてから離れる運用で補う（マニュアル）。
  - 覚えた要求の戻り先の URL は、request の scheme と host から作る。ロードバランサーの後ろでは forward headers（`X-Forwarded-Proto` など）の扱いに頼る。デモ（Heroku）は Spring Boot が環境を検出して有効にする（Bolt 15 で確かめた）。W10 の ALB では、戻り先の URL が https のままであることを R-13（forward headers のテスト）とあわせて確かめる。
  - 送信の後の失効で送信した画面へ戻すことと、送信していない入力の保持は Bolt 32f（下の項目。サーバーの session には置かない）。
- Bolt 32f から、失効の時に送信していない入力を、ブラウザのそのタブに「入力の控え」として写し、同じ利用者が同じ画面に戻ったら戻す（ACC-06。US の横断受入条件の「未保存の入力は失われず再認証後に再開できる」の入力の分）。業務状態をクライアントに持たない方針（ADR-005）の例外で、控えは送信の前の入力の写しであり業務状態の正ではない。仕組みは [フロントエンドのアーキテクチャ](../../design/cargo-tracker/architecture_frontend.md) の「入力の控え（Bolt 32f）」、文言は [UI 設計](../../design/cargo-tracker/ui_design.md) に置く。
  - 置き場所: sessionStorage（キー `cargotracker.session.savedInput`、1 件）。タブごとで、タブを閉じれば消え、ほかのタブにも出ない。`localStorage`（タブの間の同期の時刻）には置かない。サーバー（session・表）には置かない（送信していない業務の入力をサーバーに残さない。失効した session からは利用者が分からない）。password・hidden（CSRF のトークンを含む）・ファイルは写さない。
  - 写す時: 警告の期限の到来、「画面を開き直す」を押したとき、業務の画面の POST のフォームを送信する前。サーバーが戻り先を渡した画面（GET で開いた業務の画面。Bolt 32e）でだけ写し、戻り先は 32e と同じ鍵（`continue` を除いて正規化する）にする。
  - 利用者の ID を画面に出す: 戻す前に、控えを写した利用者と今の利用者が同じかをブラウザで確かめるため、ログインした画面の警告の部分に `data-user` として、`AuthenticatedActor` の `userId()` の一方向の値（SHA-256 の先頭 16 文字、URL で使える Base64）を出す。内部の識別子（UUID）そのものは画面の HTML に出さない（既存の画面の確かめを守る。Bolt 32f の実装で見つけた）。同じ利用者かどうかだけが分かり、認証にも認可にも使わない。メールアドレスや表示名は使わない（個人情報で、変わりうる）。この値は控えにも入る。
  - 捨てる時: 戻したとき、送信が届いたとき、利用者が「戻した入力を消す」・案内の「消す」を押したとき、ログインした画面の利用者が控えの利用者と違うとき、写してから 8 時間を過ぎたとき（ログインした画面を開いたときに確かめる。8 時間の上限（SEC-03）と同じ長さで、それより古い入力は前のログインのものである）、ヘッダーのログアウトの送信、期限の到来が取り消されたとき（期限の到来で写した控え）。
  - 送信が届いたかの判定: サーバーに問い合わせない。送信の次に読み込んだ画面が公開の画面（A-03・A-01・403・413 など）なら、そのスクリプトが控えに拒まれた印を付ける（CSRF のトークンが合わない 403 で拒まれた送信も入力を失わない）。ログインした画面で印のない送信の控えは、送信が届いたとみなして捨てる。拒まれた送信の控えは、ログインし直した後の画面に案内と「画面を開く」を出す（戻り先はブラウザが送信の前に写した、サーバーが渡した戻り先で、同じ origin の path と query に限る。サーバーには渡さない）。
  - 既知の課題（8 時間のぎわの二重の送信のおそれ）: 8 時間のぎわで、送信が届いた直後の移動（PRG の GET）が上限を過ぎて A-03 になると、届いた送信の控えに拒まれた印が付き、ログインし直した後に入力が戻りうる。知らせは「まだ送信していません」と示すので、利用者がもう一度送信すると二重になりうる。控えは hidden（`commandId`・版）を写さず、戻した画面は新しい `commandId` を持つので、`commandId` では二重の反映を防げない（期待版を持つ変更は版の不一致で止まるが、新規の作成は別の作成になりうる）。まれなので受け入れ、マニュアルで一覧を確かめるよう補う。
  - 取消しの決定の変更: Bolt 32e の開始準備では「止められた利用者には入力の保持もしない」と決めたが、ブラウザでは取消しの A-03 と失効の A-03 を見分けられない（止めたことは画面に示さない）。Bolt 32f の開始準備で、「控えはそのタブに残るが、戻るのは同じ利用者がログインし直せたときだけ」に変えた。止められている間はログインできず、利用を戻されたら自分の入力が戻る。別の利用者には戻らない（利用者が違えば捨てる）。A-03 の一文も取消しの利用者に同じに出す。
  - 共用のパソコンと閉じたタブの復元: タブを閉じれば消えるが、閉じたタブを開き直したとき・ブラウザの復元・タブの複製では sessionStorage が残ることがある。ログアウトの送信で捨てるので、共用のパソコンではログアウトしてから離れる運用で補う（マニュアル 2.1。覚えた要求と同じ）。ログアウトせずに離れると、次の人が同じタブで A-03 を開いたときに控えがあることの一文が見える（中身は見えない。別の利用者がログインすると捨てる）。
  - 個人情報と XSS: 控えには荷受人の連絡先などの個人情報が入りうるので、そのタブに最長 8 時間残りうる（PRV-01）。XSS があれば同じ origin のスクリプトに読まれる（SEC-17）。CSP（`default-src 'self'`）でインラインと外部のスクリプトを許さないことと、戻すときに `.value`・`.checked` で入れる（HTML として解釈しない）ことで抑える。sessionStorage を使えないときは何もしない。

### 4. 監査

- ログインの成功・失敗・ログアウトを、Spring Security のイベントの listener から、同じ request の中で `identity.audit_record`（追記だけ）に書く。失敗の理由（`BAD_CREDENTIALS`・`UNKNOWN_USER`・`SUSPENDED`・`COMPANY_INACTIVE`）は記録にだけ残す。
- 存在しないメールアドレスでの失敗は、操作者を空にして記録し、メールアドレスは残さない。
- 監査記録の書き込みに失敗したら、ログインも失敗にする（IA-INV-08 の同期の書き込み。fail-closed）。
- 権限外のアクセスの試行の記録は、利用者と役割の管理（US-16）の Bolt で足す。
- W5 のロックの失敗の数え方は、この listener が失敗・成功のイベントをアプリケーションのサービス `LoginAttemptService` に写す形にする。数えるのと、その監査記録を書くのはサービスの 1 つのトランザクションで、listener は数えない。数えない拒否と password の段の成功は listener が監査記録に直接書く（Bolt 32。下の「W5 で見直すこと」の 5。[ADR-011](011-mfa-totp.md) の決定 9）。Bolt 32 から、ロックしたときに `ACCOUNT_LOCKED` を書き、ロック中の試行を理由 `LOCKED`、利用者の読み出し（`loadUserByUsername`）の DB の誤りを理由 `SERVICE_ERROR` で記録する（どちらも数えない）。
- Bolt 31 から、操作を `PASSWORD_VERIFIED`（password の段の成功）と `LOGIN_SUCCEEDED`（2 つの要素がそろった）に分ける。TOTP の失敗は利用者と理由（`INVALID_TOTP`・`TOTP_REUSED`）で記録する（下の「W5 で見直すこと」の 1）。Bolt 31b から TOTP の登録を `TOTP_ENROLLED` で、Bolt 31c から回復コードの使用を `RECOVERY_CODE_USED`（`LOGIN_SUCCEEDED` と 2 行）で記録し、回復コードの拒否は理由 `INVALID_RECOVERY_CODE` で記録する（[ADR-011](011-mfa-totp.md) の決定 7）。Bolt 31d から、TOTP を登録していない利用者の TOTP の送信を理由 `TOTP_NOT_ENROLLED` で記録する（今までは `INVALID_TOTP`。利用者への応答は変えない。ADR-011 の決定 8）。Bolt 32c から、request ごとの確かめで session を捨てたときに `SESSION_REVOKED`（結果 `FAILURE`、理由 `SUSPENDED`・`COMPANY_INACTIVE`・`UNKNOWN_USER`、操作者は session の主体の利用者 ID と企業 ID）を書く。書くのはアプリケーションのサービス `SessionRevalidationService` で、監査を直接書く入口の経路は増やさない。役割が変わっただけのときは書かない。

### 6. 層の規則 D-5 の認証の例外（Bolt 31）

- 認証の入口のパッケージ（`identity.infrastructure.security`。Spring Security の provider・listener・成功と失敗のハンドラー・フィルター・拒否の例外）から、`identity.application.internal.commandservices` だけを参照してよい。例外の範囲は入口だけである（Bolt 31d で決めた）。暗号の部品（`AesGcmSecretCipher`・`Base32`・`BcryptRecoveryCodeHasher`・`JavaOtpTotpCodeGenerator`・`RandomRecoveryCodeGenerator`・`RandomTotpSecretIssuer`）は Bolt 31c まで同じパッケージにあり、例外がパッケージ全体にかかっていた（Bolt 31 の開発レビューで挙がり、Bolt 31b では秘密の発行と回復コードの生成の実装を名指しして application に依存しない規則で守りを補った）。Bolt 31d で暗号の部品を `identity.infrastructure.crypto` に分け、既存の規則（合成ルートと入口の外の infrastructure は application に依存しない）が crypto にかかるようにし、名指しの規則をパッケージの規則に置き換えた。足した規則は「`crypto` は `security` に依存しない」だけである。保存した要素を読めないときは crypto が `UnreadableCredentialException`（ドメインの例外）を投げ、入口が捕まえる（[ADR-011](011-mfa-totp.md) の決定 8）。Bolt 31c の回復コードの provider（`RecoveryCodeAuthenticationProvider`）も入口で、`RecoveryCodeVerificationService`（`commandservices`）を呼ぶので、この例外の範囲に収まる。合成ルートの外の `infrastructure` は `application` に依存しない規則（D-5）の狭い例外で、サガの例外（`infrastructure.persistence` から `application.sagas`）と同じ書き方で `LayerArchitectureTest` に足し、理由を `because` に書く。例外の外は落ちることを、違反を入れて確かめる。
- 理由: provider と listener は Spring Security が呼ぶ入口で、`interfaces` の層に置けない。業務の判断（TOTP の照合の結果を写し、刻みを使う。照合そのものは Bolt 31d から TOTP の資格）はアプリケーションのサービス（`TotpVerificationService`）に置き、入口は結果を認証の成否に直すだけにする。
- Bolt 32 のロックの数え方（下の「W5 で見直すこと」の 5）も同じ形にする（2026-10-13、Bolt 31 の整合性検証の指摘への回答で human:kakimomokuri が決定。[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)）。入口の監査のリスナー（`AuthenticationAuditListener`）・照合の後の確認の部品（`LockAwareUserDetailsChecker`）・TOTP と回復コードの provider は、`LoginAttemptService`（`commandservices`）を呼ぶので、この例外の範囲に収まる（Bolt 32）。
- request ごとの確かめ（下の「W5 で見直すこと」の 3）も同じ形にする。入口のフィルター `SessionRevalidationFilter` は `SessionRevalidationService`（`commandservices`）を呼び、結果を session と主体に写すだけにする。判定（`User#authenticationRejection`）と `SESSION_REVOKED` の書き込みはサービスに置く。利用者と企業はドメインのポート `UserStandingRepository`（`identity.domain.model.aggregates`）で 1 回で読み、実装は `identity.infrastructure.persistence` に置く。この例外の範囲に収まり、新しい例外は要らない（Bolt 32c の計画の承認）。
- ロックの判定 `LoginAttemptService#isLocked` は問い合わせだが `commandservices` に置く。認証の入口から呼ぶため、入口から呼べるのは `commandservices` だけという例外の範囲に収める（例外を `queryservices` に広げない）。登録済みかの問い合わせ `isEnrolled` を `queryservices` に移した Bolt 31b の前例は、入口から呼ばず画面の層だけが呼ぶ問い合わせだったためで、矛盾しない（Bolt 32 の計画の承認）。

### 5. 開発環境の入力済み

- ADR-011 の決定 3 の 1〜4 層目をこの Bolt で入れる。`@Profile("dev")` の部品が `cargotracker.dev-login.email`・`password` を A-01 に入れる。`dev` の外、または `staging`・`prod` と一緒にこの設定があれば起動を失敗させる。開発用の企業と利用者は `db/dev-data/`（`dev` のときだけの Flyway の場所）に置き、password は固定の開発用の値にする。
- 5 層目（ステージング・本番で `dev` を拒む）は W10 の IaC で入れる。

### 代替案

| 案 | 採らない理由 |
| :--- | :--- |
| 主体の型を `identity` に置き、`quotation` から参照する | `quotation → identity → quotation :: events` が循環する |
| controller が `Authentication` を受け取り、利用者 ID を取り出す | 業務の層の外側とはいえ、認証の方式が `quotation` に入る。TOTP の段（要素の権限）を足すときに `quotation` の変更が要る |
| session を Servlet のコンテナのメモリに置く | 複数のコンテナ（W10 の ECS）で session を共有できない。利用停止の即時の失効を DB の削除でできない |
| 送信（POST）の後の失効では、Referer の画面を覚えて戻す（Bolt 32e） | Referer は利用者の側で変えられ、オープンリダイレクトの入口になる。送信の後の画面は送信の URL のまま描き直しており、開き直すと別の画面か 405 になる。送信の後の失効は Bolt 32f で、ブラウザが送信の前に戻り先を持つ |
| 入力の控えをサーバーの session や表に置く（Bolt 32f） | 送信していない業務の入力をサーバーに残すことになる。無操作の失効では失効した session から利用者が分からず、戻す相手を確かめられない。ADR-005 の例外がブラウザより広くなる |
| 入力の控えを `localStorage` に置く（Bolt 32f） | タブとブラウザの終了をまたいで残り、ほかのタブにも出る。共用のパソコンに個人情報が長く残る |
| 覚えた要求を利用者に結び付ける（Bolt 32e） | 無操作の失効では、失効した session から利用者が分からない。8 時間の上限だけ結び付けると振る舞いが分かれる。別の利用者は認可で拒まれ、データは見えない |
| 認可を request ごとに DB で確かめるのも今入れる | 利用者を止める操作（US-16）がなく、効き目を確かめられない。AC6 と一緒に W5 で入れる（Bolt 32c で入れた。DB の値を変えて確かめる） |

## 影響

### ポジティブ

- `quotation` の controller の変更は引数の型の置き換えで済み、TOTP の段を足しても `quotation` は変わらない。
- 仮の主体がなくなり、提出者・承認者などに本当の利用者が記録される。

### ネガティブ

- session は JDK の直列化で DB に置く。`Role` の定数の改名・削除、`CargoUserDetails` の移動や項目の変更、Spring Security の版の更新、W5 の要素の権限の追加で、配備の前の session が復元できなくなる。互換を壊す変更を配備するときは、配備の手順で `platform.spring_session` の行を消し、利用者にログインし直してもらう（W10 の運用手順書に書く）。復元の失敗を無効な session として扱う変換は、W5 の要素の権限を足すときに検討する（Bolt 31 では、要素の権限を足しても配備の前の session は復元でき、password の段の時刻がないので期限の判定で A-03 に送る形になった。復元そのものが失敗する変更の変換は入れていない。ADR-011 の決定 2）。Bolt 32 のロックでは、互換を保つため `CargoUserDetails` に項目を足さず、ロックの状態を session に写さない（ADR-011 の決定 9）。
- 未認証の request（ログインの画面の CSRF のトークン、失敗の後のメールアドレス）でも session の行ができる。作られる速さの上限（レート制限）は、W10 の WAF の送信元ごとの流量の規則で扱う（利用者ごとのロックでは抑えられない。Bolt 32 の開始準備で S-02 とあわせて決めた。SEC-20、リスク台帳）。

- Bolt 32c まで、利用停止しても既存の session は最長 8 時間使えた（パイロットの前で本番の利用者はいない）。Bolt 32c で request ごとに確かめるようにして解消した。その代わりに、認証済みの request ごとに 1 回の SQL が増える。キャッシュは入れない（入れると AC6 の「次の request から」が崩れる）。W10 の性能の評価で計測する（PERF-01・02）。
- 既存のテスト（controller の単体テスト、画面の層のシナリオ）にログインの準備が要る。
- Bolt 32e から、失効の後に同じブラウザで別の利用者がログインすると、前の利用者が開いていた画面の URL へ移る（役割の違いは A-04、他社の業務番号は見つからない扱い。URL の path は見える）。覚えた要求を利用者に結び付けないため。共用の端末はログアウトしてから離れる運用で補う。
- ステージング・本番の利用者を作る手段は、利用者の管理（US-16）まで無い。
- Bolt 32f から、送信していない入力（個人情報を含みうる）がブラウザのそのタブに最長 8 時間残りうる。XSS があれば読まれる。閉じたタブの復元で残ることがある。8 時間のぎわで届いた送信の入力が戻り、二重に送信されうる（上の決定 3 の Bolt 32f の項目の既知の課題）。

## W5 で見直すこと

番号は Bolt 31 の計画で振った。1・2 は Bolt 31、4 は Bolt 31c、5 は Bolt 32、3 は Bolt 32c で片づける（2026-10-13、Bolt 31 の計画の承認。4 は Bolt 31b の開始準備で 31b と 31c に分けたときに 31c に送り、Bolt 31c の計画の承認で決めた。3 は Bolt 32 の開始準備で 32 と 32c に分けたときに 32c に送った）。

1. `@EnableMultiFactorAuthentication` を入れると、password の段でも認証の成功のイベントが出る。監査の操作を「password を確かめた」と「ログインした」に分ける（Bolt 31）。
2. 認可の条件（`hasRole`）に要素の権限を加える（Bolt 31）。全体の注釈で業務の URL に 2 つの要素を求めるので、URL ごとの規則は役割だけのままにし、要素の欠けた主体で業務の URL を開けないことを全 URL で確かめる。
3. request ごとの DB の確かめ（SEC-12・AC6）は、利用者 ID から主体を作り直して差し替える形にし、問い合わせを 1 回にまとめる（決めた。2026-10-14、Bolt 32c の計画の承認で human:kakimomokuri が決定。Bolt 32 の開始準備で 32 から送った）。
   - 入口のフィルター `SessionRevalidationFilter`（`identity.infrastructure.security`）を `SessionLifetimeFilter` の直後に置く。認証済みの主体（`CargoUserDetails`）を持つ request だけで、`SessionRevalidationService#revalidate(UserId, CompanyId)` を呼ぶ。password の段だけの主体（A-02・A-07 の途中）にもかけ、止められた利用者に TOTP・回復コードの段を続けさせない。
   - 読み出しはドメインのポート `UserStandingRepository`（利用者と所属企業の組 `UserStanding` を返す）で 1 回。実装は `identity.app_user` と `identity.company` を内部結合し `identity.user_role` を外部結合する 1 つの SQL で、役割の数だけ出る行を 1 人の `User` にたたむ。
   - サービスは今のドメインの規則（`User#authenticationRejection(company)`）で判定する。利用者が見つからなければ `UNKNOWN_USER`。拒否なら `SESSION_REVOKED` を 1 つのトランザクションで書く。フィルターは session を捨て、セキュリティのコンテキストを消して A-03 に送る。
   - 拒否でなければ、フィルターが今の役割・表示名・企業名で `CargoUserDetails` を作り直し、今の `Authentication` を `toBuilder()` で主体と `ROLE_` の権限だけ替える。要素の権限（`FACTOR_PASSWORD`・`FACTOR_TOTP`）は残す。新しいセキュリティのコンテキストに入れ、session には保存しない（毎回作り直すので古い役割は戻らない）。A-02・A-07 が通ったときは、認証のフィルターが差し替えた後の主体で session に保存する。この振る舞いは特性テストで固定した（Bolt 32 の Try T-114）。
   - 誤りは fail-closed にする。読み出しの DB の誤り、監査の書き込みの誤り、役割が 0 件の利用者（データの不整合）は、session を捨ててから 500 にする。
   - 採らなかった案: フィルターが `infrastructure.persistence` の具象を直接呼ぶ（判定と監査が入口に入る）、`UserRepository` に企業を返すメソッドを足す（集約のリポジトリが別の集約を返す）、`queryservices` に置く（D-5 の例外を広げる。監査を書くのでコマンドでもある）、今の 2 つのリポジトリで 3 回読む（「1 回」に反する）。
4. 引数の解決で、TOTP まで済んでいない主体を渡さない（決めた。2026-10-13、Bolt 31c の計画の承認で human:kakimomokuri が決定）。
   - password の段だけで開く画面（A-02・A-07）が受ける主体は、`AuthenticatedActor`（2 つの要素がそろった主体だけ）と分けた identity の中だけの型 `PasswordVerifiedUser` にする。`identity.domain.model.valueobjects` に置く `@ValueObject` の record で、項目は利用者 ID（`UserId`）・企業 ID（`CompanyId`）・メールアドレス（`EmailAddress`）。置き場所は Bolt 31b の開始準備の骨組みの確かめ（T-101）による（`identity.interfaces.web` に置くと infrastructure の引数の解決からの参照が層の規則に当たり、`valueobjects` に置くと通った）。
   - `PasswordVerifiedUserArgumentResolver`（`identity.infrastructure.security`）が password の要素がある主体から作って渡す。未認証なら例外。A-02・A-07 の controller（`LoginController`・`TotpEnrollmentController`）はこれを受け、`Principal` の引数を外す（メールアドレスは dev の A-02 の入力済みと登録の URI のアカウント名に使う）。`PasswordVerifiedUser` を受ける controller はこの 2 つだけにする（ArchUnit。空振りを防ぐ見張りを並べる）。
   - `AuthenticatedActorArgumentResolver` は `FACTOR_TOTP` がない主体に `AuthenticatedActor` を渡さず例外にし、`Optional<AuthenticatedActor>` は空にする。本番では全体の注釈で要素の欠けた主体は業務の URL に届かないので、届いたら実装の誤りである。ほかのコンテキストの controller の本体は変わらない。
   - 画面の単体テストの主体（`@WithAuthenticatedActor`）の工場（`WithAuthenticatedActorSecurityContextFactory`）が、`FactorGrantedAuthority` で `FACTOR_PASSWORD`・`FACTOR_TOTP` を足す（使うテストのファイルは変えない）。
5. ロックの数え方（IA-INV-03）は `identity.application` のサービスとログインの失敗の集約に置き、監査の listener は入口だけにする（決めた。2026-10-13、Bolt 32 の計画の承認で human:kakimomokuri が決定）。
   - 規則（5 回・15 分、ロック中は数えない・延ばさない）はログインの失敗の集約 `LoginFailure` と方針 `LockPolicy` のまま（2026-10-11 に改めた。初めは利用者の集約としたが、失敗のたびに利用者の版が上がり利用者の管理の更新とぶつかるため、Bolt 30 の計画の承認で別の集約にした）。
   - 数える・0 に戻す・監査記録を書くのは `LoginAttemptService`（`identity.application.internal.commandservices`）で、回数と監査記録を 1 つのトランザクションにする（IA-INV-08）。listener は誤りの失敗・ロック中の試行・2 つの要素がそろった成功をイベントの型でサービスのメソッドに写し、数えない拒否と password の段の成功は監査記録に直接書く（Bolt 32 の開発レビューで実装に合わせた）。数え方の入口は決定 6 の D-5 の認証の例外と同じ形で、`isLocked` も `commandservices` に置く（決定 6）。
   - 最初の失敗の行は主たるトランザクションの前にトランザクションの外の 1 文で作り、一意制約の違反はトランザクションの外で受ける。詳細は [ADR-011](011-mfa-totp.md) の決定 9。

## コンプライアンス

- ArchUnit: `identity` の外は `org.springframework.security` に依存しない。Spring Modulith の検証で、`quotation` は `identity` に依存しない。
- セキュリティの統合テスト: 未認証の拒否、AC2・AC4 の同じ応答、session ID の変化、30 分・8 時間の境界の 3 点、8 時間の確認が認証より前、CSRF、役割の分離、監査記録の同期の書き込み、応答のヘッダー。
- 既定の設定で A-01 が入力済みにならないこと、`dev` の外の `cargotracker.dev-login.*` で起動が失敗すること。
- セキュリティの統合テスト（Bolt 32c）: 利用停止・無効な企業・利用者の削除の後の次の request が A-03 で session が捨てられ `SESSION_REVOKED` が残る、password の段だけの主体も同じ、役割の取消し・追加が次の request から効く（取り消した URL は A-04）、要素の権限と 8 時間の起点が保たれる、認証済みの 1 つの request の読み出しが 1 回で未認証・期限切れでは読まない、DB の誤りと役割 0 件は 500。
- セキュリティの統合テスト（Bolt 32e）: 無操作・8 時間で失効した session で業務の画面を開くと A-03 へ移り、ログインし直すとその画面へ `?continue` 付きで戻る（覚えた要求に query 以外の parameter がない）、古い Cookie のまま A-03 を開いても 200、送信・公開の URL・許す一覧の外では覚えない（覚える場合と対で確かめる）、認証コードの誤りと password の段の失効をはさんでも戻る、取消しの後はホームに着く、別の利用者がログインすると認可で拒まれる（受け入れた振る舞いの特性テスト）。
- セキュリティの統合テスト（Bolt 32b）: 延長の 204 と session の最終アクセスの更新、CSRF のトークンなし・誤りの 403、無操作で失効した session の延長は A-03、延長は 8 時間の上限を延ばさない、8 時間の残りは 2 つの要素のそろった session でだけ request の属性に置かれる（password の段では置かれない）。
- セキュリティの統合テスト（Bolt 32f）: ログインした画面に `data-user`（利用者の ID の一方向の値。UUID は出ない）と知らせの領域が出て、公開の画面（A-03・403）に入力の控えのスクリプトが出る。控えの写す・戻す・捨てる（別の利用者、8 時間と 7 時間 59 分、送信が届いた、ログアウト、期限の到来の取り消し）と、password・CSRF のトークン・ファイルを写さないことは、画面の層の受入シナリオ（`session_timeout_ui.feature`）で確かめる。
- ArchUnit（`LayerArchitectureTest` の D-5）: 認証の例外は `identity.infrastructure.security` から `identity.application.internal.commandservices` に限り、例外の外は落ちる（Bolt 31）。例外は入口だけにかかり、暗号の部品（`identity.infrastructure.crypto`）は application にも `security` にも依存しない（Bolt 31d。crypto に 6 つの暗号の部品があることと、既存の規則が crypto のクラスにかかることを見張りで確かめる）。

## 改訂の経緯

- 2026-10-14: Bolt 32f の計画の承認で、決定 3 に失効の時の入力の控え（置き場所は sessionStorage、利用者の ID を画面に出す理由、写す時と捨てる時、送信が届いたかの判定、8 時間のぎわの二重の送信のおそれ、共用のパソコンと閉じたタブの復元、個人情報と XSS）を書いた。取消しの決定を「止められた利用者には入力の保持もしない」から「控えはそのタブに残るが、戻るのは同じ利用者がログインし直せたときだけ」に変えた。代替案にサーバーと `localStorage` に置く案、ネガティブに控えが残ること、コンプライアンスに統合テストを足した。ADR-005 の例外にした（human:kakimomokuri が決定）。
- 2026-10-14: Bolt 32e の開発レビューで、別の利用者が前の URL へ移ったときの結果を「役割の違いは A-04、他社の業務番号は見つからない扱い」に正した。取消しで覚えないのは止めた request の分で、A-03 の後に利用者が開いた画面は通常の未認証の入口が覚えること、覚えた要求には URL のほかに要求のヘッダーと Cookie も入ることを書いた（human:kakimomokuri が承認した計画の範囲の訂正）。
- 2026-10-14: Bolt 32e の計画の承認で、決定 3 に失効の扱いで要求を覚えること（`invalidSessionUrl` から `invalidSessionStrategy` へ、覚えるかに関わらず新しい session を作る、覚えるのは GET で許す一覧の画面だけ、password の段・登録の途中の失効と認証コードの誤りでは持ち越す、取消しでは覚えない、利用者に結び付けない、forward headers に頼る）を書き、Bolt 32b の延長の経路の `invalidSessionUrl` の記述を改めた。代替案に Referer と利用者への結び付け、ネガティブに別の利用者が前の URL へ移ること、コンプライアンスに統合テストを足した（human:kakimomokuri が決定）。
- 2026-10-14: Bolt 32b の計画の承認で、決定 3 に延長の URL（`POST /session/extend`。今のフィルターを通し、8 時間の上限を延ばさず、監査記録を書かない）、CSRF と失効の経路（無操作で失効した session の延長は CSRF のトークンがない拒否で A-03、session が生きていてトークンが誤りなら 403）、8 時間の残りを認証の入口から request の属性で画面の層に渡すことを書き、コンプライアンスに統合テストを足した（human:kakimomokuri が決定）。
- 2026-10-14: Bolt 32c の計画の承認で、「W5 で見直すこと」の 3 を決めた（`SessionRevalidationFilter` の位置、`UserStandingRepository` の 1 回の読み出し、判定と `SESSION_REVOKED` は `SessionRevalidationService`、差し替えは request の中だけ、fail-closed）。決定 2 の「認可はログインの時点の役割」を改め、決定 3 に止められた利用者を A-03 に送ること、決定 4 に `SESSION_REVOKED`、決定 6 にこの形が認証の例外の範囲に収まることを書いた。ネガティブの「最長 8 時間使える」を解消した（human:kakimomokuri が決定）。
- 2026-10-13: Bolt 32 の計画の承認で、「W5 で見直すこと」の 5 を決めた（`LoginAttemptService` の 1 つのトランザクション、listener は写すだけ、`isLocked` を `commandservices` に置く理由を決定 6 に）。3 を Bolt 32c に送り、決定 4 の listener の数え方と監査の操作・理由（`ACCOUNT_LOCKED`・`LOCKED`・`SERVICE_ERROR`）、ネガティブの `CargoUserDetails` の項目（足さない）とレート制限（W10 の WAF）を改めた（human:kakimomokuri が決定）。
- 2026-10-13: Bolt 31d の計画の承認で、D-5 の認証の例外を入口（`identity.infrastructure.security`）だけにし、暗号の部品を `identity.infrastructure.crypto` に分けたことを決定 6 とコンプライアンスに書いた。監査の理由に `TOTP_NOT_ENROLLED` を足した（human:kakimomokuri が決定）。
- 2026-10-13: Bolt 31c の計画の承認で、「W5 で見直すこと」の 4 を決めた（`PasswordVerifiedUser` の置き場所と項目、2 つの引数の解決の振る舞い、受ける controller の規則、`@WithAuthenticatedActor` の要素の権限）。監査の操作と理由に `TOTP_ENROLLED`・`RECOVERY_CODE_USED`・`INVALID_RECOVERY_CODE` を足し、入口と暗号の部品のパッケージを Bolt 31d で諮ることに改めた（31c と 31d に分けたため。human:kakimomokuri が決定）。
- 2026-10-13: Bolt 31b の計画の承認で、「W5 で見直すこと」の 4 を Bolt 31c に送り、password の段の主体の型を `identity.domain.model.valueobjects` に置く見込み（Bolt 31c で決める）と、入口と暗号の部品のパッケージを Bolt 31c で諮ることを書いた（31b と 31c に分けたため。human:kakimomokuri が決定）。
- 2026-10-13: Bolt 31 の計画の承認で、「W5 で見直すこと」に番号と片づける Bolt（1・2 は Bolt 31、4 は Bolt 31b（のちに 31c）、3・5 は Bolt 32）を書き、層の規則 D-5 の認証の例外（決定 6）を足した。8 時間の起点を TOTP が初めて通った時刻に改め、password の段だけの session の上限（5 分）と監査の操作の分け方を足した（human:kakimomokuri が決定）。
- 2026-10-11: ロックの数え方の置き場所を、利用者の集約からログインの失敗の集約に改めた（Bolt 30 の計画の承認）。

## 参考資料

- [ADR-011 多要素認証（TOTP）](011-mfa-totp.md)
- [Bolt 14 計画](../../development/cargo-tracker/bolt_14_plan.md)
- [Bolt 13 開発成果物レビュー](../../review/cargo-tracker/bolt_13_review_20261006.md)
- [データモデル](../../design/cargo-tracker/data_model.md)、[ドメインモデル](../../design/cargo-tracker/domain_model.md)、[非機能要件](../../design/cargo-tracker/non_functional.md)
