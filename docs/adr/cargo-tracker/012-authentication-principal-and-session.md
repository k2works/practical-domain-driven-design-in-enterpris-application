---
type: ADR
title: "ADR-012: 認証の主体は共有カーネルの型にし、password の段を Spring Security の form login と Spring Session JDBC で作る"
description: "US-18 の password によるログインと session を、Spring Security 7 の form login と Spring Session JDBC で作る。"
tags: [adr, authentication, session]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-14T02:18:41Z }
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
- ステージング・本番の利用者を作る手段は、利用者の管理（US-16）まで無い。

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
- ArchUnit（`LayerArchitectureTest` の D-5）: 認証の例外は `identity.infrastructure.security` から `identity.application.internal.commandservices` に限り、例外の外は落ちる（Bolt 31）。例外は入口だけにかかり、暗号の部品（`identity.infrastructure.crypto`）は application にも `security` にも依存しない（Bolt 31d。crypto に 6 つの暗号の部品があることと、既存の規則が crypto のクラスにかかることを見張りで確かめる）。

## 改訂の経緯

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
