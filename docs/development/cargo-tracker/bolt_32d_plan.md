---
type: Plan
title: "Bolt 32d 計画 - 認証の部品の低の指摘とテストの整理"
description: "W5 の 11 本目の Bolt の計画。Bolt 31 から送ってきた認証の部品の低の指摘（Base32 の不正な長さ、合成ルートの部品の組み立て方）を片づけ、Bolt 32・32c で増えた identity の統合テストの重複と揺れの元を整理する（共通の時計の設定と spy の基底で Spring のコンテキストを減らす、テストの補助の共通化、同時の待ちを PostgreSQL のロックの待ちで判定、dev のテストの刻みの境界、8 時間の起点の値と監査の順、ロックのシナリオの axe）。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-14T03:55:21Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-14T03:55:21Z }
---

# Bolt 32d 計画 - 認証の部品の低の指摘とテストの整理

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 32d 回（W5 の 11 本目。US-18 の残りの 8 本目） |
| 予定 | W5（2026-11-02 の週。前倒しで着手できる）。作業は約 4 時間 10 分（承認ゲートの待ち時間を除く。上限に近い。「時間の配分と打ち切り」） |
| 対象 | `identity`（`Base32` の不正な長さ、合成ルートの部品の組み立て方）、identity のテスト（統合テストの Spring のコンテキスト、テストの補助、同時の待ちの判定、dev のテストの刻みの境界、8 時間の起点の値と監査の順）、画面の層のロックのシナリオの axe、ADR-011、アーキテクチャ、テスト戦略、リリース計画 |
| SP | 0（US-18 の SP は #13 をクローズする Bolt 33b で数える） |
| GitHub | [#13](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/13)（US-18、R1.0）。この Bolt では閉じない |
| 承認ゲート | **ステップごとに止める**（W4 の締めの決定: 認証の完成はステップごと）。確認必須（構造（合成ルートの部品の組み立て方、テストの共通の設定）、セキュリティ（dev だけ。Base32 の失敗へ倒す範囲））は計画の承認の場で確認ポイントとして判断を受ける |
| アプローチ | リファクタリング（W5 の表の 32d の行）。テストの整理と組み立ての変更は振る舞いを変えず、テストの件数と結果を変えない（足すテストを除く）。振る舞いが変わるのは `Base32` の不正な長さの 1 つだけで、そこはインサイドアウトの TDD にする（Bolt 31d と同じ書き方） |
| 前の Bolt | [Bolt 32c 終了報告](bolt_32c_report.md)、[Bolt 32 終了報告](bolt_32_report.md)、[Bolt 31d 終了報告](bolt_31d_report.md) |

## Bolt ゴール

Bolt 31 から送ってきた認証の部品の低の指摘を片づけ、Bolt 32・32c で増えた identity の統合テストの重複と揺れの元を整理する。不正な Base32 は失敗へ倒し、identity の合成ルートの組み立て方を規則にそろえ、統合テストが作る Spring のコンテキストの数を減らし、テストの確かめを強める（同時の待ちは PostgreSQL のロックの待ちで判定、8 時間の起点は値で、監査は順で確かめる）。

### 確かめたい仮説

| # | 仮説 | この Bolt で分かること |
| :--- | :--- | :--- |
| H1 | 時計の設定と spy の組を共通にすれば、identity の統合テストの Spring のコンテキストの数が減り、テストの件数と結果は変わらない | 整理の前後で、`org.springframework.test.context.cache` の DEBUG の統計（コンテキストの数・hit・miss）と、`check` の件数・失敗 0 を比べる |
| H2 | 同時の操作の「待ち」を PostgreSQL のロックの待ち（`pg_blocking_pids` に先の操作の接続が現れる）で判定すれば、「500 ミリ秒の間に終わらない」より確かな証拠になる | 待ちの判定を外す違反（後の操作が先の行ロックを取らない）を入れて、同時のテストが落ちることを確かめる（T-112） |

### 開始準備で確かめたこと（T-101・T-114）

- **spy とコンテキストの共有**（spring-test 7.0.9 のソースで確かめた。横断整合性検証）: コンテキストの区別の鍵に入るのは `BeanOverrideContextCustomizer` の handler の集合で、型で引く spy の handler の等しさにはフィールド名が入る（`BeanOverrideHandler`）。親クラスのフィールドも探す。よって、2 つのクラスが同じ型の spy を持っていても、フィールド名や置き場所が違えば別のコンテキストになり、同じフィールドを継ぐ基底のクラスなら共有できる。`@Import` の組も鍵に入るので、時計の設定もそろえる必要がある。`@MockitoSpyBean` の `reset` の既定は `MockReset.AFTER`（各テストの後）
- **合成ルートの部品の移動**: `RandomTotpSecretIssuer`・`RandomRecoveryCodeGenerator` に `@Component` を付けても、ArchUnit（`AuthenticationArchitectureTest` は依存とパッケージだけを見る）、部品探索（`ComponentScanTest` が外すのは `.application.` だけ）、Spring Modulith（同じモジュールの同じパッケージ）のどれにも当たらない（CLAUDE.md の「規則が置き場所を拒んだら諮る」に当たらない）
- 実際にコンテキストの数が減ることは、ステップ 4 の最初に骨組み（時計の共通の設定だけを 2 つのクラスに当てる）で数えて確かめる（T-101）。減らなければ止めて諮る

## ふりかえりと前の Bolt からの引き継ぎ

| 入力 | 内容 | この Bolt での扱い |
| :--- | :--- | :--- |
| 開始準備の決定（2026-10-14、human:kakimomokuri） | Base32 の不正な長さは失敗へ倒す。合成ルートは今のクラスの分け方のまま規則を書く（外れている部品だけ動かす）。統合テストのコンテキストは時計と spy の両方をまとめる。ほかの 6 項目もすべて入れる | 本計画の範囲と、確認ポイント 1〜4 |
| Bolt 32c の Try T-115 | 「〜では呼ばない・読まない」の約束は、その条件が成り立つ場面で確かめる | ステップ 5 の待ちの判定は、待っていることを先に確かめてから先をコミットする |
| Bolt 32c の Try T-116 | SonarQube はテストとコードを書き換えていない間に流す | ステップ 6 |
| Bolt 32 の Try T-113・T-114 | 共有の表の件数はそのテストの利用者で絞る。枠組みに頼る確認ポイントは確かめてから決める | ステップ 5 の監査の順は、そのテストの利用者で絞って `occurred_at` の順で確かめる。上の「開始準備で確かめたこと」 |
| Bolt 31d の Try T-110〜T-112 | pathspec のコミット、`LogCapture` は見張る操作の間だけ、見張りの違反は理由になった書き方で | 全ステップ。H2 の違反は「待たずに終わる」書き方そのもので確かめる |
| Bolt 31 の低の指摘（Bolt 31 のレビュー 18） | Base32 の不正な長さ、T-100 の待ちの判定、刻みを戻す SQL の重複、dev のテストの刻みの境界、合成ルートのクラスの分け方 | ステップ 2〜5 |
| Bolt 31 の既知の課題 | 監査の同じ時刻の並び | ステップ 5。同じ時刻に書く行（`RECOVERY_CODE_USED` と `LOGIN_SUCCEEDED`）は順を問わず集合で確かめ、時刻の違う行だけを順で確かめる |
| Bolt 31d から送った指摘 | 3 つの統合テストの時計の設定、8 時間の起点の値と監査の順のテスト | ステップ 4・5 |
| Bolt 32 の既知の課題 | 統合テストの独自の Spring のコンテキスト、画面の層のロックのシナリオで誤りのたびに axe を流している | ステップ 4・5 |
| Bolt 32c の既知の課題 | `sessionExists` と Spring Session の表の参照の重複、`SessionRevalidationSecurityIntegrationTest` の独自のコンテキスト | ステップ 4 |
| W4 の Try T-83・T-88・T-89・T-90 | 骨組みのまま流して本命で落とす。見張り。後に回したものは Issue か計画の表に。画面の文言を変える Bolt にテクニカルライターの観点 | 全ステップ。後に回すものはステップ 1 でリリース計画の行に書く（確認ポイント 7）。画面と文言は変えないので、テクニカルライターの観点は文書（ADR・テスト戦略）に絞る |

## スコープ

### 入れるもの・入れないもの

| 入れる | 入れない（後で） |
| :--- | :--- |
| `Base32.decode` が不正な長さ（空白と埋め草を除き大文字にした後の長さを 8 で割った余りが 1・3・6）と、余りのビットが 0 でない最後の文字と、埋め草の数の誤り（埋め草を付けるなら 8 の倍数の長さにそろうこと）を `IllegalArgumentException` で拒む | Bolt 33b に送った低の指摘（桁数・周期・アルゴリズムの定数、SVG の組み立て、登録を始めた時刻のキーの定数、回復コードの形の知識、`TotpEnrollmentService` の引数の型と `instanceof` の `switch` 化、`normalize` の戻り値の型、空の復号の結果） |
| identity の合成ルートの規則（下の「設計の約束」）と、外れている暗号の部品 2 つ（`RandomTotpSecretIssuer`・`RandomRecoveryCodeGenerator`）を部品探索に移す。`@Bean` の戻り値の型の見張り | 合成ルートのクラスを分けること（今の分け方のまま）。ほかのコンテキストの合成ルート（腐敗防止層・`ApplicationRunner`・booking の採番の `@Bean`）は規則の対象外 |
| 統合テストの共通の時計の設定（1 つの `@TestConfiguration`）と、spy を使う 2 つのクラスの共通の基底（時計の設定も持つ） | `@Service` と `@Bean` の二重の定義、ログインの監査の書き込みの経路、ロックと IA-INV-04 の明文化（Bolt 34） |
| テストの補助: 刻みを戻す SQL（3 か所）と、Spring Session の表の参照（下の表の 5 つのクラスの 5 種類の操作）を共通にする | 500 の応答の画面の確かめ、監査の書き込みの誤りのロールバックの統合テスト、request ごとの SQL と接続プールの計測、`password_hash` を request ごとに読むこと、存在する利用者としない利用者の DB の往復の回数（W10 の性能・運用の評価） |
| `OverlappingTransactions` の待ちを PostgreSQL のロックの待ちで判定する（4 つの呼び出し元を直す） | 「5 回」「15 分」の画面の文言と `LockPolicy` の重複（Bolt 33b の 2 章の書き直しと一緒に見る）、「暗号の部品は crypto にある」の規則を名前ごとに分けるか（Bolt 34 で合成ルートに部品が増えるときに見る） |
| `DevLoginPrefillDevProfileTest` の A-02 の入力済みの刻みの境界の揺れ | ユーザーマニュアル（利用者に見える振る舞いは変わらない。画面と文言も変えない） |
| 8 時間の起点の値（TOTP・回復コードが通った時刻）と、監査記録の順のテスト | デモの動画（画面の振る舞いが変わらない） |
| 画面の層のロックのシナリオで、axe を 1 回目の誤りとロックの後の画面だけにする | テストの JVM の途中の終了（Bolt 32 の既知の課題）は、出たらメモリを調べる（見張りを続ける） |

Spring Session の表を参照する 5 つのクラスと操作:

| クラス | 操作 |
| :--- | :--- |
| `SessionRevalidationSecurityIntegrationTest` | session の行の有無（`sessionExists`） |
| `AuthenticationSecurityIntegrationTest` | 最終アクセスの時刻を戻す（`idle`。`last_access_time` の UPDATE） |
| `RecoveryCodeLoginSecurityIntegrationTest` | 属性の名前の読み出し、属性のバイト列の読み出し |
| `TotpEnrollmentSecurityIntegrationTest` | 属性の名前の読み出し |
| `SessionTimeoutPropertyIntegrationTest` | `max_inactive_interval` の読み出し |

これらを `identity` のテストのパッケージの 1 つの補助（`SpringSessionRows`）にまとめ、cookie の Base64 から session ID を取る処理も 1 か所にする。

### 設計の約束（Try T-1）

- **identity の合成ルートの規則**（architecture_backend.md に、identity の規則として書く）: identity の送信アダプター（`infrastructure.persistence`・`infrastructure.crypto`）は `@Repository`・`@Component` で部品探索に拾わせる。identity の合成ルート（`infrastructure.config`）の `@Bean` が返してよい型は、`identity.application.internal..`（部品探索から外したアプリケーションのサービス・イベントハンドラー・問い合わせ）、`identity.infrastructure.security..`（Spring Security の入口の部品）、`org.springframework..`（Spring Security の部品・フィルターの連なり・Flyway の設定など）、設定の値から作る部品 `AesGcmSecretCipher` に限る。`IdentityConfiguration` はアプリケーションのサービスと設定から作る部品、`SecurityConfiguration` は Spring Security の部品、`DevLoginGuardConfiguration`・`IdentityWebConfiguration` は今のまま。判定は `@Bean` のメソッドの戻り値の型が、これらのパッケージの型か `AesGcmSecretCipher` か
- **Base32**: 空白を除き大文字にし、末尾の埋め草（`=`）を除いた長さを 8 で割った余りが 1・3・6 なら拒む（RFC 4648 で現れない長さ）。最後の文字の余りのビットが 0 でなければ拒む（同じバイト列に 2 つの書き方を許さない）。埋め草を付けるなら全体の長さが 8 の倍数であること（`MY=` は拒み、`MY======` と `MY` は通る）。dev の固定の秘密の設定が不正なら、`DevTotpCodes` の構築で復号するので起動の時点で失敗する（今は黙って短い秘密になる）
- **テストの共通の設定**: 時計は `identity` のテストの共通の `@TestConfiguration`（`MutableClock` を `@Primary`）を `@Import` する。同じ組の設定を使うクラスは Spring のコンテキストを共有する。spy を使う 2 つのクラス（`AuthenticationErrorHandlingIntegrationTest`・`SessionRevalidationSecurityIntegrationTest`）は、同じ spy のフィールドの組（`UserStandingRepository`・`UserRepository`・`CompanyRepository`・`AuditRecordRepository`・`TotpCodeGenerator`・`LoginFailureRepository`）と共通の時計の `@Import` を持つ基底のクラスを継ぐ。`AuthenticationErrorHandlingIntegrationTest` は今まで本物の時計を使っていたので、`@BeforeEach` で時計を決めた時刻に置く。spy の後片付けは既定（各テストの後の `reset`）に任せ、テストの途中の `reset` は残す
- **待ちの判定**: `OverlappingTransactions` に `DataSource`（または `JdbcTemplate`）を渡す。先の操作のトランザクションの中で `pg_backend_pid()` を取り、別の接続で `pg_blocking_pids(pid)` にその pid を含む接続が現れるまで待ってから（タイムアウトで失敗）、先をコミットする。今の「500 ミリ秒の間に終わらない」の確かめも残す。判定の SQL はテストの補助の中に置き、本体のコードに置かない。4 つの呼び出し元を直す

## 設計（この Bolt の範囲）

この Bolt はドメインのモデル・データモデル・画面を変えない（リファクタリングと `Base32` の 1 つの振る舞い）。4 図のうち、状態遷移図・ER 図・画面遷移図は対象がないので省く。部品の組み立ての図だけを置く。

### 部品の組み立て（identity）

```plantuml
@startuml
package identity.infrastructure.config {
  class IdentityConfiguration <<合成ルート>> {
    アプリケーションのサービス
    AesGcmSecretCipher（設定の鍵から）
  }
  class SecurityConfiguration <<合成ルート>> {
    Spring Security の部品
    入口の部品
    フィルターの連なり
  }
}
package identity.infrastructure.crypto {
  class AesGcmSecretCipher
  class Base32 {
    +decode(String) : byte[]
    不正な長さ・余りのビット・埋め草の数を拒む（Bolt 32d）
  }
  class RandomTotpSecretIssuer <<Component（Bolt 32d で移す）>>
  class RandomRecoveryCodeGenerator <<Component（Bolt 32d で移す）>>
  class BcryptRecoveryCodeHasher <<Component>>
  class JavaOtpTotpCodeGenerator <<Component>>
}
IdentityConfiguration ..> AesGcmSecretCipher : @Bean
RandomTotpSecretIssuer ..> AesGcmSecretCipher
@enduml
```

## ステップ計画

状態の記号: `[ ]` 未着手、`[-]` 進行中、`[?]` 承認待ち、`[x]` 完了。各ステップの終わりに `check` が緑であることと、計画からの変更を設計文書に反映したこと（T-53）を確かめ、push して CI を確かめる（T-26、T-65）。コミットは pathspec で作り、そのステップのファイルだけを入れる（T-84、T-110）。複数行の書き換えはスクリプトのファイルにする（T-103）。ステップ 1 を push してからコードのステップを push する（T-109）。

- [ ] **1. 設計文書** 【承認ゲート: 設計文書】
  - architecture_backend.md: identity の合成ルートの規則（「設計の約束」）。暗号の部品の節に、部品探索で拾うことを足す。ほかのコンテキストの合成ルートは対象外と書く
  - ADR-011 の決定 1: Base32 は不正な長さ・余りのビット・埋め草の数を拒む（Bolt 32d）。改訂の経緯
  - test_strategy.md: 統合テストの節に、identity の統合テストは共通の時計の設定と spy の基底で Spring のコンテキストを共有することと、同時の待ちは PostgreSQL のロックの待ちで判定することを足す。アーキテクチャテストの表に `@Bean` の戻り値の型の見張り。アクセシビリティの節に、同じ画面を繰り返し表示するシナリオでは axe を 1 回目だけにする例外
  - release_plan.md の W5 の節: 32d の行を本計画に（承認ゲートは「ステップごと（構造）」）。557 行の局面とアプローチの 32d の文を「振る舞いの変わる Base32 の 1 つだけをインサイドアウトの TDD」に直す。後に回すものを行に書く（T-89）: 33b の行に Bolt 31d から送った残り（`instanceof` の `switch` 化、空の復号の結果）と「5 回」「15 分」の文言の重複、34 の行にロックと IA-INV-04 の明文化と「暗号の部品は crypto にある」の規則、W10 の持ち込みに 500 の応答の画面・監査の書き込みのロールバック・request ごとの SQL と接続プール・`password_hash` の読み出し・DB の往復の回数。W5 の状態
  - 完了の判定: `okf:check` ERROR 0、`documentationTest` 緑
- [ ] **2. Base32 の不正な長さ** 【承認ゲート: Red・Green、セキュリティ（dev だけ）】
  - Red: `Base32Test` に、余りが 1・3・6 の長さ（埋め草の有無、空白と小文字を含む形）、余りのビットが 0 でない最後の文字（`MY` は通り `MZ` は拒む）、埋め草の数の誤り（`MY=`）を拒むこと、正しい長さの全部（余り 0・2・4・5・7、埋め草の有無）が今どおり通ることを足す（「ないこと」に「あること」を並べる。T-106）
  - Green: `Base32.decode` で長さ・余りのビット・埋め草の数を確かめる
  - 完了の判定: `check` 緑。dev の設定の秘密（`application-dev` の 5 人分。どれも 32 文字）が今どおり読めること（`DevTotpCodesTest`・`DevTotpCredentialsDevProfileTest`）
- [ ] **3. identity の合成ルートの規則** 【承認ゲート: 構造】
  - `RandomTotpSecretIssuer`・`RandomRecoveryCodeGenerator` に `@Component` を付け、`IdentityConfiguration` の `@Bean` を外す。`ComponentScanTest`・ArchUnit・起動のテストが緑のまま
  - 見張り: identity の `infrastructure.config` の `@Bean` のメソッドの戻り値の型が「設計の約束」の型であること（ArchUnit のメソッドの規則。書きにくければ反射のテスト）。対象が存在すること（`@Bean` のメソッドが 1 つ以上あること）を先に確かめる（T-107）。違反（暗号の部品の `@Bean` を足す）を入れて落ちることを確かめる（T-112）
  - 完了の判定: `check` 緑
- [ ] **4. テストの共通の設定と補助** 【承認ゲート: 構造】
  - 前: identity の統合テストを流し、コンテキストのキャッシュの統計（DEBUG のログ）でコンテキストの数を数える（H1）
  - 骨組み: 共通の時計の設定（`identity` のテストの `@TestConfiguration`）を 2 つのクラスに当てて、コンテキストが 1 つになることを先に確かめる（T-101。ならなければ止めて諮る）
  - 共通の時計の設定で 5 つの統合テスト（`AuthenticationSecurityIntegrationTest`・`LoginLockSecurityIntegrationTest`・`RecoveryCodeLoginSecurityIntegrationTest`・`TotpEnrollmentSecurityIntegrationTest`・`SessionRevalidationSecurityIntegrationTest`）の入れ子の `FixedClock` を置き換える
  - spy の共通の基底（6 つの spy のフィールドと共通の時計の `@Import`）を作り、2 つのクラスが継ぐ。`AuthenticationErrorHandlingIntegrationTest` は `@BeforeEach` で時計を置く
  - 共通の補助: 刻みを戻す SQL（`AuthenticationSecurityIntegrationTest`・`DevLoginPrefillDevProfileTest`・`UiUsers`）と、Spring Session の表の参照（上の表の 5 つのクラス）。`ui` のパッケージからも使えるように公開の補助にする（`AuthenticationFactorFixtures` と同じ置き方）
  - 後: コンテキストの数と、`check` の件数・失敗 0 を比べる
  - 完了の判定: `check` 緑。テストの件数は整理の前と同じ
- [ ] **5. テストの確かめを強める** 【承認ゲート: Red・Green】
  - `OverlappingTransactions`: 「設計の約束」の待ちの判定。4 つの呼び出し元を直す。違反（後の操作が先の行ロックを取らない）を入れて落ちることを確かめる（H2、T-112）
  - `DevLoginPrefillDevProfileTest`（`signIn`）: コードを作ってから画面を開くまでに刻みをまたぐと揺れる。画面に出たコードを読み、それが送る前後の刻みのどちらかのコードであることを確かめてから、画面に出たコードを送る
  - 8 時間の起点の値: 時計を進めて password の段と TOTP・回復コードの段の時刻を分け、`AUTHENTICATED_AT` の値が TOTP・回復コードが通った時刻であることを確かめる（`AuthenticationSecurityIntegrationTest`・`RecoveryCodeLoginSecurityIntegrationTest`）。監査記録はそのテストの利用者で絞り、`occurred_at` の順で `PASSWORD_VERIFIED` → `LOGIN_SUCCEEDED`（回復コードは `PASSWORD_VERIFIED` → {`RECOVERY_CODE_USED`, `LOGIN_SUCCEEDED`}。同じ時刻の 2 行は集合で）を確かめる（T-113）
  - 画面の層のロックのシナリオ（`LoginUiSteps` の誤りの送信）: 2 回目以降の誤りでは axe を流さない（1 回目と、ロックの後の画面だけ）
  - 完了の判定: `check` と `uiTest` が緑
- [ ] **6. SonarQube と開発レビューと終了報告** 【承認ゲート: 終了報告】
  - SonarQube（`sonar-local:check`。テストとコードを書き換えていない間に流す。T-116）
  - 開発レビュー（プログラマー、アーキテクト（合成ルートの規則と見張り）、テスター（共通の設定と待ちの判定）、テクニカルライター（ADR・テスト戦略の書き方））
  - `bolt_32d_report.md`。release_plan の W5 の 32d の行、開発の索引。#13 に進み具合をコメントする。デモの動画は撮らない（画面の振る舞いが変わらない）

### 時間の配分と打ち切り

| # | 目安 | 打ち切り |
| :--- | :--- | :--- |
| 1 | 30 分 | — |
| 2 | 25 分 | — |
| 3 | 30 分 | 見張りが ArchUnit で書きにくければ、反射のテストにする |
| 4 | 60 分 | 骨組みでコンテキストがまとまらなければ止めて諮る。spy の基底でまとまらなければ、時計の共通化だけにして spy は既知の課題にする |
| 5 | 65 分 | 4.5 時間を超えると見えたら止めて諮る（候補: 8 時間の起点の値と監査の順を 32b に送る。32b は同じ session の上限の部品に触る） |
| 6 | 40 分（SonarQube の待ちを除く） | — |

合計 250 分（約 4 時間 10 分。上限に近い）。

## 確認ポイント（計画の承認の場でまとめて確認する。Try T-6）

| # | 確認すること | ステップ | 推奨 |
| :--- | :--- | :--- | :--- |
| 1 | **Base32 の不正な長さ**（開始準備で決めた。セキュリティ（dev だけ）） | 2 | 失敗へ倒す。不正な長さ、余りのビットが 0 でない最後の文字、埋め草の数の誤りを拒む。使うのは dev の固定の秘密の設定だけで、本番の経路（A-07 の手入力のキーは符号化だけ。復号しない）には影響しない。Bolt 31d の「失敗へ倒す範囲の変更はセキュリティの確認必須」に合わせ、ステップ 2 の承認ゲートにセキュリティ（dev だけ）を付ける |
| 2 | **identity の合成ルートの規則**（開始準備で決めた。構造） | 1・3 | クラスは今のまま分けず、規則を architecture_backend に identity の規則として書く（ほかのコンテキストは対象外）。規則から外れている暗号の部品 2 つを部品探索に移す。`@Bean` の戻り値の型の見張りを足す（許す型は「設計の約束」のパッケージと `AesGcmSecretCipher`） |
| 3 | **統合テストのコンテキスト**（開始準備で決めた。構造） | 4 | 時計の共通の設定と、spy の共通の基底の両方。spy の基底は共通の時計も持ち、spy を使う 2 つのクラスだけが継ぐ（「開始準備で確かめたこと」） |
| 4 | **範囲**（開始準備で決めた） | 全体 | ほかの 6 項目もすべて入れる。4.5 時間を超えると見えたら止めて諮る |
| 5 | **待ちの判定の置き場所と形** | 5 | `OverlappingTransactions` は PostgreSQL の統合テストだけが使うので、`pg_backend_pid()` と `pg_blocking_pids()` に頼ってよい。判定の SQL はテストの補助の中に置き、本体のコードに置かない。引数に `DataSource` を足し、4 つの呼び出し元を直す |
| 6 | **マニュアルとデモ** | — | 入れない。利用者に見える振る舞いも画面も変わらない（Base32 は dev の設定だけ）。メモリの規則（振る舞いが変わる Bolt はマニュアルを完了条件に入れる）には当たらない |
| 7 | 後の Bolt に送るものと T-89 | 1 | 33b: Bolt 31d から送った低の指摘（`instanceof` の `switch` 化、空の復号の結果を含む）、「5 回」「15 分」の文言の重複。34: `@Service` と `@Bean` の二重の定義、ログインの監査の書き込みの経路、ロックと IA-INV-04、「暗号の部品は crypto にある」の規則。W10: 500 の応答の画面、監査の書き込みのロールバック、request ごとの SQL と接続プール、`password_hash` の読み出し、DB の往復の回数。どれもステップ 1 でリリース計画の行か W10 の持ち込みに書く |
| 8 | 承認ゲート | 全体 | ステップごとに止める（W4 の締めの決定）。ステップ 2 はセキュリティ（dev だけ）、3・4 は構造の確認必須 |

## 完了条件

- [ ] `Base32.decode` が不正な長さ・余りのビット・埋め草の数を拒み、正しい長さはすべて今どおり通る。dev の設定の秘密が読める
- [ ] identity の合成ルートの規則が architecture_backend に書かれ、暗号の部品は設定から作る `AesGcmSecretCipher` を除いて部品探索で拾われ、`@Bean` の戻り値の型の見張りがある
- [ ] identity の統合テストが共通の時計の設定と spy の基底を使い、Spring のコンテキストの数が減った（前後の数を報告に書く）。テストの件数は整理の前と同じ（足したテストを除く）
- [ ] 刻みを戻す SQL と Spring Session の表の参照が、それぞれ 1 か所の補助になった
- [ ] 同時の待ちを PostgreSQL のロックの待ちで判定し、違反で落ちることを確かめた。dev の A-02 のテストが刻みの境界で揺れない。8 時間の起点は値で、監査は順で確かめる。ロックのシナリオの axe は 1 回目とロックの後だけ
- [ ] `check` と `uiTest` が緑。push して CI を確かめた。SonarQube の Quality Gate が PASS、`okf:check` ERROR 0
- [ ] 開発レビュー、終了報告。#13 に進み具合をコメントした

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-14 | 初版作成（承認待ち）。開始準備で、Bolt 31 の低の指摘、Bolt 31d・32・32c の既知の課題、Bolt 32c の Try（T-115・T-116）を調べ、human:kakimomokuri の決定（Base32 は失敗へ倒す、合成ルートは今のまま規則を書く、コンテキストは時計と spy の両方、ほかの 6 項目もすべて入れる）を反映した | anthropic/claude-opus-5-5 |
| 2026-10-14 | 承認（確認ポイント 1〜8 は推奨のまま）。`/goal Bolt32d` の下で、ステップの承認ゲートでは止めずに進め、終了報告でまとめて確かめを受ける | human:kakimomokuri |
| 2026-10-14 | 詳細整合性検証（16 件）と横断整合性検証（10 件）の指摘を反映した。`@Bean` の見張りの許す型をパッケージで書いた（入口の部品と Spring の型）、spy の基底に共通の時計を持たせ `AuthenticationErrorHandlingIntegrationTest` は時計を置く、合成ルートの規則を identity に限った、待ちの判定の形（`pg_backend_pid`・`pg_blocking_pids`、`DataSource`、4 つの呼び出し元）、「開始準備で確かめたこと」の節（spring-test 7.0.9 のソース）、Spring Session の表の参照の 5 種類の操作、Base32 の長さを測る時点と埋め草の数、監査の同じ時刻の行は集合で、後に回すものの送り先（33b・34・W10）、release_plan の 557 行と承認ゲートの書き方、テスト戦略の見張りと axe の例外、ステップ 2 のセキュリティ（dev だけ）、ステップ 5 の時間 | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [リリース計画](release_plan.md)（W5）
- [Bolt 32c 終了報告](bolt_32c_report.md)、[Bolt 32 終了報告](bolt_32_report.md)、[Bolt 31d 終了報告](bolt_31d_report.md)
- [ADR-011](../../adr/cargo-tracker/011-mfa-totp.md)（決定 1）、[ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
- [バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、[テスト戦略](../../design/cargo-tracker/test_strategy.md)
