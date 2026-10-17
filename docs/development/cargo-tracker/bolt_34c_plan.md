---
type: Plan
title: "Bolt 34c 計画 - 認証・監査の経路と文書と見張り（振る舞いの変わらない直し）"
description: "W5 の 25 本目の Bolt の計画。ログインの監査の書き手の経路と @Service・@Bean の役割を今の形のまま理由とともに文書にし、回復コードのログインの IA-INV-08 の範囲とロックと IA-INV-04 の関係を明文化する。暗号の部品の規則を名前ごとに分け、@MockitoSpyBean と @Configuration の置き場所を ArchUnit で見張り、login_failure の列の注釈をマイグレーションで直す。34c の行を 34c・34e・34f に分けた。"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-17T11:25:06Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-17T11:25:06Z }
---

# Bolt 34c 計画 - 認証・監査の経路と文書と見張り（振る舞いの変わらない直し）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 34c 回（W5 の 25 本目） |
| 予定 | W5（2026-11-02 の週。前倒しで着手できる）。作業は約 3.6 時間（承認ゲートの待ち時間と、統合テスト・uiTest・SonarQube の待ちを除く） |
| 対象 | 文書（ADR-011・ADR-012・architecture_backend・domain_model・data_model・test_strategy、過去の終了報告の送り先の書き添え）、ArchUnit の規則（`AuthenticationArchitectureTest`・`LayerArchitectureTest`・新しい `TestPlacementArchitectureTest`）、SpotBugs の除外、マイグレーション（`login_failure` の列の注釈）と `LoginFailure` の Javadoc、回復コードのログインの特性テスト（なければ） |
| SP | 0（技術タスク） |
| GitHub | なし（release_plan の W5 の表で扱う技術タスク） |
| 承認ゲート | **ステップごとに止める**（W5 の表の 34c の行。構造とスキーマ）。層・モジュールの規則に当たったら、`/goal` の下でも止めて諮る（T-125） |
| アプローチ | 技術タスク（開発戦略の表のとおり、テストの入口は検証の手段: ArchUnit の規則とスキーマの統合テスト）。本体の振る舞いを変えない。文書 → 見張りの規則 → 注釈のマイグレーションの順。各ステップで `check` の件数と失敗 0 を前後で比べ、`uiTest` はステップ 5 で前後の件数を比べる |
| マニュアル・デモ | 画面を変えないので、マニュアルの更新とデモの動画はない |
| 前の Bolt | [Bolt 34d 終了報告](bolt_34d_report.md) |

## Bolt ゴール

ログインの監査を誰がどこで書くかと、`@Service` と `@Bean` がそれぞれ何のためにあるかが、理由とともに設計文書から読める。回復コードのログインの監査のトランザクションの範囲と、ロックが既存の session を取り消さないことが不変条件として明文化される。暗号の部品の置き場所と、テストの spy・本体の `@Configuration` の置き場所が ArchUnit で見張られ、1 つの名前が変わったときに規則が黙って通らない。`login_failure` の列の注釈が今の数え方と一致する。

### 確かめたい仮説

| # | 仮説 | この Bolt で分かること |
| :--- | :--- | :--- |
| H1 | 暗号の部品の規則を名前ごとに分ければ、1 つの名前が変わったときに対象が空になって落ちる | テストの中の名前の文字列を 1 つ変える変異で、その規則だけが落ちる（控えから戻す） |
| H2 | `@MockitoSpyBean` の置き場所を見張れば、テストのクラスごとに spy の組が増えて Spring のコンテキストが増えることを防げる | 規則を足しても今のテストが緑のまま。基底の外に spy を書く変異で落ちる |

### 開始準備で確かめたこと（T-114。2 つの整合性検証で確かめた）

- **書き手の経路**: `AuthenticationAuditListener` は `PASSWORD_VERIFIED`・数えない `LOGIN_FAILED`（理由 `SERVICE_ERROR`・`UNKNOWN_USER`・利用停止と無効な企業）・`LOGOUT` を 1 文で直接書く（:66・81・90・103・158）。`LoginAttemptService` は `LOGIN_SUCCEEDED`・数える `LOGIN_FAILED`・`ACCOUNT_LOCKED` を回数と同じトランザクションで（:62-73・84-90）、ロック中の試行の `LOGIN_FAILED`（理由 `LOCKED`。数えない）を 1 文で書く（:79-81）。ほかの監査はどれもアプリケーションのサービスが書く。ADR-011:68 は「寄せない」（Bolt 32c の開始準備）と記録している
- **`@Service` と `@Bean`**: `CargoTrackerApplication:22-29` は `..application..` を部品探索から外しており（`ComponentScanTest` が固定）、Bean は `IdentityConfiguration` の `@Bean` の 1 つだけ。`@Service` は JIG がユースケースとして読む印（2026-10-03 の決定。architecture_backend.md:281）。identity の 16 クラスが両方を持つ。SpotBugs の `EI_EXPOSE_REP2` の除外はクラスを 1 つずつ列挙し、`TotpReEnrollmentController`（`interfaces.web`）と notification の 1 つも含む（config/spotbugs/exclude.xml:14-50。正規表現でパッケージを指す前例は :11-12）
- **回復コードのログイン**: `RecoveryCodeVerificationService` はコードの使用と `RECOVERY_CODE_USED` を 1 つのトランザクションで書き（:154-161）、成功のイベントでリスナーが `LoginAttemptService.recordSuccess`（`LOGIN_SUCCEEDED` と回数の戻し）を別のトランザクションで書く。後者の追記が失敗したときの結果（ログインの応答、使ったコード）を固定するテストは見当たらない（確認ポイント 7）
- **ロック**: `SessionRevalidationService` はロックを見ない。session を捨てるのは利用停止・無効な企業・利用者なし（:214-224）と、再開の確定の後の消去（Bolt 34d）
- **暗号の部品の規則**: `AuthenticationArchitectureTest:151-177` は 11 の名前を `.or()` でつないだ 1 本の規則。33a・33d で一覧から漏れた名前を後から足した。`QrCodeSvg`・`JavaOtpTotpCodeGenerator` はほかの規則も名前で指す（:129-136・:208-216）。`archunit.properties` はなく、ArchUnit 1.5.1 の既定は `failOnEmptyShould=true`。前例は `.allowEmptyShould(false)` を明示する（:263、T-107）
- **spy と `@Configuration`**: `@MockitoSpyBean` を使うのは `IdentitySpyIntegrationTestBase`（パッケージの外から見えない `abstract class`、7 フィールド）と `integration.QuotationRequoteRollbackIntegrationTest:58`。本体で直接 `@Configuration` を持つのは config の 8 つと `ClockConfiguration`（ルート）だけ。`@SpringBootApplication` とテストの `@TestConfiguration`（約 15）はメタ注釈として `@Configuration` を持つ。「config には `@Configuration` だけを置く」は `LayerArchitectureTest:194-200`（`DoNotIncludeTests`）
- **`login_failure` の注釈**: `V20261011083300__create_identity_totp.sql:57` の注釈は「連続の失敗の回数（password と TOTP を合わせる。成功で 0 に戻す）」のまま。`LoginFailure.java:11` の Javadoc も回復コードを含まない。注釈だけのマイグレーションの前例がある（`V20261003110000__add_japanese_comments.sql`）。`SchemaCommentIntegrationTest` は注釈があるかだけを見る。SchemaSpy の生成物はリポジトリになく、MkDocs の配備が作る

## ふりかえりと前の Bolt からの引き継ぎ

| 入力 | 内容 | この Bolt での扱い |
| :--- | :--- | :--- |
| 開始準備の決定（2026-10-17、human:kakimomokuri） | (1) 34c の行を 3 本に分ける: 34c（認証・監査の経路と文書と見張り）、34e（利用者の管理のドメインとサービス）、34f（S-20 の画面の構造）。この順に進める。(2) ログインの監査の書き手は今のまま（リスナーが直接書く分を残す）にし、理由を書く。(3) `@Service` と `@Bean` は今の形を残し、「二重の定義の解消」を課題から外す。SpotBugs の除外をパッケージの指定にまとめる。(4) `login_failure` の注釈のマイグレーションをこの Bolt で足す | 確認ポイント 1〜4 |
| Bolt 34d の Try T-147 | SonarQube は、ほかの Gradle の実行が終わってから流す | ステップ 5 で、開発レビューのエージェントにはテストを流させない（読むだけ） |
| Bolt 34d の Try T-146 | 状態を戻せる操作では往復の後に残るものを確認する | この Bolt は振る舞いを変えないので当たらない |
| Bolt 32 の議題 5・既知の課題 | 監査の書き込みの経路が 2 つ | 決定 (2) で今のままにし、ADR-011 と architecture_backend に書き手の表と理由を書く |
| Bolt 32c の議題 6 | ロックと IA-INV-04 の関係の明文化 | domain_model の IA-INV-03・04 に書く（確認ポイント 6） |
| Bolt 32 の既知の課題（bolt_32_report.md:159・160） | 回復コードのログインの IA-INV-08 の範囲、`login_failure` の注釈 | IA-INV-08 に書く（確認ポイント 7）。注釈はステップ 4（決定 4） |
| Bolt 31d の既知の課題（bolt_31d_report.md:182）と Bolt 32d（bolt_32d_report.md:139） | 暗号の部品の規則を名前ごとに分けるか（問い）、spy と `@Configuration` の見張り（AT-07 とあわせて見る） | 分ける（確認ポイント 8）。AT-07（`@Bean` が返す型）とは重ならない（新しい規則は置き場所）。ステップ 2・3 |
| Bolt 34 の既知の課題（bolt_34_report.md:106） | 照会の射影（`UserSummary`・`UserDetail`）を集約のポートで受けている | 入れない（W10） |
| Bolt 33b〜34d の Try（T-112・T-117・T-119・T-131・T-135・T-137・T-140・T-141） | 見張りの理由の書き方で確かめる、規則ごとに文言で確かめる、否定は先に出ていることを確かめる、控えから戻す、例外を足すならその根拠を見張る、スクリプトの接頭辞、規則を根拠にするなら規則がその依存を見るかを確かめる、写しの数だけ変異 | 暗号の部品の規則は 11 本が 1 つの補助を共有するので、変異は代表 2 つ（ほかの規則が指さない `Base32`・`RandomRecoveryCodeGenerator`）で足りる（T-117・T-141 の「写し」は補助の 1 か所）。spy の規則は、先に基底の spy のフィールドが対象として数えられていること（空でない）を確かめる（T-119）。`ClockConfiguration` の例外は、例外が広がらない見張りを同じステップで足す（T-135）。スクリプトは `b34c_` で始める |

## スコープ

### 入れるもの・入れないもの

| 入れる | 入れない（34e・34f・後で） |
| :--- | :--- |
| ログインの監査の書き手の表と、リスナーが直接書く理由（ADR-011 の Bolt 34c の決定、ADR-012、architecture_backend） | 書き手を `LoginAttemptService` に寄せること（決定 2） |
| `@Service`（JIG の印）と `@Bean`（組み立て）の役割の文書化、「二重の定義の解消」を課題から外す | 印を独自の注釈に替えること、部品探索に戻すこと（決定 3） |
| SpotBugs の `EI_EXPOSE_REP2` の除外を `identity.application.internal` の下全体（commandservices・queryservices・eventhandlers・outboundservices の ACL・session の定数）のパッケージの指定にまとめる。`TotpReEnrollmentController` と notification の 1 つは列挙に残す | ほかのコンテキストの除外の書き方（変えない） |
| IA-INV-08 に回復コードのログインのトランザクションの分かれ目、IA-INV-03・04 にロックは既存の session を取り消さないこと | ロックで session を取り消すこと（振る舞いの変更） |
| 回復コードのログインで `LOGIN_SUCCEEDED` の追記が失敗したときの特性テスト（今の振る舞いを固定するだけ。既存になければ 1 本） | その振る舞いの変更 |
| 暗号の部品の規則を名前ごとに分ける（AT-09） | 暗号の部品の名前の変更 |
| 見張りの規則: identity の統合テストでは `@MockitoSpyBean` を `IdentitySpyIntegrationTestBase` の外のフィールドで使わない（AT-10）、本体の直接の `@Configuration` は `infrastructure.config` に置き、ルートは `ClockConfiguration` だけ（AT-11） | `integration` パッケージの `@MockitoSpyBean`（identity の外。範囲に入れない）、クラスに付ける `@MockitoSpyBean(types=…)`（範囲の外と明記する） |
| `login_failure.consecutive_failures` の注釈を直すマイグレーション（common、注釈だけ）と `LoginFailure` の Javadoc | `login_failure` の列や制約の変更 |
| 34e・34f への送り先を過去の報告に書き添える | 34e・34f の項目（判定を通さずに呼べる形、判定の前半、controller の分割ほか） |

### 設計の約束（Try T-1）

- **書き手の理由**: リスナーが直接書くのは、Spring Security の認証のイベントを写すだけで数え方に関わらず、1 文の追記で済む記録（`PASSWORD_VERIFIED`・数えない `LOGIN_FAILED`・`LOGOUT`）。数え方と結び付く記録（成功・数える失敗・ロック）は、回数と同じトランザクションにするため `LoginAttemptService` が書く。ロック中の試行（理由 `LOCKED`）も数えないが、ロック中かどうかを決めるのはサービスの 1 か所（`isLocked` と条件付きの更新）なので、サービスが 1 文で書く。利用者の管理の監査は、どれも状態の変更と同じトランザクションにするためアプリケーションのサービスが書く（範囲外の記録は単独で、確定の POST だけ。GET では書かない）。ADR-011 に Bolt 34c の決定として追記し、Bolt 32c の決定の「Bolt 34 で見直す」を閉じる
- **SpotBugs の除外**: `<Match><Bug pattern="EI_EXPOSE_REP2"/><Package name="~com\.example\.cargotracker\.identity\.application\.internal\..*"/></Match>`（前例の形）。列挙していた identity の `application.internal` のクラスを消す。`TotpReEnrollmentController` と notification の 1 つは残す。「このクラスに限って抑止する」「抑止は最小に」の注釈を、「DI の部品を保持するだけのサービスの集まり。Bolt 34c の決定 3」に書き直す
- **暗号の部品の規則（AT-09）**: `AuthenticationArchitectureTest` に、名前を受けて「その名前のクラスは `identity.infrastructure.crypto` にある」規則を作る補助を置き（`.allowEmptyShould(false)` を明示する）、11 の名前ごとに `@ArchTest` を 1 つずつ書く。前例は `AcceptanceStepArchitectureTest:30-43`
- **spy の規則（AT-10）**: 新しい `TestPlacementArchitectureTest`（`architecture` パッケージ。テストのクラスも読む `@AnalyzeClasses`。前例は `AcceptanceStepArchitectureTest`）に、「パッケージ `..identity..` のクラスのフィールドの `@MockitoSpyBean` は、`com.example.cargotracker.identity.infrastructure.security.IdentitySpyIntegrationTestBase` にだけ宣言する」（完全修飾名の文字列で指す）
- **`@Configuration` の規則（AT-11）**: 本体の置き場所の規則なので `LayerArchitectureTest` の :194-200 の隣に置き、直接の注釈（`areAnnotatedWith(Configuration.class)`）だけを見る。「直接の `@Configuration` は `..infrastructure.config..` にあるか、`com.example.cargotracker.ClockConfiguration` である」と、例外が広がらない見張り「ルートのパッケージの直接の `@Configuration` は `ClockConfiguration` だけ」（T-135）
- **注釈のマイグレーション**: `common/V20261017140000__comment_identity_login_failure.sql`（`COMMENT ON COLUMN identity.login_failure.consecutive_failures IS '連続の失敗の回数（ログインの password・TOTP・回復コードの誤りを合わせて数え、ロック中の試行は数えない。2 つの要素がそろったログインの成功で 0 に戻し、ロック中は戻さない。IA-INV-03、Bolt 32）';`）。`LoginFailure.java:11` の Javadoc を同じ数え方にそろえる
- **新しいファイル**: マイグレーション 1 つ、`TestPlacementArchitectureTest`、回復コードの特性テスト（既存になければ。確認ポイント 7）

## 設計（この Bolt の範囲）

4 図のうち、どれも置かない。ドメインモデル図は集約・値・サービスを変えないので、状態遷移図は状態を変えないので、ER 図は表と列を変えない（注釈は構造を変えない）ので、画面遷移図は画面を変えないので省く。代わりに、ログインの監査の書き手の表を置く。

### 監査の書き手（文書に写す表）

| 記録 | 書き手 | トランザクション |
| :--- | :--- | :--- |
| `PASSWORD_VERIFIED` | `AuthenticationAuditListener` | 1 文 |
| `LOGIN_FAILED`（数えない: 理由 `SERVICE_ERROR`・`UNKNOWN_USER`・利用停止・無効な企業） | `AuthenticationAuditListener` | 1 文 |
| `LOGOUT` | `AuthenticationAuditListener` | 1 文 |
| `LOGIN_SUCCEEDED`・数える `LOGIN_FAILED`・`ACCOUNT_LOCKED` | `LoginAttemptService` | 回数と同じ |
| `LOGIN_FAILED`（理由 `LOCKED`。数えない） | `LoginAttemptService` | 1 文（ロックの判定をサービスの 1 か所にするため） |
| `RECOVERY_CODE_USED` | `RecoveryCodeVerificationService` | コードの使用と同じ（`LOGIN_SUCCEEDED` と回数の戻しは別のトランザクション） |
| `SESSION_REVOKED` | `SessionRevalidationService` | 単独 |
| TOTP の登録・再登録、再設定の完了、役割と利用状態の変更 | それぞれのアプリケーションのサービス | 状態の変更と同じ |
| 再設定の依頼（受付・不受付） | `PasswordResetRequestService` | 単独（不受付は状態を変えない） |
| `ADMIN_SCOPE_DENIED` | `AdministrationScopeDenialService`（controller の確定の POST だけから） | 単独。GET では書かない |

注（設計への反映が必要。ステップ 1 で直す行）:

- ADR-011: Bolt 32c の決定（:68）の「Bolt 34 で見直す」を受けて、ステータスの節に Bolt 34c の決定（書き手は今のまま、理由、上の表）を足す。決定 7 の表（:252 の監査の行か :254 の境界の行）と決定 9（:283）に回復コードのトランザクションの分かれ目、:283 の末尾の「すべてサービスに寄せるかは終了報告の議題」を「Bolt 34c の決定で今のままにした」に。改訂の経緯に 1 行
- ADR-012: :108 の「権限外のアクセスの試行の記録は US-16 の Bolt で足す」を「Bolt 34b から controller の確定の POST で `AdministrationScopeDenialService` が書く（GET では書かない）」に、見直しの 5（:109・:180）の書き手の記述を Bolt 34c の決定に結ぶ。改訂の経緯に 1 行
- architecture_backend.md: :281 に `@Service`（JIG の印）と `@Bean`（組み立て）の役割の説明と Bolt 34c の決定 3、:282 に AT-11 と `ClockConfiguration` を例外にする理由（どのコンテキストにも属さない時刻の横断の取り決め。BR-10・ADR-007）と決めた人と日付、:284 の「11 の名前の一覧の見張りで確かめる」を「名前ごとの規則（AT-09）で確かめる」に、:288 に監査の書き手の表、:296 の「二重の定義の解消は Bolt 34c」を書き換える、SpotBugs の除外をパッケージにまとめたこと
- domain_model.md: IA-INV-03・04（ロックはログインの試行だけを抑え、既存の session を取り消さない。session を捨てるのは利用停止・無効な企業・利用者なしと再開の消去。IA-INV-04）、IA-INV-08（回復コードのログインでは、コードの使用と `RECOVERY_CODE_USED` が 1 つのトランザクション、`LOGIN_SUCCEEDED` と回数の戻しが別のトランザクション。後者の失敗の結果は確認ポイント 7 で確かめた形で書く）、:1023 付近の監査の段落、:1037 の「2 つまとめて Bolt 34c で直す」を「Bolt 34e で」に、`LoginFailure` の要素表の行の数え方
- data_model.md: :920 の末尾の「マイグレーションが要るので次にこの表を変えるときに直す」を「Bolt 34c で注釈を直した」に
- test_strategy.md: AT の表（:287-294）に AT-09（暗号の部品の置き場所を名前ごと）・AT-10（identity の spy の置き場所）・AT-11（本体の `@Configuration` の置き場所）、:330 の spy の段落に見張りがあること
- release_plan.md: 34c の行を 34c・34e・34f に分けた（開始準備で済み）。:553 の W5 の状態（34d を終えた、34c・34e・34f の分け方）、:557 の局面とアプローチ（34c・34e・34f）、チェックリスト、更新履歴
- 過去の報告の送り先: bolt_34d_report.md:105 の「34c に送ったもの」の各項目に「→ 34e」「→ 34f」、bolt_32d_report.md:139 と bolt_31d_report.md:182 に「→ 34c」

## ステップ計画

状態の記号: `[ ]` 未着手、`[-]` 進行中、`[?]` 承認待ち、`[x]` 完了。各ステップの終わりに `check` が緑で件数が減っていないことを確かめ、push してアプリの CI と MkDocs の配備を確かめる（T-26、T-126）。コミットは pathspec で作る（T-110）。

- [ ] **1. 文書と SpotBugs と特性テスト** 【承認ゲート: 設計文書・構造】
  - 回復コードのログインで `LOGIN_SUCCEEDED` の追記が失敗したときの今の振る舞いを、既存のテストで確かめる。なければ特性テストを 1 本足す（spy で `LOGIN_SUCCEEDED` の追記だけを失敗させ、ログインの応答とコードの使用済みを固定する）
  - 上の注の行を直す。SpotBugs の除外をパッケージの指定にまとめる（`check` の SpotBugs が緑のまま）
  - 完了の判定: `okf:check` ERROR 0、`documentationTest` 緑、`check` 緑
- [ ] **2. 暗号の部品の規則を名前ごとに分ける（AT-09）** 【承認ゲート: 構造】
  - 名前ごとの規則と、テストの中の名前の文字列を 1 つ変える変異で落ちることの確かめ（`Base32`・`RandomRecoveryCodeGenerator` の 2 つ。控えから戻す）
- [ ] **3. spy と `@Configuration` の見張り（AT-10・AT-11）** 【承認ゲート: 構造】
  - AT-10 は先に、基底の spy のフィールドが対象として数えられていることを確かめる（T-119）。AT-11 と例外が広がらない見張り。今のテストが緑のまま。基底の外に spy を置く変異と、config の外に `@Configuration` を置く変異で落ちることを確かめる（控えから戻す）
- [ ] **4. `login_failure` の注釈のマイグレーション** 【承認ゲート: スキーマ】
  - マイグレーションと `LoginFailure` の Javadoc。PostgreSQL の統合テスト（`SchemaCommentIntegrationTest`）と H2 の起動が緑。SchemaSpy は MkDocs の配備で作り直される
- [ ] **5. uiTest と SonarQube と開発レビューと終了報告** 【承認ゲート: 終了報告】
  - `uiTest`（件数を前後で比べる）、SonarQube（T-116。ほかの Gradle が終わってから。T-147）。開発レビュー（アーキテクト、テスター。読むだけでテストは流させない）。`bolt_34c_report.md`、release_plan、開発の索引

### 時間の配分と打ち切り

| # | 目安 | 打ち切り |
| :--- | :--- | :--- |
| 1 | 75 分 | 特性テストが既存の spy の組を変えると見えたら止めて諮る |
| 2 | 25 分 | — |
| 3 | 35 分 | 規則が既存のテストのクラスを大量に拒むと見えたら止めて諮る |
| 4 | 30 分（統合テストの待ちを除く） | — |
| 5 | 50 分（uiTest と SonarQube の待ちを除く） | — |

合計 215 分（約 3.6 時間）。ステップ 3 を終えた時点で 2.5 時間を超えていたら止めて諮る（候補: 見張りの規則を 34e に送る）。

## 確認ポイント（計画の承認の場でまとめて確認する。Try T-6）

| # | 確認すること | ステップ | 推奨・決定 |
| :--- | :--- | :--- | :--- |
| 1 | **分け方** | 全体 | 決定済み（1）: 34c・34e・34f の 3 本。この順 |
| 2 | **ログインの監査の書き手** | 1 | 決定済み（2）: 今のまま。理由と書き手の表を ADR-011・ADR-012・architecture_backend に書く |
| 3 | **`@Service` と `@Bean`** | 1 | 決定済み（3）: 今の形を残し、課題から外す。SpotBugs の除外を `identity.application.internal` の下全体のパッケージの指定にまとめ、`TotpReEnrollmentController` と notification の 1 つは列挙に残す |
| 4 | **`login_failure` の注釈** | 4 | 決定済み（4）: この Bolt でマイグレーションを足す。注釈の文は設計の約束のとおり |
| 5 | **`ClockConfiguration` を AT-11 の例外にする**（層の規則の例外を設計の判断として諮る） | 3 | 例外にする（置き場所を動かさない）。理由: どのコンテキストにも属さない時刻の横断の取り決め（BR-10・ADR-007）で、置く config がない。例外は完全な名前の 1 つに限り、「ルートの直接の `@Configuration` は `ClockConfiguration` だけ」を同じステップで見張る（T-135）。architecture_backend:282 に理由と決めた人と日付を書く。代わりに `shared.infrastructure.config` などへ動かす案もある（1 クラスの移動で、`Clock` は型で注入される）。決定済み（計画の承認） |
| 6 | **ロックと IA-INV-04 の文言** | 1 | 「ロックはログインの試行だけを抑え、既存の session を取り消さない（session を捨てるのは利用停止・無効な企業・利用者なしと、再開の確定の後の消去。IA-INV-04）」を IA-INV-03 と IA-INV-04 の両方に書く。決定済み（計画の承認） |
| 7 | **回復コードのログインで `LOGIN_SUCCEEDED` の追記が失敗したとき** | 1 | 文書は今の振る舞いを確かめてから書く。固定するテストがなければ特性テストを 1 本足す（振る舞いは変えない）。結果がログインの拒否で、コードが使用済みのまま残るなら、そう書き、変えるかは後の Bolt の議題にする。決定済み（計画の承認） |
| 8 | **暗号の部品の規則を名前ごとに分ける**（Bolt 31d の問いへの答え） | 2 | 分ける。11 本が 1 つの補助を共有するので、変異は代表 2 つで足りる。決定済み（計画の承認） |
| 9 | **見張りの規則の範囲と置き場所** | 3 | AT-10 は identity のパッケージのクラスのフィールドだけ（`integration` パッケージの 1 つとクラスに付ける使い方は範囲の外と明記）、新しい `TestPlacementArchitectureTest` に置く。AT-11 は本体の規則なので `LayerArchitectureTest` に置き、直接の注釈だけを見る。決定済み（計画の承認） |
| 10 | **新しいファイル** | 1・3・4 | マイグレーション 1 つ、`TestPlacementArchitectureTest`、特性テスト（なければ）。決定済み（計画の承認） |
| 11 | **開発レビュー** | 5 | アーキテクト、テスター（読むだけ）。決定済み（計画の承認） |
| 12 | **アプローチと承認ゲートと時間** | 全体 | 技術タスク（テストの入口は ArchUnit とスキーマの統合テスト）。ステップごとに止める（構造・スキーマ）。約 3.6 時間。決定済み（計画の承認） |

## 完了条件

- [ ] ログインの監査の書き手の表と理由、`@Service` と `@Bean` の役割が ADR-011・ADR-012・architecture_backend にあり、「二重の定義の解消」が課題から外れている
- [ ] IA-INV-03・04・08 に、ロックと session の関係と、回復コードのログインのトランザクションの分かれ目が、確かめた振る舞いのとおりに書かれている
- [ ] 暗号の部品の規則が名前ごとにあり、名前を変える変異で落ちる。spy と `@Configuration` の置き場所の規則があり、変異で落ちる。`ClockConfiguration` の例外が文書にあり、広がらないことを見張っている
- [ ] `login_failure.consecutive_failures` の注釈と `LoginFailure` の Javadoc が今の数え方と一致する
- [ ] `check` と `uiTest` が緑で、件数が減っていない（足した規則とテストの分だけ増える）。push してアプリの CI と MkDocs の配備を確かめた。SonarQube の Quality Gate が PASS、`okf:check` ERROR 0
- [ ] 開発レビュー、終了報告

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-17 | 初版作成（承認待ち）。human:kakimomokuri の決定（34c・34e・34f に分ける、監査の書き手は今のまま理由を書く、`@Service` と `@Bean` は今の形を残す、`login_failure` の注釈のマイグレーションを 34c で足す）を反映した | anthropic/claude-opus-5-5 |
| 2026-10-17 | 詳細整合性検証（20 件）と横断整合性検証（15 件）の指摘を反映した。SpotBugs の除外に残すクラス、書き手の表の `LOCKED` と再設定の依頼と範囲外の記録、AT-11 を `LayerArchitectureTest` に置き直接の注釈だけを見る、`ClockConfiguration` の例外の理由と見張りと独立の確認ポイント 5、回復コードの特性テスト（確認ポイント 7）、`allowEmptyShould(false)` と変異の名前、注釈の文と Javadoc、AT の番号、アプローチを技術タスクに、注の行の具体化（ADR-011:252・283、ADR-012:108、architecture_backend:281・282・284・296、domain_model:1037、release_plan:553・557、過去の報告の送り先）、時間（約 3.6 時間） | anthropic/claude-opus-5-5 |
| 2026-10-17 | 計画を承認した。確認ポイント 5〜12 を推奨のとおりに決めた（`ClockConfiguration` は AT-11 の例外にする、回復コードは今の振る舞いを確かめて書く）。`/goal` の下で、ステップごとの承認ゲートでは止めずに進め、層・モジュールの規則と打ち切りの条件に当たったときだけ止めて諮る | anthropic/claude-opus-5-5、承認 human:kakimomokuri |

## 関連ドキュメント

- [リリース計画](release_plan.md)（W5）
- [Bolt 34d 終了報告](bolt_34d_report.md)、[Bolt 32 終了報告](bolt_32_report.md)、[Bolt 32c 終了報告](bolt_32c_report.md)、[Bolt 32d 終了報告](bolt_32d_report.md)
- [ADR-011](../../adr/cargo-tracker/011-mfa-totp.md)、[ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[データモデル](../../design/cargo-tracker/data_model.md)、[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、[テスト戦略](../../design/cargo-tracker/test_strategy.md)
