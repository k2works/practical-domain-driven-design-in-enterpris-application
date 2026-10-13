---
type: Plan
title: "Bolt 31d 終了報告 - 認証の部品の守りの直し（照合の 3 値、暗号のパッケージ、特性テスト、スパイクの削除）"
description: "W5 の 8 本目の Bolt の終了報告。TOTP の照合の結果をドメインの値 TotpMatch にし、暗号の部品を identity.infrastructure.crypto に分けて層の規則 D-5 の認証の例外を入口だけにし、保存した要素を読めないときだけを拒否に倒すようにした。FACTOR_*・全 URL・ログの平文の見張りと Spring Security の特性テストを足し、スパイクを本体のテストとの対応を確かめてから消した。承認ゲートを止めずに進めたので、止まらなかったゲートと途中の 2 つの失敗を承認の議題に置く。"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-13T11:09:01Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-13T11:09:01Z }
---

# Bolt 31d 終了報告 - 認証の部品の守りの直し（照合の 3 値、暗号のパッケージ、特性テスト、スパイクの削除）

## 承認の議題（T-36。先に確かめてほしいこと）

計画は 2026-10-13 に確認ポイント 1〜9 を推奨のとおりで承認を受けた。人の指示（「推奨のまま承認して止めずに進める」）により、ステップ 1〜6 の承認ゲートで止まらずに進めた。止めて諮る条件（対応表に当てはまらないスパイクのテスト、層の規則の新しい例外）には当たらなかった。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **止まらなかった承認ゲート**: ステップ 1（設計文書）、2（Red・Green）、3（セキュリティ・構造: 失敗へ倒す範囲、パッケージの移動、新しい `package-info.java`）、4（セキュリティ: 見張り）、5（ファイルの削除: スパイク）、6（終了報告） | 振る舞いの変わる 2 つは Red を本命のアサーションで落としてから Green にした（T-83）。構造の直しは守りのテストを先に足した。見張りは違反を入れて落ちることを確かめた（暗号の部品から入口への参照、業務の URL の公開、`FACTOR_` の 3 つの書き方（二重引用符・単引用符・定数の参照））。スパイクは 34 本すべての対応先を確かめてから消した（下の対応表） | 「ステップの結果」「スパイクとの対応表」 |
| 2 | **途中の失敗 ①: 改名だけの中間のコミット**。ステップ 3 で `git mv` した改名が index に入ったまま、ステップ 1 の設計文書のコミット（`57b46be`）に混ざった。`57b46be` と `54b4c8d` は、移した 13 のファイルのディレクトリ（`crypto/`）とパッケージの宣言（`security`）が食い違う。`8af3b8b` で直った | 共有の develop に push 済みなので履歴は書き換えない。再発の防ぎとして、Checkstyle に `PackageDeclaration`（宣言とディレクトリの一致）を足した。以後のコミットは pathspec（`git commit -- <paths>`）で作った。T-110 にする | 「判断と学び」 |
| 3 | **途中の失敗 ②: CI の OutOfMemoryError**。ログの見張りで、大きな統合テストのクラスに `OutputCaptureExtension` をクラス単位で付けたため、クラス全体の出力がメモリに溜まり、`6705894` の CI の check が落ちた（手元では通った） | 見張りたい操作の間だけロガーの出力を取り込む `LogCapture` に置き換えた。直した後の CI は「品質ゲート」の欄 | 「品質ゲート」 |
| 4 | **確認ポイント 5 の続き**: `UnreadableCredentialException` は承認どおり `IllegalArgumentException` を継ぐ。アーキテクトは、どこかに `catch (IllegalArgumentException)` が書かれると狭めた範囲が黙って元に戻るので `RuntimeException` を継ぐ形を勧めた | 承認された確認ポイントを変えることになるので、この Bolt では変えなかった。代わりにテストで両側を型で固定した（実装の誤りの側は `isExactlyInstanceOf(IllegalArgumentException)`）。Bolt 32 で認証の入口に触るときに、親の型を `RuntimeException` に替えるかを確認ポイントにすることを勧める | `UnreadableCredentialException`、ADR-011 の決定 8 |
| 5 | **後の Bolt に送るもの**（下の「既知の課題」） | 計画の確認ポイント 8 と、開発レビューの中・低のうちこの Bolt で直さなかったもの | 「既知の課題」 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 31d 回（W5 の 8 本目。US-18 の残りの 5 本目） |
| 期間 | 2026-10-13 18:24（計画の承認の記録 `3340bd7`）〜 19:50。開始準備（計画の初版 `f942cb5`）は 17:50 頃から |
| 対象 | `identity`（照合の結果、確かめのサービス、監査の理由、読めない要素の例外、暗号の部品のパッケージ、層の規則）、ArchUnit・Checkstyle、セキュリティの統合テスト・特性テスト・見張り、スパイク、ADR-011・ADR-012、ドメインモデル・データモデル・アーキテクチャ、リリース計画 |
| SP | 0（US-18 の残りの SP は 33b で数える） |
| GitHub | [#13](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/13)（閉じない）。画面を変えないのでデモの動画は撮らない |
| 計画 | [Bolt 31d 計画](bolt_31d_plan.md) |

## 成果

### ステップの結果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計文書 | ADR-011 の決定 8、ADR-012 の決定 6（D-5 の例外を入口だけに）、アーキテクチャ・ドメインモデル（用語集）・データモデル、release_plan の 31d・32・33b の行。サブエージェントに任せ、push してからコードを push した（T-109） | `57b46be`（議題 2） |
| 2. 照合の結果 | Red 3 件 → `TotpMatch`（sealed interface と入れ子の record）、`TotpCredential#match`、確かめのサービスは結果を写すだけ（拒否のときの生成の呼び出しは 6 回から 3 回）、監査の理由 `TOTP_NOT_ENROLLED` | `54b4c8d` |
| 3. 読めない要素と暗号のパッケージ | Red 9 件 → `UnreadableCredentialException`（ドメイン）を暗号の部品が投げ、2 つの provider と登録のサービスはこれだけを捕まえる。暗号の部品 6 つとテストを `identity.infrastructure.crypto` に移した。ArchUnit を「crypto は security に依存しない」とライブラリの閉じ込めを crypto に狭める形に置き換えた（Red 8 件） | `8af3b8b` |
| 4. 守りのテスト | `FACTOR_*` の参照の見張り、全 URL の見張り（ハンドラーの一覧の GET・POST）、ログの平文の見張り、Spring Security の特性テスト 3 つ | `6705894` |
| 5. スパイクを消す | 34 本の対応を確かめ、`spikes/ts-01-totp/` を消した。ADR-011 のリンクは消す前のコミットに固定した | `27f8c1f` |
| 6. SonarQube・レビュー・報告 | SonarQube の新しい指摘を直した（2 回）。3 つの観点の開発レビューの高・主な中を直した | `e18ed21`、`4c53bb1`、`d9056d6`、`81ec938` |

### スパイクとの対応表（確認ポイント 7）

分け方: (a) 本体のテストに対応する、(b) 後の Bolt の計画に対応する、(c) ADR の記録として残す、(d) 本体に足した。出典は [`670589409f` のスパイク](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/tree/670589409f/spikes/ts-01-totp)。本体のテストのクラス名は `AuthenticationSecurityIntegrationTest` を「認証」、`RecoveryCodeLoginSecurityIntegrationTest` を「回復コード」と略す。

| スパイクのテスト | 分け方 | 対応先 |
| :--- | :--- | :--- |
| `MfaLoginTest` 未認証で業務の画面を開くとログインの画面へ導く | (a) | 認証「未認証で荷主の画面を開くとログインへ移り業務データを返さない」 |
| passwordだけでは業務の画面を開けずTOTPの画面へ導く | (a) | 認証「passwordだけでは業務の画面を開けずA02へ導かれる」、全 URL の見張り |
| passwordと正しいTOTPで両方の要素がそろい業務の画面を開ける | (a) | 認証「荷主担当者はpasswordの後にTOTPを入れると荷主のホームへ移り段ごとにsessionIDが変わる」 |
| 誤ったTOTPでは業務の画面を開けない | (a) | 認証「誤ったTOTPではpasswordの要素を捨ててA01に戻る」 |
| 同じ時間の窓のコードは一度しか使えない | (a) | 認証「一度通ったコードは同じ刻みの間にもう一度は使えない」 |
| passwordを経ずにTOTPだけを送っても認証されない | (a) | 認証「passwordの段を経ずにTOTPを送っても通らず認証コードの誤りの文言も出さない」 |
| passwordとTOTPの失敗が5回続くと正しいpasswordでもロックされ15分後に解ける | (b) | Bolt 32（ADR-011 のコンプライアンスの節「5 回の失敗のロックの境界」） |
| 回復コードはTOTPの代わりに一度だけ使える | (a) | 回復コード「使った回復コードはもう一度使えず…」 |
| 本人確認から8時間を過ぎたsessionは業務の画面を開けず再認証を求める | (a) | 認証「発行から8時間ちょうどで再認証の案内へ移り業務データを返さない」「最長8時間はTOTPが初めて通った時刻から数える」 |
| 本人確認から7時間59分59秒では業務の画面を開ける | (a) | 認証「最長8時間はTOTPの時刻から数えて1秒前は使える」 |
| 本人確認から8時間を過ぎたsessionではTOTPを送っても延長されずログインの画面へ導く | (a) | 認証「期限切れのsessionにTOTPを送っても延びない」 |
| 本人確認済みのsessionでTOTPを送り直しても本人確認の時刻は変わらない | (a) | 認証「有効期間の中でTOTPを送り直しても8時間の起点は延びない」 |
| TOTPで本人確認するとsessionのIDが変わる | (a) | 認証「荷主担当者はpasswordの後にTOTPを入れると…段ごとにsessionIDが変わる」 |
| 失敗が4回ではロックされない | (b) | Bolt 32 |
| 失敗が5回続くと14分59秒まではロックされロックの理由で拒否される | (b) | Bolt 32 |
| 成功すると失敗の回数は数え直される | (b) | Bolt 32 |
| passwordを確かめた後にロックされたら正しいTOTPも拒否される | (b) | Bolt 32 |
| 別の利用者のpasswordを送っても先の利用者のTOTPの要素は引き継がれない | (a) | 認証「別の利用者のpasswordを送ると先の利用者のTOTPの要素は引き継がれない」 |
| `DevLoginPrefillTest` ログインの画面にメールアドレスとpasswordが入っている | (a) | `DevLoginPrefillDevProfileTest`「devではログインの画面にメールアドレスとpasswordが入っている」 |
| 認証コードの画面にその時点のコードが入っている | (a) | `DevLoginPrefillDevProfileTest`「営業担当者の開発用の利用者を選ぶとA02が入力済みで…」 |
| 認証コードの画面にコードは入らない | (a) | `DevLoginPrefillDefaultProfileTest`「既定の設定では入力済みの部品がない」 |
| ログインの画面は空で出る | (a) | `DevLoginPrefillDefaultProfileTest`「既定の設定ではログインの画面のメールアドレスとpasswordが空」 |
| 開発環境のプロファイルでないのに入力済みの設定があると起動しない | (a) | `DevLoginGuardTest`「devの外で設定があれば起動させない」ほか |
| `PasswordStorageTest` 既定の委譲する符号化はbcryptで保存し照合できる | (a) | `BcryptRecoveryCodeHasherTest`「ハッシュはbcryptで平文を含まない」「同じ平文でも塩で毎回違うハッシュになり両方に一致する」 |
| Argon2idはBouncy_Castleがないと符号化できない | (c) | ADR-011 の決定 2 の表「password の保存」 |
| 既定のbcryptの強さは10 | (d) | 認証「本体のpasswordの符号化は委譲する形のbcryptで強さ10」（本体の部品で確かめる形で足した） |
| `SessionTimeoutTest` sessionの無操作の期限は30分 | (a) | 「無操作30分で再認証の案内へ移る」「設定した無操作の期限がsessionに入る」 |
| `SpikeSetupTest` Spring_Security_7系が入っている | (c) | ADR-011 の背景（Spring Boot 4.1.1・Spring Security 7.1.1）、技術スタック |
| `TotpCandidatesTest` 候補a_JDKのHMACだけの実装がRFC6238の試験ベクトルを満たす | (c) | ADR-011 の決定 1 の比較の表（採らなかった候補） |
| 候補b_java_otpがRFC6238の試験ベクトルを満たす | (a) | `JavaOtpTotpCodeGeneratorTest`「八桁のコードはRFC6238の試験ベクトルと一致する」 |
| 候補c_samstevensがRFC6238の試験ベクトルを満たす | (c) | ADR-011 の決定 1 の比較の表（採らなかった候補） |
| 候補a_前後1つの窓のコードを受け付けそれより外と誤りは受け付けない | (a) | `TotpCredentialTest`「前後1つの刻みのコードを受け付けそれより外は受け付けない」 |
| 候補a_刻みの開始と終了の時点で前後1つの窓だけを受け付ける | (a) | `TotpCredentialTest`「時刻の刻みは30秒ごとの番号」（刻みの境界）と窓のテスト |
| 候補b_java_otpは6桁で先頭の0を保ちRFC6238の全試験ベクトルの下6桁と一致する | (a) | `JavaOtpTotpCodeGeneratorTest`「六桁のコードはRFC6238の試験ベクトルの下6桁と一致し先頭の0を保つ」 |

計 34 本: (a) 24、(b) 5、(c) 4、(d) 1。どれにも当てはまらないものはなかった。

### 作ったもの・変えたもの

| 層 | 部品 |
| :--- | :--- |
| ドメイン | `TotpMatch`（値）、`TotpCredential#match`、`UnreadableCredentialException`、`AuthenticationRejection.TOTP_NOT_ENROLLED`、ポートの `@throws` |
| アプリケーション | `TotpVerificationService`（結果を写すだけ）、`TotpEnrollmentService`（読めない要素だけを捕まえる） |
| インフラ（`identity.infrastructure.crypto`） | 暗号の部品 6 つ（`security` から移した）、`package-info.java` |
| インフラ（`identity.infrastructure.security`） | 2 つの provider の捕まえ方、`AuthenticationAuditListener` の理由の写し、`TotpRejectedException` は通った結果を持たない、`package-info.java` の説明 |
| テスト・規則 | `FactorAuthorityReferenceTest`、`SpringSecurityCharacterizationTest`、`TotpAuthenticationProviderTest`、`LogCapture`、全 URL の見張り、`AuthenticationArchitectureTest` の置き換え、Checkstyle の `PackageDeclaration` |
| 削除 | `spikes/ts-01-totp/` |

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `check` 1924 件・失敗 0（新しいテストのクラス 4 つと `LogCapture`）。画面を変えていないので uiTest は CI で確かめる |
| 承認ゲートの通過 | 計画は人が承認。ステップ 1〜6 は止めずに進めた（議題 1） |
| 人の変更依頼 | 0。途中の決定 0 |
| リードタイム | 約 1 時間 25 分（計画の承認 18:24〜 19:50。計画の目安 4 時間 30 分の内） |
| 変異の確かめ | 暗号の部品から入口への参照、業務の URL の公開、`FACTOR_` の 3 つの書き方を一時的に入れ、それぞれの見張りだけが落ちることを確かめた |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check`（ModularityTest・ArchUnit・Checkstyle・SpotBugs・Spotless・documentationTest を含む） | 通過 |
| SonarQube（ローカル） | Quality Gate PASS。新しいコードのカバレッジ 96.0%、重複 0.1%、新しい指摘 0（途中で 2 回、テストの書き方と構築子の検証の位置を直した） |
| CI | `6705894` は OutOfMemoryError で check が落ちた（議題 3）。`e18ed21` は check が通過。直した後の最後の push（`81ec938`、run 38384844908）で check・ui・デモ環境への配備が通過 |
| `okf:check` | ERROR 0 |

## 仮説の結論

| # | 仮説 | 結論 | 根拠 |
| :--- | :--- | :--- | :--- |
| H1 | 照合の結果をドメインの値にすれば、確かめのサービスは結果を写すだけになり、資格を作り直す必要がなくなる | 支持 | `wasAlreadyUsed` が消え、確かめのサービスは網羅的な `switch` だけになった。拒否のときの生成の呼び出しは 3 回（テストで固定）。副作用として、刻み 0 の窓で使った刻みと一致した場合が `Mismatch` から `Reused` になる（1970-01-01 の最初の 30 秒だけで、運用では起きない） |
| H2 | 暗号の部品を別のパッケージに分ければ、D-5 の例外をパッケージの規則のまま入口だけに狭められ、クラス名の一覧の規則がいらなくなる | 支持 | 足した規則は「crypto は security に依存しない」だけで、application への依存は既存の規則（合成ルートと入口の外の infrastructure）がかかる。クラス名の一覧の規則を消した。違反を入れて落ちることを確かめた |

## 開発レビュー

プログラマー、アーキテクト、テスター（守りのテストの空振り）の観点で読んだ。画面を変えていないので、テクニカルライターの観点は設計文書の確かめ（ステップ 1）に留めた。

| # | 観点 | 重さ | 内容 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 3 人 | 高 | `FACTOR_*` の見張りが、本来の目的の定数の参照（`TotpAuthenticationToken.FACTOR_TOTP`）・単引用符（SpEL）・テンプレートを見落とす | 語の正規表現（`FACTOR_[A-Z]`・`FactorGrantedAuthority`・`TotpAuthenticationToken`）にし、テンプレートも走査した。定数だけを参照するファイル（`LoginSuccessHandler`）が拾えることを確かめ、3 つの書き方で落ちることを確かめた |
| 2 | アーキテクト | 高 | CI の OutOfMemoryError（議題 3） | `LogCapture` に置き換えた |
| 3 | テスター | 高 | 特性テスト ② が空振りする（フィルターが何もしなくても通る） | 結果に `FACTOR_TOTP` があること、request の属性に文脈があること、session の置き場所を明示したときとの対照で確かめる形にした |
| 4 | テスター | 高 | 実装の誤りの側のテストが `isInstanceOf(IllegalArgumentException)` で、分類を誤っても落ちない | `isExactlyInstanceOf` にし、読めない側は `UnreadableCredentialException` に狭めた。登録の開始の処理の catch も固定した |
| 5 | アーキテクト | 中 | ポートの `@throws` の漏れ | `TotpCodeGenerator`・`RecoveryCodeHasher`・`TotpCredential#match` に書いた |
| 6 | アーキテクト | 中 | 中間のコミットの食い違い（議題 2） | Checkstyle の `PackageDeclaration`、pathspec のコミット、T-110 |
| 7 | アーキテクト | 中 | `UnreadableCredentialException` の親の型（議題 4） | Bolt 32 の確認ポイントに送る |
| 8 | プログラマー・アーキテクト | 中 | 届かない `ACCEPTED` を監査の理由 `INVALID_TOTP` に写す | `TotpRejectedException` は通った結果を拒み、リスナーの枝は実装の誤り（`IllegalStateException`）にした |
| 9 | プログラマー | 中 | ハッシュの部品の catch が広く、平文の null（実装の誤り）まで読めない要素に包む | 平文の null を先に弾いた |
| 10 | テスター | 中 | ログの見張りの秘密の確認が、秘密を復号しない場面で空振りする。Base32 の形を見ていない | 秘密を実際に復号する場面（正しい秘密で誤ったコード）の見張りを足し、ASCII と Base32 の両方を見た |
| 11 | テスター | 中 | bcrypt の強さ 10 は枠組みの組み立てを見ていて本体の部品を見ていない | 本体の `PasswordEncoder` の bean で確かめるテストを足した（対応表の (d)） |
| 12 | テスター | 中 | 「実装の誤りは 500」を端から端まで確かめる統合テストがない | Bolt 32（`DataAccessException` の扱いと一緒に、入口の例外の経路を確かめる） |
| 13 | ほか | 低 | Javadoc の最初の文、変数名、例外を `final` に、ADR の ArchUnit の範囲の書き方、`Matched` が `Reused` に勝つ優先順位のテスト | 直した |
| 14 | ほか | 低 | 規則の重複、全 URL の見張りのパスの変数の値と範囲（関数型のルーティング）、`TotpEnrollmentService` の `instanceof` を `switch` に、provider の単体テストの拒否の 3 値、監査のリスナーの単体テスト、`{bcrypt}` で中身が壊れたハッシュは誤りとして扱われる、空の復号の結果 | 32・33b（既知の課題） |

## 計画からの変更

- 振る舞いの変更は計画の 2 つに加え、H1 の副作用（刻み 0 の窓の再利用の判定）がある。運用では起きない
- 確認ポイント 7 の (d)（bcrypt の強さ 10）は、初めは既存のテストで足りると判断したが、レビューで本体の部品を見ていないと指摘され、テストを足した
- Checkstyle に `PackageDeclaration` を足した（計画になかった。議題 2 の再発の防ぎ）
- 開発レビューの直しで、拒否の例外と監査のリスナーの振る舞いを狭めた（届かない枝を実装の誤りにした。利用者への応答は変わらない）

## 判断と学び

- `git mv` は index に即座に入る。並行して設計文書をコミットするときに pathspec を付けないと、ステージ済みの改名が混ざる。T-109（設計文書を先に push）は守ったが、同じ作業ツリーの index は共有されていた。
- クラス単位の `OutputCaptureExtension` は、大きな統合テストのクラスでは出力をすべて溜める。手元では通っても CI のメモリで落ちる。見張りたい操作の間だけ取り込む。
- 見張りは「何を見落とすために足したか」（今回は定数の埋め込み）をそのまま違反として入れて確かめないと、別の書き方だけを確かめて満足してしまう。T-88 の違反の選び方を計画に書く。

### ふりかえり（KPT）

- Keep
  - 開始準備で骨組みを置いて層の規則を流し（T-101）、パッケージの移動で新しい例外が要らないことを先に確かめた
  - スパイクの 34 本を 1 本ずつ対応させてから消した
  - 見張りは 3 種類の違反を入れて落ちることを確かめた
- Problem
  - ステップ 1 のコミットに `git mv` の改名が混ざり、ディレクトリと宣言の食い違う中間のコミットが 2 つできた
  - クラス単位の出力の取り込みで CI が OutOfMemoryError で落ちた
  - `FACTOR_*` の見張りで、足した理由（定数の埋め込み）の書き方を違反として試さなかった
- Try
  - T-110: コミットは pathspec（`git commit -- <paths>`）で作る。`git mv` を使う作業と設計文書のコミットを並行させない
  - T-111: テストの出力の取り込みは、見張りたい操作の間だけにする（クラス単位の `OutputCaptureExtension` を大きなクラスに付けない）
  - T-112: 見張りの違反は、見張りを足した理由になった書き方そのものを入れて確かめる

### 既知の課題（後に持ち込む）

- Bolt 32: `UnreadableCredentialException` の親の型（議題 4）、「実装の誤りは 500」の端から端までの統合テスト、`DataAccessException` の扱い、Bolt 31 の低の指摘、3 つの統合テストの時計の設定、回復コードの誤りも数えるか、8 時間の起点の値と監査の順のテスト、provider の単体テストの拒否の 3 値と監査のリスナーの単体テスト、`{bcrypt}` で中身が壊れたハッシュを読めない要素として扱うか
- Bolt 33b: 桁数・周期・アルゴリズムの定数、SVG の組み立て、登録を始めた時刻のキーの定数、回復コードの形の知識の 2 か所、`TotpEnrollmentService` の引数の型と `instanceof` の `switch` 化、`normalize` の戻り値の型、空の復号の結果
- 全 URL の見張りの範囲（関数型のルーティングや actuator を足すとき）、「暗号の部品は crypto にある」の規則を名前ごとに分けるか

## 次の Bolt

Bolt 32: US-18 AC3 のロック（5 回・15 分）と ADR-012 の見直しの 3・5。開始準備では、この報告の「既知の課題」の 32 の項目と、範囲が重いので分けるかを確認ポイントにする。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-13 | 終了報告を承認した（議題 1〜5 を承認。議題 4 の親の型は Bolt 32 の確認ポイントにする）。T-110〜T-112 を足した | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-13 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 31d 計画](bolt_31d_plan.md)
- [Bolt 31c 終了報告](bolt_31c_report.md)、[Bolt 13 終了報告](bolt_13_report.md)
- [ADR-011](../../adr/cargo-tracker/011-mfa-totp.md)、[ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
- [バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)（D-5）
