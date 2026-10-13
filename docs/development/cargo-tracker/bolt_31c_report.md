---
type: Plan
title: "Bolt 31c 終了報告 - 回復コードによるログインと password の段の主体の型"
description: "W5 の 7 本目の Bolt の終了報告。A-02 で回復コードを 1 つ使ってログインを終えられるようにし（US-18 AC9 のログインの分）、password の段だけの主体を PasswordVerifiedUser に分けて AuthenticatedActor を 2 つの要素がそろった主体だけにした（ADR-012 の見直しの 4）。「承認して止めずに進める」の指示で承認ゲートを止めずに進めたので、止まらなかったゲートを承認の議題に置く。"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-13T08:04:39Z }
---

# Bolt 31c 終了報告 - 回復コードによるログインと password の段の主体の型

## 承認の議題（T-36。先に確かめてほしいこと）

計画は 2026-10-13 に確認ポイント 1〜11 を推奨のとおりで承認を受けた。人の指示（「承認して止めずに進める」）により、ステップ 1〜6 の承認ゲートで止まらずに進めた。途中で人に諮った決定はない（層の規則の例外は足していない）。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **止まらなかった承認ゲート**: ステップ 1（設計文書）、2（Red・Green）、3（セキュリティ・構造: 引数の解決と主体の型）、4（セキュリティ・画面: 回復コードの欄と認証）、5（セキュリティ: 画面の層）、6（終了報告） | どのステップも Red を本命のアサーションで落としてから Green にし（T-83）、終わりに `check` が緑であることを確かめて push した。失敗のイベントの対応表から回復コードの拒否を外すと監査の理由のテスト 3 件だけが落ちることを確かめた（T-88）。ほかのコンテキストの本体と `@WithAuthenticatedActor` を使う 11 ファイルに差分がない（H2） | 「ステップの結果」「品質ゲート」 |
| 2 | **計画から変えたこと**: ①回復コードの正規化に全角（NFKC）・全角スペース・長音を足した、②入力のトークンの平文は `eraseCredentials` で消すとしていたが、ProviderManager は入力に呼ばないので「request の範囲でしか持たない」に書き方を改めた、③`AuthenticatedActor` の引数の解決は `FACTOR_PASSWORD` と `FACTOR_TOTP` の両方を確かめる（計画は `FACTOR_TOTP` だけ）、④回復コードの欄の説明の文を受け付ける形に合わせた | どれも開発レビューの指摘。①IME のまま入れると黙って拒まれ、貴重な 1 つを使ったかが分からない。②の約束は検証されず効いていなかった。③は約束（2 つの要素）を実装と同じ強さにした。ADR-011・UI 設計・計画の更新履歴に書いた（T-53） | 「計画からの変更」 |
| 3 | **画面の文言**: 回復コードの欄の説明「半角の数字と a〜f の英字で 8 文字。大文字、空白、ハイフンはあってもかまいません。」、登録の直後の案内「認証アプリを登録しました。認証アプリに表示されたコードを入力して、ログインを終えてください。」。A-01 の誤りの文言は確認ポイント 5 で承認された文のまま | 説明の文は受け付ける形（16 進）に合わせた。「8 文字の英数字」だと l・o などを入れて拒まれる。A-01 の文の「どこに回復コードを入れるか」が分からない点は、33b のマニュアルで補う（テクニカルライターの指摘） | `login-totp.html`、ui_design.md |
| 4 | **後の Bolt に送るもの**（下の「既知の課題」）。とくに 33b の開始準備で決めること: `TotpEnrollmentService` の引数が `PasswordVerifiedUser`（password の段の型）になったので、2 つの要素がそろった session からの再登録（AC9）で、サービスを段に中立な値で受けるか、規則に 3 つ目の画面を足すかを諮る | アーキテクトの指摘。今は A-02・A-07 からしか呼ばないので直さず、33b の確認ポイントに置く | 「既知の課題」 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 31c 回（W5 の 7 本目。US-18 の残りの 4 本目） |
| 期間 | 2026-10-13 15:46（計画の承認の記録 `d78f063`）〜 17:20。開始準備（計画の初版 `a8ff636`）は 15:15 頃から |
| 対象 | `identity`（回復コードの正規化と確かめのサービス、A-02 の回復コードの欄と認証、`PasswordVerifiedUser` と引数の解決、監査、要素の判定）、画面の単体テストの主体、層の規則、画面の層のシナリオ、ADR-011・ADR-012、UI 設計・ドメインモデル・データモデル・非機能要件・アーキテクチャ、リリース計画 |
| SP | 0（US-18 の残りの SP は 33b で数える） |
| GitHub | [#13](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/13)（閉じない）。デモの動画を[コメント](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/13#issuecomment-6141126829)に添付した（`issue:attach-demo`）。ほかの Bolt の動画も、A-02 に回復コードの欄が入った形で撮り直した |
| 計画 | [Bolt 31c 計画](bolt_31c_plan.md) |

## 成果

### ステップの結果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計文書 | ADR-011 の決定 7、ADR-012 の見直しの 4 を決めた形に、UI 設計（A-01・A-02・A-03・A-07）、ドメインモデル（用語集、IA-INV-11）、データモデル、非機能要件（SEC-03・SEC-09）、アーキテクチャ（D-5）、リリース計画（31d の行。W5 は 14 本）。サブエージェントに任せ、差分を確かめた | `84ef211` |
| 2. 正規化と確かめのサービス | Red 5 件 → `RecoveryCode.normalize`、`RecoveryCodeVerificationService`（照合はトランザクションの外、使用と監査記録 `RECOVERY_CODE_USED` は 1 つのトランザクション）。同じコードの同時の使用は T-100 の重ね方で 1 つだけ通る | `7497faf` |
| 3. 主体の型と引数の解決 | Red 4 件 → `PasswordVerifiedUser`、その引数の解決、`AuthenticatedActor` は 2 つの要素がそろった主体だけ、A-02・A-07 と登録のサービスが `PasswordVerifiedUser` を受ける、`@WithAuthenticatedActor` に要素の権限。ArchUnit（受ける画面は A-02 と A-07 だけ、見張り付き） | `5f0fea3` |
| 4. A-02 の回復コードの欄 | Red 7 件 → 入力のトークンと provider（結果は `TotpAuthenticationToken`）、`RecoveryCodeRejectedException` と失敗のイベントの対応表、監査の理由 `INVALID_RECOVERY_CODE`、両方の欄がある送信は照合せずに拒む、成功で登録を始めた時刻を消す、登録の直後の案内、A-01 の誤りの文言 | `589edb6` |
| 5. 画面の層 | `@US-18-AC9 @demo @demo-bolt-31c/recovery`（回復コードでログインを終える） | `b5813bd` |
| 6. SonarQube・レビュー・報告 | SonarQube は初回で PASS（T-104）。4 つの観点の開発レビューの高 2 件と主な中を直し、直しの後の SonarQube の指摘 1 件を直した。レビューの直しの後にデモの動画を撮った（T-105） | `e4b1f76`、`f31afcf`、`2c48d88` |

### 作ったもの

| 層 | 部品 |
| :--- | :--- |
| ドメイン（`identity.domain.model`） | `RecoveryCode.normalize`、値 `PasswordVerifiedUser`、`AuditRecord.recoveryCodeUsed`、`AuditAction.RECOVERY_CODE_USED`、`AuthenticationRejection.INVALID_RECOVERY_CODE` |
| アプリケーション（`identity.application.internal.commandservices`） | `RecoveryCodeVerificationService`、`RecoveryCodeVerification` |
| インフラ（`identity.infrastructure.security`） | `RecoveryCodeAuthenticationToken`・`RecoveryCodeAuthenticationProvider`・`RecoveryCodeRejectedException`、`PasswordVerifiedUserArgumentResolver`、`AuthenticationFactors`、`TotpAuthenticationFilter`・`AuthenticatedActorArgumentResolver`・`AuthenticationAuditListener`・`SecurityConfiguration` の改修 |
| 画面（`identity.interfaces.web`） | A-02 の `<details>` の回復コードの欄と登録の直後の案内、A-01 の誤りの文言、`LoginController`・`TotpEnrollmentController` が `PasswordVerifiedUser` を受ける |

### デモ項目

- 画面の層のシナリオの動画（`@demo @demo-bolt-31c/recovery`）: [回復コードでログイン](../../assets/demo/bolt-31c/recovery.webm){:target="_blank"}（荷主担当者のログイン → A-02 →「認証アプリを使えない場合」を開く → 回復コード → 見積依頼の一覧）。レビューの直しの後に撮った（T-105）

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `check` 緑（新しいテストのクラス 4 つ: `RecoveryCodeVerificationServiceTest`・`IntegrationTest`・`RecoveryCodeLoginSecurityIntegrationTest`・`RecoveryCodeAuthenticationProviderTest`）。uiTest 166 件・失敗 0（回復コードでログインを終えるシナリオを含む） |
| 承認ゲートの通過 | 計画は人が承認。ステップ 1〜6 は止めずに進めた（議題 1） |
| 人の変更依頼 | 0。途中の決定 0 |
| リードタイム | 約 1 時間 35 分（計画の承認 15:46〜 17:20。計画の目安 4 時間 25 分の内） |
| 変異の確かめ | 失敗のイベントの対応表から回復コードの拒否を外すと、監査の理由を確かめる 3 件だけが落ちた。`FACTOR_TOTP` の確かめを入れる前は、password の段だけの主体に業務の引数を渡すテストが落ちていた |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check`（ModularityTest・ArchUnit・Checkstyle・SpotBugs・Spotless・documentationTest を含む） | 通過。SpotBugs の EI_EXPOSE_REP2 を `RecoveryCodeVerificationService` だけに抑止した（Bolt 31・31b と同じ理由。`exclude.xml`） |
| uiTest | 通過（166 件、失敗 0） |
| SonarQube（ローカル） | Quality Gate PASS。新しいコードのカバレッジ 96.2%、重複 0.1%、新しい指摘 0（レビューの直しで足したテストのラムダ 1 件を直した） |
| CI | 各ステップの push の後の cargo-tracker CI を確かめる（終了報告の push の後にまとめて確かめる） |
| `okf:check` | ERROR 0 |

## 仮説の結論

| # | 仮説 | 結論 | 根拠 |
| :--- | :--- | :--- | :--- |
| H1 | 回復コードを同じ入口で受け、結果を TOTP と同じ型・要素の権限にすれば、認可・session の ID の変更・8 時間の起点・成功の監査を変えずに 2 つ目の要素の手段を足せる | 支持 | セキュリティの統合テストで、回復コードで通った主体が業務の URL を開け、session の ID が変わり、8 時間の起点（`AUTHENTICATED_AT`）が置かれ、監査記録が `LOGIN_SUCCEEDED` になる。provider の単体テストで、結果が `TotpAuthenticationToken` で `FACTOR_TOTP` を持つ。業務の認可の設定と `CargoUserDetails` は変えていない。開始準備の検証で、別の結果の型にすると成功の監査が `PASSWORD_VERIFIED` になり、`toBuilder()` がないと password の要素が落ちることを見つけていた |
| H2 | 引数の解決が 2 つの要素がそろった主体だけに `AuthenticatedActor` を渡しても、ほかのコンテキストの controller は変わらない | 支持 | ほかのコンテキストの本体のコードと、`@WithAuthenticatedActor` を使う 11 ファイルに差分がない。主体の工場に 2 つの要素の権限を足すだけで緑のまま |

## 開発レビュー

プログラマー、アーキテクト、テスター（セキュリティの境界）、テクニカルライター（A-02 の回復コードの欄・案内・A-01 の誤りの文言。T-90）の観点で読んだ。ブロッカーはなかった。

| # | 観点 | 重さ | 内容 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | テスター | 高 | 監査記録の `toString` に平文がないことの確かめが空振り（`AuditRecord` は `toString` を持たない） | 監査記録の理由が空であることを確かめる形にした（平文がないことは統合テストの監査の文字列で確かめている） |
| 2 | テスター | 高 | 同時の使用で負けた側が監査記録を書かないことを確かめていない | 統合テストに `RECOVERY_CODE_USED` がちょうど 1 行、単体テストに使用が false のときは拒み監査を書かないことを足した |
| 3 | テスター | 中 | 照合がトランザクションの外にあることを確かめていない（確認ポイント 8） | 照合の間はトランザクションがなく、使用はトランザクションの中で呼ばれることを単体テストで確かめた |
| 4 | プログラマー・テスター | 中 | provider の単体テストがない（照合の途中の例外、両方の欄、結果の型、主体のない入力） | 足した |
| 5 | テスター | 中 | 入力のトークンの `eraseCredentials` は ProviderManager に呼ばれず、約束が効いていない | 書き方を「request の範囲でしか持たない。リスナーは `getCredentials()` を読まない」に改めた（議題 2 の②） |
| 6 | テスター | 中 | session に平文がないことの確かめが、失敗して消えた session を見ていて空振りしうる | ログインを終えた session に絞り、利用者のメールアドレスがある（デコードが効いている）ことと並べた（T-106） |
| 7 | テスター | 中 | 引数の解決が `FACTOR_PASSWORD` を確かめていない。「TOTP だけ」の行がない | 両方を確かめる形にし、行を足した |
| 8 | テスター | 中 | どちらの欄もない送信・空白だけの回復コードのテストがない。T-102 で 2 人目が回復コードを持たず、拒否の理由と切り替えを区別できない | 足した（理由は `INVALID_TOTP`。2 人目の理由と、2 人目のコードで通ること） |
| 9 | テクニカルライター・テスター | 中 | 全角（IME）・全角スペースで入れると黙って拒まれる。欄の説明「8 文字の英数字」が受け付ける形より広い | 正規化に NFKC・全角スペース・長音を足し、説明の文を直した（議題 2 の①④） |
| 10 | テクニカルライター | 中 | salt の問い合わせ先が実装と違う（「営業担当者」は社内の役割名） | salt を実装の文に合わせた |
| 11 | プログラマー・アーキテクト | 中 | 要素の判定が 3 か所にあり、引数の解決の間に横の依存がある | `AuthenticationFactors` にまとめた |
| 12 | アーキテクト・プログラマー・テスター | 中 | ArchUnit の除外が単純名で、ほかのコンテキストの同じ名前の画面を素通りする | 完全修飾名にした |
| 13 | アーキテクト | 中 | `TotpEnrollmentService` の引数が password の段の型に結び付き、33b の再登録で引っかかる | 33b の確認ポイント（議題 4） |
| 14 | プログラマー・アーキテクト | 中 | 登録を始めた時刻のキーを 2 つの定数で読み書きしている（Bolt 31b からの負債。31c で読む側が増えた） | 31d（定数の置き場所） |
| 15 | テクニカルライター | 中 | A-01 の誤りの文言が回復コードを入れる場所を示さない | 33b のマニュアル（承認された文は変えない） |
| 16 | テスター | 中 | 平文がログに出ないことの見張りがない | 31d（`OutputCaptureExtension`） |
| 17 | ほか（低） | 低 | 回復コードの形の知識が生成器と正規化の 2 か所、`FACTOR_TOTP`・`TotpAuthenticationToken` の名前と意味のずれ、provider の `IllegalArgumentException` の捕捉の広さ、`DataAccessException` の 500、テストの名前と置き場所、8 時間の起点の値と監査の順、3 つの統合テストの時計の設定の重複、`normalize` の戻り値の型 | テストの名前・企業 ID・1 回目の送信の確かめ・案内の動詞・`aria-describedby`・`spellcheck` は直した。ほかは 31d |

## 計画からの変更

議題 2 のとおり。計画の更新履歴にも書いた。

## 判断と学び

- 開始準備の 2 つの検証が、結果の型（成功の監査の判定と `toBuilder()`）と拒否の例外の対応表を実装の前に見つけた。Spring Security の暗黙の規則（型による分岐、例外のクラス名の完全一致）は、Bolt 31 で痛い目にあった場所で、検証の観点として効いた。
- 枠組みに任せた約束（`eraseCredentials` で平文を消す）は、呼ばれる経路を確かめないと効いているか分からない。設計の約束に「誰が呼ぶか」を書く。
- 「ないこと」を確かめるテストは、確かめる対象そのものが消えていても通る（失敗した session は捨てられる）。T-106 を計画に入れていたが、対象の選び方まではレビューまで気付かなかった。

### ふりかえり（KPT）

- Keep
  - 開始準備の 2 つの検証の指摘をすべて計画に入れてから実装し、途中で人に諮る決定が 0 だった
  - T-105 のとおり、レビューの直しの後にデモの動画を撮った
  - T-104 のとおり SonarQube をレビューの前に流し、初回で通った
- Problem
  - シェルのヒアドキュメントで長いスクリプトを渡して 1 回失敗した（T-103 の取りこぼし）
  - 新しいテストのうち 3 つ（監査の `toString`、session の平文、トランザクションの境界）が、レビューまで空振りに気付かなかった
  - CSRF のテストで、A-02 を開く前の送信の扱い（A-03 へ送る）を忘れて 1 回落とした
- Try
  - T-107: 「ないこと」を確かめるテストは、確かめる対象（session・記録）がテストの時点で存在することを先に確かめる
  - T-108: 枠組みに任せる約束（平文の消去、イベント、要素の合わせ）は、計画に「どの部品がいつ呼ぶか」を書き、呼ばれることをテストで確かめる

### 既知の課題（後に持ち込む）

- Bolt 31d（構造の直し）: 照合の 3 値（`TotpVerification`・`RecoveryCodeVerification` を一緒に）、`REJECTED_NOT_ENROLLED` の監査の理由、`FACTOR_*` の ArchUnit の空振り対策、入口と暗号のパッケージ、特性テスト（R-16）、Bolt 31 の低の指摘、SVG の組み立て・定数、登録を始めた時刻のキーの定数（レビュー 14）、回復コードの形の知識（生成器と正規化）、平文がログに出ない見張り（16）、provider の例外の捕捉の範囲、`FACTOR_TOTP` の名前（改名は session の直列化に当たるので人に諮る）、3 つの統合テストの時計の設定、スパイクの削除
- Bolt 32: 回復コードの誤りも失敗の回数に数えるか（アーキテクトの推奨は「数える」）、ロックの予告を A-01 の誤りの文言に足すか、`DataAccessException` の扱い
- Bolt 33b: `TotpEnrollmentService` の引数の型（議題 4）、回復コードによる再登録と残りが少ないときの案内（成功の後の「残り N 個」）、マニュアル 2 章と付録 B（A-02 の 2 つの道、回復コードの書き方、誤ったときの戻り方、登録の直後の案内、問い合わせ先、トラブルシューティング、A-02 のキャプチャ 2 枚）

## 次の Bolt

Bolt 31d: 構造の直し（照合の 3 値、`FACTOR_*` の ArchUnit、入口と暗号のパッケージ、特性テスト、定数、低の指摘、スパイクの削除）。開始準備では「既知の課題」の 31d の項目を確認ポイントにする。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-13 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 31c 計画](bolt_31c_plan.md)
- [Bolt 31b 終了報告](bolt_31b_report.md)、[Bolt 31 終了報告](bolt_31_report.md)
- [ADR-011](../../adr/cargo-tracker/011-mfa-totp.md)、[ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
- [UI 設計](../../design/cargo-tracker/ui_design.md)（A-01・A-02）、[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)（D-5）
