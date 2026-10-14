---
type: Plan
title: "Bolt 32e 終了報告 - 失効の後に開いていた画面へ戻す（ACC-06）"
description: "W5 の 13 本目の Bolt の終了報告。無操作 30 分と 8 時間の上限で失効した後、もう一度ログインすると GET で開いていた業務の画面に戻るようにした（A-03 の「開いていた画面に戻ります」を事実にした。Bolt 31 のレビュー 5）。失効の扱いを invalidSessionStrategy に替え、新しい session に要求を覚え、認証コードの誤りと password の段の失効では持ち越す。警告の期限の到来の案内のリンクは戻り先を開き直す。開発レビューで許す一覧の判定と覚えない場合の対のテストを強めた。送信の後の失効と入力の保持は Bolt 32f。"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-14T11:52:08Z }
---

# Bolt 32e 終了報告 - 失効の後に開いていた画面へ戻す（ACC-06）

## 承認の議題（T-36。先に確かめてほしいこと）

計画は 2026-10-14 に確認ポイント 1〜10 を推奨のとおりで承認を受けた（`13b7836`）。`/goal Bolt32e` の下で、ステップ 1〜5 の承認ゲートで止まらずに進めた。止めて諮る条件（4.5 時間を超える見込み、持ち越しが Spring Security の失敗の扱いの順序で書けない）には当たらなかった。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **止まらなかった承認ゲート**: ステップ 1（設計文書）、2（Red・Green、セキュリティ・構造: 失効の扱いの差し替え、覚える要求、持ち越し）、3（画面の層の Red・Green）、4（マニュアル）、5（レビューと報告） | サーバーは Red（覚えて戻る 7 件が落ち、覚えない対の側は通る）を確かめてから Green にした。画面は案内のリンクのシナリオの Red（2 件）を確かめてから JavaScript を変えた（T-120 で `check` を先に） | 「ステップの結果」「指標」 |
| 2 | **計画からの変更（構造）**: 戻り先の判定を `SessionTimerAdvice`（画面の層）でなく `SessionLifetimeFilter`（認証の入口）に置き、request の属性 `cargotracker.request.returnPath` で渡した。2 つの定数で持つ形の 3 組目 | 許す一覧の `RequestMatcher` は合成ルートが認証の入口に渡すもので、画面の層は `infrastructure` に依存できない（層の規則）。32b の残り時間と同じ形にし、名前の一致は単体テストで守る。33b の直しの対象に足した | `SessionLifetimeFilter`、release_plan の 33b の行 |
| 3 | **開発レビューで強めたこと（セキュリティ）**: 許す一覧の判定を文字列の比較から認可と同じ `PathPatternRequestMatcher` にした（`document%73` のような percent-encode で書類の除外をすり抜けた）。部分更新（`HX-Request`）を除いた。案内のリンクは同じ origin の path に限る | アーキテクトの指摘。オープンリダイレクトの経路はない（戻り先はサーバーが受けた request から作る）が、多層防御として足した | `SecurityConfiguration.returnablePages()`、`session-timeout.js` |
| 4 | **受け入れた振る舞いの確かめ（確認ポイント 4）**: 別の利用者が同じブラウザでログインすると前の画面の URL へ移るが、役割が違えば A-04（403）、同じ役割の他社の業務番号なら見つからない扱い（404）でデータは見えない。社内の画面を開いていた session に荷主がログインし直すと 403 になることを特性テストで確かめた。ADR-012 の「A-04」を正した | 共用のパソコンの注意をマニュアルの 2.1 と 2.4 に書いた（運用で補う）。W10 のセキュリティのふりかえりで再評価する | ADR-012、マニュアル 2.4 |
| 5 | **32f へ送ったもの**: 送信の後の失効の扱い、入力の保持、A-03 の「送信していない入力は保存されていません。」の文言（ログアウトの後に送信した内容も保存されていないと伝わるように）、案内のリンクの語「もう一度ログインする」の見直し（session が生きていると開き直すだけになる） | テクニカルライターの指摘。どれも入力の保持と一緒に見るのが自然 | release_plan の 32f の行 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 32e 回（W5 の 13 本目。US-18 の残りの 10 本目） |
| 期間 | 2026-10-14 19:37（計画の承認の記録 `13b7836`）〜 20:45 頃 |
| 対象 | `identity`（`ExpiredSessionRequestSaver`、`SecurityConfiguration`、`SessionLifetimeFilter`、認証コードの誤りの扱い、`SessionTimerAdvice`）、警告の部分と `session-timeout.js`、UI 設計、アーキテクチャ、ACC-06、ADR-012、テスト戦略、ユーザーストーリー、リリース計画、ユーザーマニュアル |
| SP | 0（US-18 の残りの SP は 33b で数える） |
| GitHub | [#13](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/13)（閉じない）。デモの動画（失効の後に開いていた画面へ戻る）は終了報告の承認の後に添付する |
| 計画 | [Bolt 32e 計画](bolt_32e_plan.md) |

## 成果

### ステップの結果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計文書 | UI 設計（A-03、画面遷移、案内のリンク）、アーキテクチャ（失効の扱い・覚える要求・持ち越し、32e と 32f の書き分け）、ACC-06、ADR-012（`invalidSessionStrategy`、利用者に結び付けない理由、forward headers）、テスト戦略、ユーザーストーリー、release_plan（32e・32f の行、18 本） | `c7358d1` |
| 2. 失効の扱いで要求を覚え、持ち越す | `ExpiredSessionRequestSaver`（新しい session を作り、GET の業務の画面を覚える。持ち越し）、`invalidSessionStrategy`、`SessionLifetimeFilter`（8 時間で覚える、password の段・登録の途中で持ち越す、戻り先の属性）、認証コードの誤りの持ち越し、`SessionTimerAdvice` の戻り先 | `9baaeff` |
| 3. 期限の到来の案内のリンク | 画面の層の Red（2 件）→ `session-timeout.js` が戻り先をリンクにする | `2e4a78c` |
| 4. 画面の層の受入シナリオとマニュアル | 失効の後の画面への復帰の 5 シナリオ（案内のリンク、session が生きている、開き直し、8 時間、延長の失敗の後の A-01）。マニュアル 2.1・2.4・2.5・付録 B。デモの動画 | `2e4a78c`、`9e8c7a4` |
| 5. 品質と報告 | 開発レビュー（4 つの観点）の直し（`2e4a78c`）、SonarQube の指摘 2 件の直し（`9e8c7a4`）、この報告 | 下の各欄 |

### 作ったもの・変えたもの

| 層 | 部品 |
| :--- | :--- |
| インフラ（認証の入口） | `ExpiredSessionRequestSaver`（新規。`InvalidSessionStrategy`、`discardAndRemember`、`invalidateCarryingSavedRequest`）、`SessionLifetimeFilter`（覚える・持ち越す・戻り先）、`SessionLifetime.RETURN_PATH_ATTRIBUTE`、`TotpAuthenticationFilter` の失敗の扱い（持ち越す） |
| 合成ルート | `SecurityConfiguration`（`invalidSessionStrategy`、`returnablePages()`） |
| 画面の層 | `SessionTimerAdvice`（`SessionTimer` に戻り先） |
| テンプレート・静的資源 | `layout/session-warning.html`（`data-return-path`、リンクの印）、`session-timeout.js`（同じ origin の path に限って案内のリンクにする）、`identity/session-expired.html`（コメント） |
| テスト | `ReturnAfterExpirySecurityIntegrationTest`（新規、16 件）、`SessionLifetimeFilterTest`（5 件を足した）、`SessionTimerSecurityIntegrationTest`、`AuthenticationSecurityIntegrationTest`（404 の比較）、`session_timeout_ui.feature`（5 シナリオを足した）、`SessionTimeoutUiSteps`、`BrowserSession.signInHere` |
| 文書 | UI 設計、architecture_backend・architecture_frontend、non_functional（ACC-06）、ADR-012、test_strategy、user_story、release_plan、マニュアル 2 章・付録 B |

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `check` 2082 件・失敗 0（32b の終わりは 2061 件。足したのは統合 16 件・単体 5 件）。uiTest 198 件・失敗 0（session の警告の機能は 14 シナリオ。32e で 5 シナリオを足した） |
| 承認ゲートの通過 | 計画は人が承認。ステップ 1〜5 は止めずに進めた（議題 1） |
| 人の変更依頼 | 0。途中の決定 0 |
| リードタイム | 約 1 時間 10 分（計画の承認 19:37〜 20:45 頃。計画の目安 4 時間 20 分の内） |
| 変異の確かめ | ①サーバーの部品の前（Green の前）は、覚えて戻る 7 件が落ち、覚えない対の側は通った。②JavaScript の前は、案内のリンクの 2 シナリオが落ちた。③32b の画面の層のステップが案内のリンクを `/session-expired` と決めていたので、32e の JavaScript で 3 シナリオが落ちた（テスターが予告し、期待を戻り先に直した） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check` | 緑（2082 件。ArchUnit の層の規則・全 URL の見張りを含む） |
| `uiTest` | 緑（198 件。axe の違反 0） |
| SonarQube | Quality Gate PASS（新しいコードのカバレッジ 96.1%、新しい指摘 0。最初の解析で出たテストの指摘 2 件（似たテスト 3 件、続けたアサーション）を直した） |
| CI | 最後のコードのコミット `9e8c7a4` の CI（run 38557658982）は check・ui・deploy-demo とも緑（デモの配備の後の DB を使うログインの確かめも通った） |
| `okf:check` | ERROR 0 |
| `manual:build` | 通る |

## 仮説の結論

| # | 仮説 | 結論 |
| :--- | :--- | :--- |
| H1 | 失効を見つけたところで GET を新しい session に覚えれば、今の TOTP の成功の扱いがそこへ戻す | 支持。無操作・8 時間・password の段の失効・認証コードの誤りの後のどれでも、`?continue` を付けて開いていた画面へ戻ることを統合テストで確かめた。query 付きの画面にも戻り、開いた後は覚えた要求が消える |
| H2 | 案内のリンクを戻り先の開き直しにすれば、ブラウザが先に気づいた場合でもサーバーが覚える | 支持。サーバーの session を古くしてから案内のリンクを押すと、A-03 を経てログインし直した後に同じ画面に戻る。延長が失効で失敗した後は A-01 へ直接移り、同じく戻る。session が生きていれば画面が開き直る |

## 開発レビュー

プログラマー、アーキテクト、テスター、テクニカルライターの 4 つの観点で行った（`git diff c7358d1`）。高は 4 件（テスター 3、テクニカルライター 1）で、すべて直した。

| # | 観点 | 重要度 | 指摘 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | テスター | 高 | 取消しで覚えないテストが、取消しの応答の Cookie を捨てていて空振りする | 応答の Cookie をブラウザと同じく辿り、覚えた要求がないことを確かめる |
| 2 | テスター | 高 | 32b の画面の層のステップが案内のリンクを `/session-expired` と決めていて、32e の JavaScript で 3 シナリオが落ちる | 期待を戻り先に直した（UI テストで 3 件落ちることを確かめてから） |
| 3 | テスター | 高 | 許す一覧の条件（社内の画面、書類のダウンロード、公開の URL）を外しても落ちるテストがない（T-117） | 社内の画面で戻る、書類のダウンロード・延長・公開の URL・8 時間の送信で覚えない、を足した |
| 4 | テクニカルライター | 高 | マニュアルの「別のブラウザやタブでログインし直したとき」は、タブでは Cookie を共有するので事実と違う | 「別のブラウザやほかのパソコン」にし、同じブラウザのタブでは戻ることを書いた |
| 5 | アーキテクト | 中 | 許す一覧を生の URI の文字列で判定していて、percent-encode で書類の除外をすり抜ける | 認可と同じ `PathPatternRequestMatcher` にし、`HX-Request` を除いた |
| 6 | アーキテクト | 中 | 文書が戻り先の判定の置き場所（実装は認証の入口）に追いついていない | architecture_backend・architecture_frontend を直し、「`/customer/**`・`/staff/**` の GET は状態を変えない」を設計の制約に書いた |
| 7 | プログラマー・アーキテクト | 中 | Javadoc が別のメソッドに付いている、要素の判定の重複、session を捨てる前提がコメントだけ | 並べ直し、`fullyAuthenticated()` を使い回し、`discardAndRemember` に捨てる処理を入れた |
| 8 | テスター・プログラマー | 中 | 別の利用者の「権限で拒まれる」を確かめていない、query 付きの戻り先と覚えた要求の消え方、送信の後の画面の否定の確かめが空振りしうる、`SessionLifetimeFilter` の単体テストがない、8 時間の送信、延長の失敗の後の A-01 | それぞれテストを足した |
| 9 | テクニカルライター | 中 | 共用のパソコンの注意の重み、「権限がありません」と出るとは限らない、「送信した直後」の言い回し、送信の後の画面のまま時間を過ぎた場合 | マニュアル 2.1・2.4・2.5・付録 B を直し、ADR-012 の「A-04」を「役割の違いは A-04、他社の業務番号は見つからない扱い」に正した |
| 10 | プログラマー・アーキテクト | 低 | 属性の名前の文字列、戻り先に context path がない、案内のリンクの探し方、JS の `/\` の守り、404 の比較で戻り先を丸ごと消す | cache に属性の名前を明示、戻り先に context path を含める、`data-session-return` の印、`new URL` で同じ origin に限る、比べる前に戻り先を確かめる |
| 11 | テクニカルライター | 中（32f へ） | A-03 の「送信していない入力は保存されていません。」が、ログアウトの後に送信した利用者に誤解される。案内のリンクの語 | 議題 5。release_plan の 32f の行 |

残り（低）は「既知の課題」に書いた。

## 計画からの変更

- 戻り先の判定は認証の入口（`SessionLifetimeFilter`）に置き、request の属性で画面の層に渡した（議題 2）
- 持ち越しは、覚えた要求がなくても新しい session を作る（古い Cookie のままの A-03 で失効の扱いを 1 回挟まない）
- 404 の本文の一致のテスト（BR-07）に戻り先（開いた URL）が入るので、比べる前に戻り先が開いた URL と同じことを確かめてから除いた
- マニュアルは 2.4・2.5・付録 B に加えて、2.1 に共用のパソコンの注意を 1 行足した
- テストのログインの段を、Cookie を消さない `BrowserSession.signInHere` に分けた

## 判断と学び

- 失効の扱いの差し替えは、Spring Security の既定（`SavedRequestAwareAuthenticationSuccessHandler`、`?continue`、`RequestCacheAwareFilter`）に乗ったので、作ったのは「どの要求を覚えるか」と「捨てる session から何を移すか」だけで済んだ。前提は開始準備で jar を確かめていた（T-114）ので、ステップ 2 で前提が崩れなかった
- 「覚えない」のテストは、覚える場合と同じ流れで対にしても、ブラウザが辿る Cookie を辿らないと空振りする（取消しの応答の Cookie）
- 前の Bolt の画面の層のステップが、今の Bolt で変える振る舞い（リンクの行き先）を固定していた。共通の部品を変えるときは、その部品を確かめている既存のステップを先に探す

### ふりかえり（KPT）

- Keep
  - 開始準備で jar の振る舞いを確かめ、設計を Referer なしの GET だけに絞ったので、オープンリダイレクトと送信の本体の問題を最初から避けられた
  - 4 つの観点のレビューで、空振りするテストと事実と違うマニュアルの文を報告の前に直した
- Problem
  - 許す一覧を最初に文字列の比較で書き、認可と違う path の解釈になっていた
  - 32b のステップがリンクの行き先を固定していることに、レビューで言われるまで気づかなかった
- Try
  - T-121: URL の判定は、認可と同じ道具（`PathPatternRequestMatcher`）で書く。文字列の `startsWith`・`contains` で path を判定しない
  - T-122: 共通の部品の振る舞いを変える前に、その部品を確かめている既存のテストとステップを検索し、変える期待を Red の一部にする

### 既知の課題（後に持ち込む）

- 覚えた要求（`DefaultSavedRequest`）には URL のほかに要求のヘッダーと Cookie も入り、Spring Session の表に置かれる（ADR-012 に書いた。署名付きの Cookie や認証のヘッダーを足すときに見直す）
- 許す一覧は「既定で含める」（`/customer/**`・`/staff/**` の下に HTML でない GET を足すと対象になる）。GET の handler を列挙して画面か除外かを確かめる見張りは、HTML でない GET を足す Bolt で足す
- 回復コードの誤りと登録の途中の 15 分の失効の持ち越しは、認証コードの誤り・password の段と同じ部品を通るが、統合テストはない
- 画面の層の 2 シナリオ（開き直し・8 時間）は統合テストとほぼ同じ。ブラウザで Cookie が替わることを示す 1 本を残して、ほかを単体テストに寄せるかは、画面の層のテストの時間が問題になったら見る
- 共用のパソコンで前の利用者の URL が見えること（受け入れた。W10 のセキュリティのふりかえりで再評価する）
- 共有の `MutableClock` と実時間の Spring Session に頼るテスト（32b・32d から引き継ぎ。並列実行を入れるときに見直す）

## 次の Bolt

Bolt 32f: 失効の時の入力の保持（ブラウザの sessionStorage、利用者を確かめて自動で戻して知らせる）と、送信の後の失効の扱い、A-03 の入力の文言、案内のリンクの語の見直し。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-14 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 32e 計画](bolt_32e_plan.md)
- [リリース計画](release_plan.md)（W5）
- [Bolt 32b 終了報告](bolt_32b_report.md)
- [UI 設計](../../design/cargo-tracker/ui_design.md)（A-03、session の警告）、[アーキテクチャ（バックエンド）](../../design/cargo-tracker/architecture_backend.md)、[非機能要件](../../design/cargo-tracker/non_functional.md)（ACC-06）
- [ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
- [ユーザーマニュアル 2 章](../../manual/02-ログインと共通の画面.md)（2.4・2.5）
