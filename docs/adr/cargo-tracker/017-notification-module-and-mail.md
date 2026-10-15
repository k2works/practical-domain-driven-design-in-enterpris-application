---
type: ADR
title: "ADR-017: 通知モジュールを新設し、メールは公開 API で受けて SMTP で送る"
description: "通知モジュールを最小に新設し、メールの送信を公開 API（notification.interfaces.api の PasswordResetMail）で受けて SMTP で送る決定。identity は依頼の応答から切り離した @ApplicationModuleListener のハンドラーで再設定のトークンを発行し、ACL から同期で呼ぶ。通知は identity に依存しない。dev はログに出す送信、統合テストは Mailpit、SES の SMTP は W10。"
tags: [adr, architecture, notification, mail]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-15T01:13:15Z }
---

# ADR-017: 通知モジュールを新設し、メールは公開 API で受けて SMTP で送る

通知（Notification）を独立したモジュールとして最小に作り、メールの送信を公開 API（`notification.interfaces.api`）で受ける。最初の送信は password の再設定のメール（SEC-08、US-18 AC8）で、`identity` は依頼の応答から切り離したモジュールのイベントのハンドラーでトークンを発行し、腐敗防止層から通知の公開 API を同期で呼ぶ。通知は `identity` に依存しない。

日付: 2026-10-15

## ステータス

採用（2026-10-15、human:kakimomokuri が Bolt 33 の計画の承認で決定した。方針は Bolt 33 の開始準備の決定 1・2 と W5 の決定（2026-10-11））

## コンテキスト

- US-18 AC8 は、password を忘れた利用者に、登録メールアドレスへ 1 回だけ使える再設定のリンク（30 分）を送ることを求める（SEC-08）。登録の有無を示さず、多要素を回復の手順でも迂回させない。
- 通知は一般のサブドメインで、荷主への通知（US-22。W9）もここで送る（[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)）。W5 の決定（2026-10-11）で、通知モジュールを W5 で最小に作り、送信の公開 API を `notification.interfaces.api` に置くとした。
- 公開 API の形は Bolt 17・25 で決めた（`<コンテキスト>.interfaces.api`、`@NamedInterface("api")`、実装は `interfaces.api.internal` のアダプター、呼ぶ側は `application.internal.outboundservices.acl`）。他のコンテキストの `*.interfaces.api` を参照してよいのは、`application.internal.outboundservices.acl..` と `infrastructure.config..` だけ（`LayerArchitectureTest`）。
- コンテキスト間の非同期は `@ApplicationModuleListener` と発行記録（`platform.event_publication`）で行い、`@TransactionalEventListener` は使わない（[ADR-003](003-inter-context-integration.md)）。発行記録はイベントを JSON で残すので、イベントに載せた値は表に残る。
- `notification` パッケージ、メールの依存（`spring-boot-starter-mail`）、メールのコンテナはまだない。デモ環境（Heroku）は dev プロファイルのまま動く（[ADR-013](013-heroku-demo-environment.md)）。
- 通知の節（ドメインモデル）は、宛先をアクセス・監査の公開 API で解決するとしていた。`identity` が通知を呼ぶと、通知から `identity` への依存と循環する。

## 決定

### 1. 通知モジュールと公開 API

| 要素 | 決定 |
| :--- | :--- |
| モジュール | `notification` を新設する。`allowedDependencies = {"shared"}`。公開 API は `notification.interfaces.api`（`@NamedInterface("api")`）、アダプターは `notification.interfaces.api.internal` |
| 操作 | インターフェース `PasswordResetMail` の `send(PasswordResetMailRequest)` が `PasswordResetMailReceipt` を返す。結果は `Sent`・`AlreadySent`・`Failed` |
| 要求 | 依頼の ID（UUID。通知の `event_id`）、トークンの ID（UUID。`target_ref`）、宛先の利用者の ID（UUID）、アドレス、再設定の URL、期限（`UtcInstant`）、言語。値は Java の標準・共有カーネルの型と文字列だけ |
| 通知の中 | アダプターがアプリケーションサービス `notification.application.internal.commandservices.PasswordResetMailService` を呼ぶ。サービスは通知を PENDING で記録してから送信のポートを呼び、SENT か FAILED（理由）にする。状態の更新は `status = 'PENDING'` を条件にした更新で、版の列は持たない |
| 重複 | 同じ `event_id` と宛先の通知が既にあれば送らず `AlreadySent` を返す（UK(event_id, recipient_user_id)。N-INV-02） |
| 本文 | 件名と本文は通知モジュールのテンプレート（Thymeleaf のテキストのテンプレート）で組み立てる。料金などを書かない（N-INV-01） |

### 2. identity からの呼び出しと依存の制約

- `identity` は ACL `identity.application.internal.outboundservices.acl.NotificationPasswordResetMails`（上流の名前 + 名詞）から公開 API を同期で呼ぶ。組み立ては `identity.infrastructure.config` で、`identity` の `allowedDependencies` に `"notification :: api"` を足す。層の規則に例外を足さない。
- ADR-014 の「下流の listener が上流の公開 API を呼ぶ」形ではない。通知は送信の能力を公開 API で提供し、業務の判断を持たない。
- **通知は `identity` に依存しない**（開始準備の決定 6）。宛先（利用者の ID とアドレス）は呼ぶ側が渡す。荷主への通知（US-22）で宛先をどう渡すかは W9 の開始準備で決める。

### 3. 送信のポートとアダプター

| 要素 | 決定 |
| :--- | :--- |
| ポート | `notification.application.internal.outboundservices` の `MailSender`。application は `org.springframework.mail..`・`jakarta.mail..` に依存しない（`LayerArchitectureTest` の禁止に足す） |
| SMTP | `notification.infrastructure.mail.SmtpMailSender`（`spring-boot-starter-mail` の `JavaMailSender`。版は Boot 管理） |
| dev | `notification.infrastructure.mail.LoggingMailSender` がメールをログに出す（宛先・件名・本文。リンクを含む）。dev のプロファイルでだけ Bean になる |
| 守り | `notification.infrastructure.config` で、`DevLoginGuard` と同じ式（dev かつ staging・prod と併用しない）を確かめ、外れたら起動を止める。dev 以外で SMTP の設定がないときも起動を止める |
| 統合テスト | SMTP の送信アダプターは Testcontainers の Mailpit（`GenericContainer`、版を固定）で件名・本文・宛先を確かめる。ローカルの起動では Mailpit を使わない（dev はログに出す） |
| ステージング・本番 | Amazon SES の SMTP。設定と送信元の認証（SPF・DKIM）は W10 の運用準備で行う |

### 4. 送信を依頼の応答から切り離す

| 要素 | 決定 |
| :--- | :--- |
| 依頼の応答 | `identity.application.internal.commandservices.PasswordResetRequestService` は、入力の正規化、利用者の判定、監査の記録（同じトランザクション。IA-INV-08）だけを行い、有効な利用者ならドメインのイベント `PasswordResetRequested`（`identity.domain.events`）を出す。応答は登録の有無にかかわらず同じで、トークンの発行も送信も待たない |
| イベント | 依頼の ID・利用者の ID・依頼の時刻だけを載せる。トークンも URL も載せないので、発行記録（`platform.event_publication`）に平文が残らない |
| ハンドラー | `identity.application.internal.eventhandlers.PasswordResetMailEventHandler`（`@ApplicationModuleListener`。コミットの後に別のスレッドと別のトランザクションで動く）が、利用者の行をロックし、トークンを発行してハッシュを保存し、設定の公開の base URL（`cargotracker.public-base-url`）からリンクを組み立て、ACL で送る。平文のトークンと URL は、このメソッドの中の変数と送信の要求だけにあり、発行記録・ログ・表に残らない |
| 再配信 | 同じ依頼の ID のトークンが既にあれば何もしない（トークンの表の `request_id` に一意制約）。再配信で 2 通送らない |
| 送信の失敗 | `Failed` でも例外にせず完了にする。再配信で新しいトークンを作らない。送れなかった依頼は利用者が依頼し直す |
| ADR-003 | 例外を作らない。`@ApplicationModuleListener` と発行記録の形のまま |

トークンの規則（32 バイトの乱数、SHA-256 のハッシュだけを保存、30 分、1 回だけ、新しい発行で前を無効、1 分以内の依頼し直しは送らない）は [非機能要件](../../design/cargo-tracker/non_functional.md) の SEC-08 とドメインモデルの IA-INV-12 に置く。

### 5. 送信の記録

- 通知モジュールの `notification.notification` の表に、宛先の利用者・アドレス・種類（`PASSWORD_RESET`）・対象・言語・状態・送信時刻・失敗の理由を残す。本文とリンクは保存しない。
- `PASSWORD_RESET` の `target_ref` はトークンの ID とし、本文を再現しない（この種類だけの例外。リンクを再現できる値を残さない）。`recipient_user_id` はコンテキストをまたぐので FK にしない。
- 繰り返し（1 分以内）で送らなかったことは記録せず、送信の記録がないことで分かる。監査（`PASSWORD_RESET_REQUESTED`）と送信の記録は結ばない（AUD-02 の対象の列は使う Bolt で足す）。

### 代替案

| 案 | 採らない理由 |
| :--- | :--- |
| 送信のポートを基盤（`platform`）に置き、`identity` から使う | コンテキストのコードから `platform` を参照しない規則（例外は `platform :: web` だけ）に反する。送信の記録を持つ場所もなくなる |
| `@TransactionalEventListener(AFTER_COMMIT)` と `@Async` で送る | ADR-003 の例外になる。購読の前に止まるとイベントが失われ、再配信もない |
| 依頼の応答の中で同期にトークンを発行して送る | 応答の時間（送信を待つか）で登録の有無が漏れる。SMTP の遅れが画面の応答を遅らせる |
| トークン（または URL）をイベントに載せ、ハンドラーは送るだけにする | 発行記録に平文のトークンが JSON で残る。表にハッシュだけを置く意味がなくなる |

## 影響

### ポジティブ

- 通知の部品を `identity` に持たずに、モジュールと層の規則を例外なしで守れる。荷主への通知（US-22）は同じモジュールに足せる。
- 応答は判定と監査だけなので、登録の有無で応答の内容と時間（トークンの発行と送信の分）がそろう。
- 平文のトークンが表・発行記録・ログに残らない。

### ネガティブ

- 依頼と送信が別のトランザクションになり、送信の失敗を利用者に示せない（利用者が依頼し直す）。送信の再試行とバウンス・苦情の記録（OBS-11）は W9・W10。
- 利用者の検索と監査の書き込みの時間の差は応答に残る（既知の課題。W10 の WAF とあわせて扱う）。
- dev はリンクを含むメールをログに出す。デモ環境（Heroku）でもログに出る（デモの利用者のアドレスだけ。ADR-013）。
- 宛先を呼ぶ側が渡す制約のため、US-22 では宛先の渡し方を W9 の開始準備で決める必要がある。

## コンプライアンス

- ModularityTest のモジュール一覧に通知を足し、`notification` が `shared` だけに依存することを確かめる。
- `LayerArchitectureTest` に、application が `org.springframework.mail..`・`jakarta.mail..` に依存しない規則を足す。`PublicApiArchitectureTest` で公開 API の形を確かめる。identity の合成ルートの `@Bean` の戻り値は AT-07 の範囲に収める。
- 統合テストで、Mailpit にメールが届くこと、登録あり・なし・止められた利用者で応答が同じこと、送信アダプターを遅くしても応答が待たないこと、発行記録の JSON・トークンの表・ログにトークンも URL もないこと、同じ依頼の ID の再配信で 2 通送らないこと、ロールバックしたらハンドラーが呼ばれないことを確かめる。
- dev の守りの式が外れたら起動が止まることを、本体のテストで守る。

## 関連ドキュメント

- [ADR-003](003-inter-context-integration.md)、[ADR-011](011-mfa-totp.md)、[ADR-012](012-authentication-principal-and-session.md)、[ADR-013](013-heroku-demo-environment.md)、[ADR-014](014-downstream-to-upstream-notification.md)
- [バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、[技術スタック](../../design/cargo-tracker/tech_stack.md)、[非機能要件](../../design/cargo-tracker/non_functional.md)（SEC-08、AUD-01）
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)（IA-INV-12、N-INV-01・02）、[データモデル](../../design/cargo-tracker/data_model.md)（`identity.password_reset_token`、`notification.notification`）
- [Bolt 33 計画](../../development/cargo-tracker/bolt_33_plan.md)
