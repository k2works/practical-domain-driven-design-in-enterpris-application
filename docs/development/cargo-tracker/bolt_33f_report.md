---
type: Plan
title: "Bolt 33f 終了報告 - 期間の表示と回復コードの型と session の URL の整理（振る舞いの変わらない直し）"
description: "W5 の 21 本目の Bolt の終了報告。期限の時刻を SessionPolicy.deadline に寄せ、期間の汎用の書式 DurationDisplay.length を足し、テンプレート 6 か所の「15 分」「8 時間」を SessionPolicy から描き、URL を SessionKeys から SessionPaths に分け（password の再設定の URL も寄せた）、回復コードの生成のポートと issueSet を NormalizedRecoveryCode で受けるようにした。振る舞いと表示は変えていない。"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-16T04:36:21Z }
---

# Bolt 33f 終了報告 - 期間の表示と回復コードの型と session の URL の整理（振る舞いの変わらない直し）

## 承認の議題（T-36。先に確かめてほしいこと）

計画は 2026-10-16 に承認を受けた（`/goal Bolt33f` の後に承認を尋ねて受けた。`803b07d2f`）。`/goal` の下で、ステップ 1〜6 の承認ゲートで止まらずに進めた。層・モジュールの規則には当たらず、計画の打ち切りの条件（ステップ 4 の後に 3.5 時間を超えていたら回復コードの型を送る）にも当たらなかった。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **止まらなかった承認ゲート**: ステップ 1（設計文書）、2〜5（構造）、6（終了報告の前の直し） | 振る舞いと表示を変えないので、既存のテストを緑のまま移した。描く前に、A-07・A-08・A-03 の期間の文言と session の警告の属性の値の統合テストを足した（今は `check` で守られていなかった）。変異の確かめは 4 つで、どれも落ちた（控えのファイルから戻した。T-131） | 「指標」 |
| 2 | **開発の途中で人が決めたこと（開発レビューの決定、2026-10-16、human:kakimomokuri）**: ADR-012 の決定 3 に書いた「URL を application 層に置けば `interfaces` の規則に例外を足さずに済む」は事実と違った（`static final String` の定数はコンパイル時に埋め込まれ、ArchUnit には見えない。合成ルートが画面の層の `PasswordResetController.URL` を参照していても規則は緑だった）。理由を「規則に見えない定数の依存の向きを、設計の約束として保つ」に正し、前例の password の再設定の URL も `SessionPaths` に寄せた | アーキテクトのレビューで見つかった。理由の誤りは、開始準備で私が選択肢に書いた文をそのまま ADR に写したことによる。範囲が少し広がった（合成ルート 2 か所と `PasswordResetController`） | ADR-012 の決定 3、`SessionPaths` |
| 3 | **計画からの変更（人に諮らずに変えた）**: A-03 の「8 時間」を、計画の `LoginController` でなく全画面に効く `SessionTimerAdvice` の `sessionMaxLifetime` で描いた | session の警告と A-03 が同じ値を使うので、出どころを 1 つにした（`LoginController` にも置くと、同じ名前の値を 2 か所で供給する）。全画面に効く `@ControllerAdvice` の `@ModelAttribute` が増えたことは、既知の課題に数えて引き継ぐ | `SessionTimerAdvice`、計画の約束と図 |
| 4 | **開発レビューで直したこと**: (a) 社内業務 Web のレイアウト（`layout/staff.html`）の印の知らせを守るテストがなかったので足し、6 か所すべてが描く仕組みを外す変異で落ちることを確かめた。(b) 設計文書に `SessionKeys` の URL と書いたままの箇所（ui_design・architecture_frontend・architecture_backend）を直した。(c) ADR-012 などの「Web の経路の関心」の「経路」が、ユビキタス言語の「経路」（経路設計）とぶつかるので「URL（遷移先）」にした | (a) は完了条件「式を外す変異で落ちる」を 6 か所で満たすため。(b)(c) は文書の事実と言葉の一致 | `RecoveryCodeLoginSecurityIntegrationTest`、設計文書 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 33f 回（W5 の 21 本目） |
| 期間 | 2026-10-16 11:54（計画の承認の記録 `803b07d2f`）〜 13:40 頃 |
| 対象 | `identity`（`SessionPolicy.deadline` と 3 か所、テンプレート 6 か所と画面・Advice の `@ModelAttribute`、`SessionPaths` と URL の参照・password の再設定の URL、回復コードの生成のポート・実装・`issueSet`・登録と再登録のサービス）、`platform.web.DurationDisplay`、ArchUnit の Javadoc、設計文書（domain_model・architecture_backend・architecture_frontend・ui_design・ADR-011・ADR-012・test_strategy・release_plan） |
| SP | 0（技術タスク） |
| GitHub | なし |
| 計画 | [Bolt 33f 計画](bolt_33f_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計文書 | domain_model・architecture_backend・ui_design・ADR-011・ADR-012・test_strategy・release_plan | `ee4e4e45` |
| 2. 期限の時刻と期間の書式 | `SessionPolicy.deadline` と 3 か所、`DurationDisplay.length`（`connection` はそれを使う） | `43d2adca` |
| 3. テンプレートの期間 | 見張りの統合テストを先に足し、6 か所を `[[${…}]]` と `th:data-lifetime-message` で描く | `c53f452e` |
| 4. URL のクラス | `SessionPaths` と `SessionPathsTest`、参照 10 ファイル | `27a020ba` |
| 5. 回復コードの型 | ポート・実装・`issueSet`・2 つのサービス（画面に渡す結果は文字列のまま）・テスト。マニュアルの文言と回復コードの例が画面と一致することを確かめた | `04131f8e` |
| 6. 品質と報告 | SonarQube、開発レビュー（3 つ）と直し（議題 2〜4）、この報告 | この報告のコミット |

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `check` 2267 件、失敗 0（33e の終わりは 2254 件）。`uiTest` 244 件、失敗 0 |
| 承認ゲートの通過 | 計画は人が承認した。ステップ 1〜6 は止めずに進め、開発レビューの後に 1 回諮った（議題 2） |
| 人の変更依頼 | 0（議題 2 は開発レビューの指摘への判断） |
| リードタイム | 約 1 時間 45 分（計画の承認 11:54 から）。計画の目安 4.5 時間の内 |
| 変異の確かめ | 4 つ: テンプレートの式を外す（直しの前に 5 本、`staff.html` のテストを足した後に 1 本）、サービスが平文の代わりに伏せ字を渡す（4 本） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check` | 緑（層・モジュールの規則の例外は足していない） |
| `uiTest` | 緑 |
| SonarQube | PASS（開発レビューの前の 1 回目は PASS。直しの後の 2 回目は、足したテストの `allMatch` の前に空を確かめていない指摘 1 件で FAIL し、`hasSize` を足して 3 回目に新しい指摘 0。新しいコードの網羅率 95.3%） |
| `okf:check` | ERROR 0 |

### 開発レビューの指摘と扱い

| # | 観点 | 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- | :--- | :--- |
| 1 | アーキテクト | 中・要判断 | ADR-012 の理由が事実と違う（定数は層の規則に見えない） | 人が決めた（議題 2）。理由を正し、password の再設定の URL も寄せた |
| 2 | アーキテクト | 中 | 認証の入口の例外の規則は、定数の参照には効いていない | `LayerArchitectureTest` の Javadoc に、守っているのはメソッドと Bean だと書いた |
| 3 | アーキテクト・テスター | 中 | A-03 の期間の描き方が計画と違い、記録がない | 計画の約束と図に反映し、`SessionTimerAdvice` の Javadoc に書いた（議題 3） |
| 4 | テスター | 中・要判断 | `staff.html` の印の知らせを守るテストがない | 社内の利用者の統合テストを足した（共通の部品にまとめる案は採らなかった。議題 4 の (a)） |
| 5 | テクニカルライター・アーキテクト | 中 | 設計文書に `SessionKeys` の URL と書いたままの箇所、ui_design の段落の食い違いと閉じていない括弧 | 直した（議題 4 の (b)） |
| 6 | テクニカルライター | 中・要判断 | 「Web の経路の関心」の「経路」がユビキタス言語とぶつかる | 「URL（遷移先）」にした（議題 4 の (c)） |
| 7 | テスター・SonarQube | 低 | `DurationDisplayTest` の主従、`SessionPolicyTest` の import と関係のテスト、期間が分で割り切れること、A-03 のテストの分離、`plainCodes` の名前、fragment が暗黙に読むモデル | 直した |
| 8 | テクニカルライター | 低 | ADR-012 の見出し、「期限」と「期間」の使い分け、`SessionPolicy` の Javadoc の `deadline` | 直した（無操作の「期限」を「期間」に） |
| 9 | アーキテクト | 低 | `new UtcInstant(x)` で包んで戻す形が 3 か所 | 決定 5 どおり残す。4 か所目が出たら、属性を `UtcInstant` で読む関数を検討する（既知の課題） |

## 判断と学び

- `static final String` の定数はコンパイル時に埋め込まれるので、ArchUnit の層の規則は定数の参照を見ない。層の規則を理由に置き場所を決めるときは、その規則が実際に見えるもの（クラスの依存）かを確かめる
- 選択肢に書いた理由を、そのまま決定の理由として ADR に写すと、確かめていない前提が記録に残る。理由は書く前に事実と照らす
- 表示を変えない直しでも、静的な文言が守られていない箇所（`check` で見ていない警告の属性、社内のレイアウト）は、描き換える前に守りを足す必要があった

### ふりかえり（KPT）

- Keep
  - 開始準備の 2 つの検証で、タグで包むと既存の統合テストが落ちること、警告が属性の値であること、`UtcInstant` に `plus` がないことを着手前に見つけた
  - 描く前に守りを足し、描く仕組みを外す変異で確かめた（T-136）
- Problem
  - ADR-012 の理由を、事実を確かめずに書いた
  - 6 か所のうち 1 か所（`staff.html`）の守りの欠けに、レビューまで気づかなかった
- Try
  - T-140: 層の規則を根拠にする理由は、規則が実際にその依存を見るか（定数の埋め込みなど）を確かめてから書く
  - T-141: 同じ部品を複数のレイアウトに写しているときは、変異の確かめを写しの数だけ行う

### 既知の課題（後に持ち込む）

- 全画面に効く `@ControllerAdvice` が `identity.interfaces.web` に 4 つ、`@ModelAttribute` が増えた（`codeDigits` 3 つ、期間 4 つ。まとめるかは後で検討）
- 印の知らせが `layout/customer.html` と `layout/staff.html` の 2 か所に写されている（共通の部品にまとめるかは後で検討）
- `new UtcInstant(x)` で包んで戻す形が 3 か所（4 か所目が出たら関数を検討）
- 画面の層の受入シナリオ「CSRF のトークンが合わず拒まれた送信でも見積依頼の一覧の案内から入力を戻せる」（Bolt 32f）が CI で 1 回だけ落ちた（33e。もう一度落ちたら調べる）
- #49（33b の「始めたときの秘密を置き換えの条件にする」を送った）

## 次の Bolt

W5 の残り（Bolt 34・35）。US-18 の振る舞いの変わらない直し（33c〜33f）はこの Bolt で終える。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-16 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 33f 計画](bolt_33f_plan.md)、[Bolt 33e 終了報告](bolt_33e_report.md)
- [リリース計画](release_plan.md)（W5）
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、[UI 設計](../../design/cargo-tracker/ui_design.md)、[テスト戦略](../../design/cargo-tracker/test_strategy.md)
- [ADR-011](../../adr/cargo-tracker/011-mfa-totp.md)、[ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
