---
type: Plan
title: "Bolt 33e 終了報告 - 認証の入口と session の型と期限の整理（振る舞いの変わらない直し）"
description: "W5 の 20 本目の Bolt の終了報告。「ちょうどの時刻は期限切れ」の判定を SessionPolicy.isExpired に寄せ（テストのなかった A-08 の画面の分岐に統合テストを足した）、ほかの session を失効させるときに残す session の ID を値オブジェクト SessionId にし、回復コードの経路を認証の結果のトークンが transient の欄 SecondFactorPath で持つようにして request の属性での受け渡しをやめ、画面だけが使う属性の名前 2 つの値を固定した。振る舞いは変えていない。"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-16T01:45:41Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-16T01:45:42Z }
---

# Bolt 33e 終了報告 - 認証の入口と session の型と期限の整理（振る舞いの変わらない直し）

## 承認の議題（T-36。先に確かめてほしいこと）

計画は 2026-10-16 に承認を受けた（`/goal Bolt33e` の後に承認を尋ねて受けた。`08acc2fdc`）。`/goal` の下で、ステップ 1〜6 の承認ゲートで止まらずに進めた。層・モジュールの規則には当たらず、計画の打ち切りの条件にも当たらなかった。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **止まらなかった承認ゲート**: ステップ 1（設計文書）、2〜5（構造）、6（終了報告の前の直し） | 振る舞いを変えないので、既存のテスト（session の上限・回復コードの印・再登録・回復コードのログインの単体・統合・画面の層）を緑のまま移した。テストのなかった A-08 の画面の期限の分岐は、統合テストを先に足した（決定 5）。変異の確かめは 7 つで、どれも落ちた（控えのファイルから戻した。T-131） | 「指標」 |
| 2 | **人に諮らずに足したこと**: SpotBugs の `SE_TRANSIENT_FIELD_NOT_RESTORED`（`transient` の欄が直列化を戻しても設定されない）を、`TotpAuthenticationToken` の `path` の欄に限って抑止した（`config/spotbugs/exclude.xml`） | 指摘は決定 4（経路を session に保存しない）で意図した振る舞いそのもの。抑止はパターン・クラス・欄の 3 つで絞り、理由を書いた（既存の抑止と同じ形）。`transient` を外すと落ちるテスト（`TotpAuthenticationTokenTest`）を開発レビューで足したので、抑止が黙って死ぬことはない | `exclude.xml`、`TotpAuthenticationTokenTest` |
| 3 | **開発レビューで直したこと**: (a) `transient` を守るテストがなかった（外しても全テストが緑だった）ので、直列化して読み直すと経路が null になり、直列化に経路の型が入らないことを確かめるテストを足した。(b) 経路を渡し忘れたときに黙って TOTP の扱いにならないよう、`authenticated` で null を拒む。(c) 残す session の ID の null を失効の実装の入口で拒む。(d) `SecondFactorPath` を要素の権限の見張り（ArchUnit とソースの走査）の対象に足した | (a) は決定 4 と完了条件「`transient` の欄で持つ」をテストで担保するため（2 人のレビューが中として挙げた）。(b)〜(d) は低の指摘で、振る舞いは変わらない | `TotpAuthenticationTokenTest`、`AuthenticationArchitectureTest`、`FactorAuthorityReferenceTest` |
| 4 | **33f に送ったもの（要判断）**: 期限の時刻を求める `start.plus(期間)` の 3 か所（画面に示す時刻）、`SessionPolicy` に期限の時刻の関数を置くか判定が再び散るのを見張るか（アーキテクトのレビュー） | `isExpired` は任意の期間を受ける一般の関数に近く、`isBefore(x.plus(SessionPolicy.…))` が再び書かれても検出できない。どちらの直し方にするかは、テンプレートの期間を `SessionPolicy` から描く 33f で、表示の起点と一緒に決める | release_plan の 33f の行 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 33e 回（W5 の 20 本目） |
| 期間 | 2026-10-16 09:32（計画の承認の記録 `08acc2fdc`）〜 10:50 頃 |
| 対象 | `identity`（`SessionPolicy` と期限を判定する 4 か所、`SessionId` と `UserSessionRevoker`・実装・再登録のサービスと画面、`TotpAuthenticationToken`・`SecondFactorPath`・2 つの provider・TOTP のフィルターの成功の扱い、画面だけが使う属性の名前）、ArchUnit とソースの走査の見張り、SpotBugs の抑止、設計文書（domain_model・architecture_backend・ADR-011・ADR-012・test_strategy・release_plan）、33c・33d の報告 |
| SP | 0（技術タスク） |
| GitHub | なし |
| 計画 | [Bolt 33e 計画](bolt_33e_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計文書 | domain_model（`SessionPolicy`・`SessionId`・`TotpAuthenticationToken` の行、再登録のサービス、図）、architecture_backend（判定と経路と `transient` の理由）、ADR-012 の決定 3、ADR-011（分け方、決定 7・10）、test_strategy、release_plan（33e の行、33f の行、W5 を 23 本に）、33c・33d の報告に「→ 33f」 | `cc4e1a8d` |
| 2. 期限の判定 | A-08 の画面の期限の分岐の統合テストを先に足し、`SessionPolicy.isExpired` と `SessionPolicyTest`、4 か所を付け替え | `ec5a808e` |
| 3. session の ID | `SessionId` と `SessionIdTest`、ポート・実装（`keep.value()` と比べる）・再登録のサービスと画面・テスト | `31ca82e6` |
| 4. 認証の経路 | `SecondFactorPath`、トークンの `transient` の欄と `Builder`、2 つの provider、成功の扱い、request の属性をやめた、SpotBugs の抑止（議題 2） | `5b964801` |
| 5. 属性の名前の値 | `TotpReEnrollmentSessionKeysTest` | `5b964801` |
| 6. 品質と報告 | SonarQube、開発レビュー（3 つ）と直し、この報告 | `6db1df81`、`6159a557` |

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `check` 2254 件、失敗 0（33d の終わりは 2243 件）。`uiTest` 244 件、失敗 0 |
| 承認ゲートの通過 | 計画は人が承認した。ステップ 1〜6 は止めずに進めた |
| 人の変更依頼 | 0 |
| リードタイム | 約 1 時間 20 分（計画の承認 09:32 から）。計画の目安 4 時間の内 |
| 変異の確かめ | 7 つ: A-08 の画面の境界（1 本）、`isExpired` の境界（4 か所のテスト 12 本）、失効の比較の取り違え（1 本）、成功の扱いで経路を読まない（12 本）、`Builder` が経路を写さない（12 本）、属性の名前の値（1 本）、`transient` を外す（開発レビューの後。1 本） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check` | 緑（層・モジュールの規則の例外は足していない。SpotBugs の抑止を 1 つ足した。議題 2） |
| `uiTest` | 緑 |
| SonarQube | PASS（開発レビューの前の 1 回目は、テストのラムダの指摘 1 件で FAIL。直しの後の 2 回目は新しい指摘 0、新しいコードの網羅率 95.2%） |
| `okf:check` | ERROR 0 |

### 開発レビューの指摘と扱い

| # | 観点 | 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- | :--- | :--- |
| 1 | テスター・アーキテクト | 中 | `transient` を守るテストがない | `TotpAuthenticationTokenTest` を足し、`transient` を外す変異で落ちることを確かめた（議題 3 の (a)） |
| 2 | アーキテクト | 中 | architecture_backend の成功の扱いの説明に、経路をトークンから読むことがない | 書き足した |
| 3 | アーキテクト | 低〜中 | ADR-011・ADR-012 の改訂の経緯に 2026-10-16 の行がない | 足した |
| 4 | アーキテクト | 低 | ADR-011 の決定 7 の結果の型が 2 引数のまま | 経路の引数を書き足した |
| 5 | アーキテクト | 低 | `SecondFactorPath` が要素の権限の見張りの対象にない | ArchUnit とソースの走査に足した（議題 3 の (d)） |
| 6 | アーキテクト | 低・要判断 | `start.plus(期間)` の 3 か所と、判定が再び散るのを防ぐ形 | 33f の行に足した（議題 4） |
| 7 | セキュリティ | 低 | `authenticated` が経路の null を受ける | null を拒む（議題 3 の (b)） |
| 8 | セキュリティ | 低 | 権限の合わせの後に経路が残ることを直接確かめていない | `TotpAuthenticationTokenTest` で確かめる |
| 9 | セキュリティ | 低 | 失効の実装が残す ID の null で NPE になる | 入口で拒む（議題 3 の (c)） |
| 10 | セキュリティ・テスター | 低 | 経路は等しさに入らない | 意図として Javadoc に書き、読み直す前後で等しいことをテストで確かめる |
| 11 | テスター | 低 | 統合テストの属性の名前の写しに参照先がない、`isExpired` の `@return`、`SessionIdTest` の確かめ方、`SessionPolicyTest` の境界の名前 | 直した（`SessionPolicyTest` はパラメーター化） |
| 12 | SonarQube | 低 | テストのラムダに例外を投げうる呼び出しが 2 つ（`new SessionId` をラムダの中に書いた） | ラムダの外に出した |

## 判断と学び

- 直列化する型に欄を足すときは、新しい版が古い session を読む向きだけでなく、ローリング配備の間に古い版が新しい版の session を読む向きも考える。計画の初版はこの向きを見落としており、開始準備の詳細整合性検証で見つかった
- 意図した振る舞いに当たる静的解析の指摘を抑止するときは、抑止が黙って死なないよう、その振る舞いを確かめるテストを一緒に置く（今回は開発レビューで足した）
- 文字列を値オブジェクトに置き換えると、`equals` の相手が文字列のまま残ってもコンパイルが通り、常に偽になる。比べる箇所は変異で確かめる

### ふりかえり（KPT）

- Keep
  - 開始準備の 2 つの検証で、直列化の互換、テストのない分岐、比較の取り違え、付け替えるテストの一覧を着手前に見つけた
  - 移す前に守りのテストを足し、変異で落ちることを確かめた
- Problem
  - `transient` を決めたのに、それを守るテストを計画に入れていなかった（開発レビューで足した）
  - 値オブジェクトに置き換えたテストのデータをラムダの中で作り、SonarQube の指摘を受けた
- Try
  - T-138: 直列化の形に関わる決定（`transient`・`serialVersionUID`・型の追加）は、直列化して読み直すテストを同じステップで足す
  - T-139: テストのラムダの中では値オブジェクトを作らず、外で作ってから渡す

### 既知の課題（後に持ち込む）

- 33f の行（テンプレートの期間の数値、回復コードの生成のポートの型、ADR-012 の URL の記述と `SessionKeys` の命名、期限の時刻の `start.plus(期間)` の 3 か所と判定が散るのを防ぐ形）
- 全画面に効く `@ControllerAdvice` が `identity.interfaces.web` に 4 つ、`codeDigits` の `@ModelAttribute` が 3 つの controller に（まとめるかは後で検討）
- #49（33b の「始めたときの秘密を置き換えの条件にする」を送った）

## 次の Bolt

Bolt 33f（W5 の続き）。画面と回復コードと文書の振る舞いの変わらない直し（release_plan の 33f の行）。範囲と時間を開始準備で見直す。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-16 | 終了報告を承認した（議題 1〜4。議題 2 の人に諮らずに足した SpotBugs の抑止を事後に承認。議題 4 は 33f の開始準備で扱う） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-16 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 33e 計画](bolt_33e_plan.md)、[Bolt 33d 終了報告](bolt_33d_report.md)
- [リリース計画](release_plan.md)（W5）
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、[テスト戦略](../../design/cargo-tracker/test_strategy.md)
- [ADR-011](../../adr/cargo-tracker/011-mfa-totp.md)、[ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
