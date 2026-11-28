---
type: Plan
title: "Bolt 34c 終了報告 - 認証・監査の経路と文書と見張り（振る舞いの変わらない直し）"
description: "W5 の 25 本目の Bolt の終了報告。ログインの監査の書き手と @Service・@Bean の役割を今の形のまま理由とともに書き、ロックと session の関係と回復コードのログインの監査の範囲を明文化した。回復コードのログインで成功の監査を書けないときの今の振る舞いを特性テストで固定した。暗号の部品・spy・@Configuration の置き場所を ArchUnit で見張り（AT-09〜11）、login_failure の列の注釈を直した。"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-17T12:48:07Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-17T12:48:07Z }
---

# Bolt 34c 終了報告 - 認証・監査の経路と文書と見張り（振る舞いの変わらない直し）

## 承認の議題（T-36。先に確かめてほしいこと）

計画は 2026-10-17 に承認を受けた（`b08072ac7`）。`/goal` の下で、ステップ 1〜5 の承認ゲートで止まらずに進めた。層・モジュールの規則には当たらず、打ち切りの条件にも当たらなかった。本体の振る舞いは変えていない（変えたのは文書・テスト・SpotBugs の除外・列の注釈）。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **止まらなかった承認ゲート**: ステップ 1（設計文書・構造）、2・3（構造）、4（スキーマ）、5（終了報告の前の直し） | 各ステップで `check` を緑にして push した。スキーマの変更は列の注釈だけのマイグレーション 1 つで、中身を統合テストで確かめた | 成果の表、`V20261017140000__comment_identity_login_failure.sql` |
| 2 | **回復コードのログインで成功の監査を書けないときの今の振る舞い（確認ポイント 7）**: 500 でログインは通らない。session は password の段のまま残り、同じ session で別のコードを送り直せて、送るたびにまた 500 になりコードが 1 つずつ使用済みになる（戻らない）。回数は戻らず、誤りとしても数えない。特性テストで固定し、IA-INV-08 と ADR-011 に書いた | 監査の表が直らないあいだ、利用者の回復コードが減っていく。振る舞いを変えるかは後の Bolt の議題（この Bolt では変えない） | `AuthenticationErrorHandlingIntegrationTest`、domain_model の IA-INV-08 |
| 3 | **計画からの変更（人に諮らずに変えた）**: (a) 確認ポイント 6 の IA-INV-03・04 の文言の列挙を事実に合わせた（session を捨てる場面に、password の再設定の完了と認証アプリの再登録を足した。ロックは含まれない、を主にした）。(b) 書き手の表の理由 `LOCKED` の行を 2 つの経路に分け、リスナーの `default` 節（ほかの失敗のイベント）を足した。(c) AT-09 に逆向きの規則（crypto の部品は一覧のどれか。足し忘れを捕まえる）を足した。(d) AT-11 の 2 本目の役割を「例外の名前が実在し続けることの見張り」に正した（広がりは 1 本目が止める）。(e) 注釈の中身を確かめるテストを `SchemaCommentIntegrationTest` に足した | どれも開発レビューの指摘で、決定の中身を変えずに事実に合わせた直しと見張りの追加 | domain_model、ADR-011、architecture_backend、`AuthenticationArchitectureTest`、`LayerArchitectureTest` |
| 4 | **SpotBugs の除外の範囲（決定 3 の帰結）**: パッケージの指定には、同じパッケージの結果の record も入る。今これで新たに黙る指摘はないが、今後 record が防御の複製を忘れても SpotBugs は知らせない | record は compact constructor で複製する規約で守ると注釈に書いた。絞るなら `<Class name="~.*(Service|EventHandler|Mails)"/>` を組み合わせる案がある（アーキテクトの要判断） | `config/spotbugs/exclude.xml` |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 34c 回（W5 の 25 本目） |
| 期間 | 2026-10-17（計画の承認の記録 `b08072ac7` から約 1 時間。uiTest と SonarQube の待ちを含む） |
| 対象 | ADR-011・ADR-012・architecture_backend・domain_model・data_model・test_strategy、`AuthenticationArchitectureTest`・`LayerArchitectureTest`・`TestPlacementArchitectureTest`、`AuthenticationErrorHandlingIntegrationTest`・`SchemaCommentIntegrationTest`、SpotBugs の除外、`login_failure` の注釈と `LoginFailure` の Javadoc |
| SP | 0（技術タスク） |
| 計画 | [Bolt 34c 計画](bolt_34c_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 文書と SpotBugs と特性テスト | 監査の書き手の表と理由、`@Service`・`@Bean` の役割、IA-INV-03・04・08、SpotBugs の除外をパッケージに、特性テスト | `8276b5aa6` |
| 2. 暗号の部品の規則（AT-09） | 名前ごとの 11 本 | `85967f2cb` |
| 3. spy と `@Configuration` の見張り（AT-10・AT-11） | `TestPlacementArchitectureTest`、`LayerArchitectureTest` の 2 本 | `acc35f867` |
| 4. `login_failure` の注釈 | 注釈だけのマイグレーションと Javadoc、注釈の中身のテスト | `e2e9e82a7` |
| 5. 開発レビューの反映 | 特性テストを強めた、逆向きの規則、AT-11 の役割、文書を事実に | `c82c7edb7` |

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `check` 2459 件、失敗 0（Bolt 34d の終わりは 2443 件。規則とテストの分だけ増えた）。`uiTest` 272 件、失敗 0（同 272 件。振る舞いは変わっていない） |
| 承認ゲートの通過 | 計画は人が承認した。ステップ 1〜5 は止めずに進めた |
| 人の変更依頼 | 0 |
| リードタイム | 約 1 時間（計画の承認から最後の直しのコミットまで）。計画の目安 3.6 時間の内 |
| 変異の確かめ | 7 つ: 暗号の部品の名前の文字列を変える 2 つ、一覧から名前を消す（逆向きの規則）、spy を基底の外に置く、`@Configuration` を合成ルートの外に置く、例外の名前を変える（2 本とも落ちる）、マイグレーションを外す。どれも控えから戻した |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check` | 緑（層の規則に例外を 1 つ足した。`ClockConfiguration`。確認ポイント 5 で人が決めた） |
| `uiTest` | 緑（件数は前後で同じ） |
| SonarQube | PASS（1 回目。ほかの Gradle の実行の後に流した。T-147。新しい指摘 0） |
| `okf:check` | ERROR 0 |

### 開発レビューの指摘と扱い

| # | 観点 | 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- | :--- | :--- |
| 1 | アーキテクト | 中・要判断 | IA-INV-03・04 の session を捨てる場面の列挙に、再設定の完了と再登録が漏れている | 事実に合わせて直した（議題 3 (a)） |
| 2 | アーキテクト・テスター | 中 | 特性テストが行き先を固定しておらず、session が password の段に残ることと送り直しの影響を示していない | 行き先を A-02 に固定し、送り直すとまたコードが使用済みになることまで固定した（議題 2） |
| 3 | テスター | 中 | 回数が戻らないことを固定していない | 先に誤った password で 1 回にしてから確かめる形にした |
| 4 | アーキテクト・テスター | 中 | AT-11 の 2 本目の役割の説明が違う、変異で確かめていない | 役割を正し、例外の名前を変える変異で確かめた |
| 5 | アーキテクト | 中・要判断 | SpotBugs のパッケージの指定に結果の record も入る | 注釈に規約を書いた（議題 4） |
| 6 | アーキテクト・テスター | 低 | 名前ごとの規則は一覧への足し忘れを捕まえない | 逆向きの規則を足した |
| 7 | アーキテクト | 低 | 書き手の表の `LOCKED` の行と `default` 節 | 直した |
| 8 | アーキテクト | 低・要判断 | `ClockConfiguration` を platform に置かない理由がない | 理由を 1 文足した（platform は MyBatis・Web の技術の部品、時刻の取り決めは業務の規則） |
| 9 | テスター | 低 | 特性テストの写し・完全修飾名・文字列の引数・`.as()`・書き換えてよいことの注記、注釈のテストの壊れやすさ | 直した |
| 10 | アーキテクト・テスター | 低 | AT-10 は spy だけを見る（`@MockitoBean` などは見ない）、文書の時制、計画と release_plan の進み具合 | Javadoc と文書に書き、直した |

## 判断と学び

- 不変条件に「〜するのは A と B」と列挙で書くと、漏れた場面がそのまま誤りになる。否定の不変条件（ロックは session を取り消さない）は、否定を主文にして列挙を補足にする
- 特性テストは「通らない」だけでなく、どの状態に留まるか（session の段）と、繰り返したときの影響（コードが減る）まで固定すると、後の議題の材料になる
- 見張りの規則は、何を捕まえ何を捕まえないか（名前の変更か、足し忘れか、例外の広がりか、例外の名前の陳腐化か）を規則ごとに書かないと、守れていると誤解する

### ふりかえり（KPT）

- Keep
  - 開始準備で 13 項目を 3 本に分け、決めるべき 4 点を先に諮ったので、計画から実装まで手戻りがなかった
  - 開発レビューのエージェントにテストを流させず、uiTest と SonarQube を順に流した（T-147。ぶつからなかった）
- Problem
  - 開始準備の調べが session を捨てる場面を一部しか見ておらず、承認を受けた文言に漏れがあった
  - 新しい規則の役割の説明を、変異で確かめる前に書いた
- Try
  - T-148: 「〜するのは〜だけ」と書く不変条件や確認ポイントは、該当するコードを全体で grep してから文言にする
  - T-149: 見張りの規則を足すときは、規則ごとに「捕まえる変更・捕まえない変更」を Javadoc に書き、捕まえる側を変異で確かめてから文書に写す

### 既知の課題（後に持ち込む）

- 回復コードのログインで成功の監査を書けないと、送り直すたびに回復コードが減る（議題 2。直すかは後の Bolt の議題）
- SpotBugs のパッケージの指定に結果の record が入る（議題 4）
- AT-10 は spy だけを見る（`@MockitoBean`・`@TestPropertySource` などは見ない）。クラスに付ける `@MockitoSpyBean(types = …)` と `integration` パッケージも範囲の外
- 34e・34f の項目（判定を通さずに呼べる形、判定の前半の写し、login の補助の共通化、controller の分割ほか）

## 次の Bolt

Bolt 34e（利用者の管理のドメインとサービス）、その後 Bolt 34f（S-20 の画面の構造）、Bolt 35（US-04 AC3・AC5、#43）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-17 | 終了報告を承認した（議題 1〜4。議題 3 の人に諮らずに変えた点を事後に承認。議題 4 は今のまま） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-17 | 初版作成（承認待ち）。`check` 2459 件・`uiTest` 272 件が緑、SonarQube PASS | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 34c 計画](bolt_34c_plan.md)、[リリース計画](release_plan.md)（W5）
- [Bolt 34d 終了報告](bolt_34d_report.md)
- [ADR-011](../../adr/cargo-tracker/011-mfa-totp.md)、[ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、[テスト戦略](../../design/cargo-tracker/test_strategy.md)
