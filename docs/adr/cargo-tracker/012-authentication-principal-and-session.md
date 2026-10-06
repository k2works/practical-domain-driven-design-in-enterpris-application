---
type: ADR
title: "ADR-012: 認証の主体は共有カーネルの型にし、password の段を Spring Security の form login と Spring Session JDBC で作る"
description: "US-18 の password によるログインと session を、Spring Security 7 の form login と Spring Session JDBC で作る。"
tags: [adr, authentication, session]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-06T06:51:29Z }
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
- 認可はログインの時点の役割で判定する。request ごとに DB の現在値を確かめる（SEC-12・IA-INV-04）のは、権限の取消し（AC6）とあわせて W5 で入れる。
- 応答のヘッダーは `X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、CSP `default-src 'self'`。Cookie は HttpOnly・SameSite=Lax、`dev` の外で Secure。

### 3. session

- Spring Session JDBC で、`platform` スキーマの `spring_session`・`spring_session_attributes` に置く（DDL はベンダーごと）。
- 無操作 30 分は session の期限（`MAX_INACTIVE_INTERVAL`）で判定する。期限は `spring.session.timeout` で決める（`server.servlet.session.timeout` は Spring Session に効かないことを、既定と違う値のテストで確かめた）。
- 発行から 8 時間の上限は、ログインのときに session の属性（固定の名前）に置いた認証時刻と、アプリケーションの `Clock` で比べるフィルターで判定する。このフィルターは認証の処理より前に置く（Bolt 13 の Try T-40）。認証済みなのに認証時刻がない session も失効させる（fail-closed。ログインの時刻を置かない認証の経路を足しても上限が外れない）。
- 役割とホームの対応はルートのコントローラーの 1 か所に置き、ログインの後もルートを通す。画面のまだない役割は A-04 にする。
- 失効した session の request は、業務データを出さずに A-03（`/session-expired`）へ移す。上限を過ぎた session のままのログインの送信も A-03 へ移し、ログインの送信で session を延命させない。session に CSRF のトークンがない古いフォームの送信も、Spring Security が無効な session として A-03 へ移す。

### 4. 監査

- ログインの成功・失敗・ログアウトを、Spring Security のイベントの listener から、同じ request の中で `identity.audit_record`（追記だけ）に書く。失敗の理由（`BAD_CREDENTIALS`・`UNKNOWN_USER`・`SUSPENDED`・`COMPANY_INACTIVE`）は記録にだけ残す。
- 存在しないメールアドレスでの失敗は、操作者を空にして記録し、メールアドレスは残さない。
- 監査記録の書き込みに失敗したら、ログインも失敗にする（IA-INV-08 の同期の書き込み。fail-closed）。
- 権限外のアクセスの試行の記録は、利用者と役割の管理（US-16）の Bolt で足す。
- W5 のロックの失敗の数え方も、この listener に足す（ADR-011 の決定 2 の「失敗を一か所で数える」）。

### 5. 開発環境の入力済み

- ADR-011 の決定 3 の 1〜4 層目をこの Bolt で入れる。`@Profile("dev")` の部品が `cargotracker.dev-login.email`・`password` を A-01 に入れる。`dev` の外、または `staging`・`prod` と一緒にこの設定があれば起動を失敗させる。開発用の企業と利用者は `db/dev-data/`（`dev` のときだけの Flyway の場所）に置き、password は固定の開発用の値にする。
- 5 層目（ステージング・本番で `dev` を拒む）は W10 の IaC で入れる。

### 代替案

| 案 | 採らない理由 |
| :--- | :--- |
| 主体の型を `identity` に置き、`quotation` から参照する | `quotation → identity → quotation :: events` が循環する |
| controller が `Authentication` を受け取り、利用者 ID を取り出す | 業務の層の外側とはいえ、認証の方式が `quotation` に入る。TOTP の段（要素の権限）を足すときに `quotation` の変更が要る |
| session を Servlet のコンテナのメモリに置く | 複数のコンテナ（W10 の ECS）で session を共有できない。利用停止の即時の失効を DB の削除でできない |
| 認可を request ごとに DB で確かめるのも今入れる | 利用者を止める操作（US-16）がなく、効き目を確かめられない。AC6 と一緒に W5 で入れる |

## 影響

### ポジティブ

- `quotation` の controller の変更は引数の型の置き換えで済み、TOTP の段を足しても `quotation` は変わらない。
- 仮の主体がなくなり、提出者・承認者などに本当の利用者が記録される。

### ネガティブ

- session は JDK の直列化で DB に置く。`Role` の定数の改名・削除、`CargoUserDetails` の移動や項目の変更、Spring Security の版の更新、W5 の要素の権限の追加で、配備の前の session が復元できなくなる。互換を壊す変更を配備するときは、配備の手順で `platform.spring_session` の行を消し、利用者にログインし直してもらう（W10 の運用手順書に書く）。復元の失敗を無効な session として扱う変換は、W5 の要素の権限を足すときに検討する。
- 未認証の request（ログインの画面の CSRF のトークン、失敗の後のメールアドレス）でも session の行ができる。作られる速さの上限（レート制限）は、W5 のロックか W10 の WAF で扱う（リスク台帳）。

- W5 まで、利用停止しても既存の session は最長 8 時間使える（パイロットの前で本番の利用者はいない）。
- 既存のテスト（controller の単体テスト、画面の層のシナリオ）にログインの準備が要る。
- ステージング・本番の利用者を作る手段は、利用者の管理（US-16）まで無い。

## W5 で見直すこと

- `@EnableMultiFactorAuthentication` を入れると、password の段でも認証の成功のイベントが出る。監査の操作を「password を確かめた」と「ログインした」に分ける。
- 認可の条件（`hasRole`）に要素の権限を加える。
- request ごとの DB の確かめ（SEC-12・AC6）は、利用者 ID から主体を作り直して差し替える形にし、問い合わせを 1 回にまとめる。
- 引数の解決で、TOTP まで済んでいない主体を渡さない。画面の単体テストの主体（`WithAuthenticatedActor`）にも要素の権限を足す。
- ロックの数え方（IA-INV-03）は `identity.application` のサービスと利用者の集約に置き、監査の listener は入口だけにする。

## コンプライアンス

- ArchUnit: `identity` の外は `org.springframework.security` に依存しない。Spring Modulith の検証で、`quotation` は `identity` に依存しない。
- セキュリティの統合テスト: 未認証の拒否、AC2・AC4 の同じ応答、session ID の変化、30 分・8 時間の境界の 3 点、8 時間の確認が認証より前、CSRF、役割の分離、監査記録の同期の書き込み、応答のヘッダー。
- 既定の設定で A-01 が入力済みにならないこと、`dev` の外の `cargotracker.dev-login.*` で起動が失敗すること。

## 参考資料

- [ADR-011 多要素認証（TOTP）](011-mfa-totp.md)
- [Bolt 14 計画](../../development/cargo-tracker/bolt_14_plan.md)
- [Bolt 13 開発成果物レビュー](../../review/cargo-tracker/bolt_13_review_20261006.md)
- [データモデル](../../design/cargo-tracker/data_model.md)、[ドメインモデル](../../design/cargo-tracker/domain_model.md)、[非機能要件](../../design/cargo-tracker/non_functional.md)
