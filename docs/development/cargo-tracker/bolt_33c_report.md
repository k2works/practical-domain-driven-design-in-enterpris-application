---
type: Plan
title: "Bolt 33c 終了報告 - session の属性の名前と期間の置き場所の整理（振る舞いの変わらない直し）"
description: "W5 の 18 本目の Bolt の終了報告。認証の入口と画面に同じ値の 2 つの定数で持っていた session・request の属性の名前と A-08・A-03 の URL を application 層の SessionKeys に、ログインの期間を domain の SessionPolicy に寄せ、認証の入口の規則に例外を 1 つ足した（ふるまいを持たないことを ArchUnit で見張る）。覚えた要求は Bean にせず属性の名前だけを共有した（Bean にすると Spring Security が型で見つけて未認証の入口でも使うため）。A-01 の数値を LockPolicy から描き、T-128 の見張り、tracking のコメント、空の復号の境界のテストを足した。振る舞いは変えていない。"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-15T21:59:31Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-15T12:41:20Z }
---

# Bolt 33c 終了報告 - session の属性の名前と期間の置き場所の整理（振る舞いの変わらない直し）

## 承認の議題（T-36。先に確かめてほしいこと）

計画は 2026-10-15 に承認を受けた（`d196f41a6`。検証の指摘の反映の前に受けた承認を、決定 2 の回答とあわせて記録した）。`/goal Bolt33c` の下で、ステップ 1〜6 の承認ゲートで止まらずに進めた。計画の打ち切りの条件に 1 回当たり、止めて諮った（議題 2）。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **止まらなかった承認ゲート**: ステップ 1（設計文書）、2・3（構造）、4・5（小さな直しと見張り）、6（終了報告の前の直し） | 振る舞いを変えないので、既存のテスト（全 URL の見張り、覚えた要求に戻るテスト、画面の層のシナリオ）を緑のまま移した。値を固定するテスト（`SessionKeysTest`）を先に書いた。変異の確かめ: 空の復号の境界で 1 件、T-128 の規則で 1 件、session の見張りで 1 件が落ちた（どれも控えのファイルから戻した。T-131） | 「指標」 |
| 2 | **開発の途中で人が決めたこと（決定 7、2026-10-15、human:kakimomokuri）**: 覚えた要求の `RequestCache` は Bean にせず、属性の名前（`SessionKeys.SAVED_REQUEST`）だけを共有する | `RequestCache` の Bean を作ると、Spring Security の `RequestCacheConfigurer` が型で見つけて未認証の入口でも使い、覚える範囲が変わる（決定 4「既定には渡さない」を守れない）。計画の打ち切りの条件として止めて諮った | ADR-012 の決定 3、architecture_backend |
| 3 | **計画からの変更（人に諮らずに変えた）**: (a) Thymeleaf のリンク式は静的なクラス（`T(...)`）を参照できないので、画面の層の `SessionUrlsAdvice` がモデルで URL を渡す（全画面に効く `@ControllerAdvice` が 1 つ増えた）。(b) `SessionLifetime` は名前と期間を移して空になったので消した。(c) 例外の根拠（ふるまいを持たない）を見張る ArchUnit の規則 3 つを足した（開発レビュー） | (a) テンプレートの URL を直書きのまま残すと、完了条件「URL が `SessionKeys` だけにある」を満たせない。値は定数の record で、振る舞いは変わらない。(b) 空のクラスを残す理由がない。(c) 例外が黙って広がらないように | architecture_backend、`LayerArchitectureTest` |
| 4 | **人の判断が要るかもしれないこと（開発レビュー）**: (a) 規則の名前「認証の入口はidentityのコマンドサービスだけに依存してよい」は、ADR が名前で参照しているので変えず、Javadoc と because を実態に合わせた。(b) URL（Web の経路の関心）を application 層の `SessionKeys` に置いたことを、意図した負債として ADR-012 に書くか。(c) 期限の境界の判定（「ちょうどの時刻は期限切れ」の 4 か所）を `SessionPolicy` に寄せるか | (b) は決定 2 の (2) の範囲（名前と URL は application）で、今は書いていない。(c) は 33d の行に候補として足した（→ (b)・(c) とも 33e。Bolt 33d の開始準備の決定 3） | release_plan の 33d の行 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 33c 回（W5 の 18 本目） |
| 期間 | 2026-10-15 20:34（計画の承認の記録 `d196f41a6`）〜 21:40 頃 |
| 対象 | `identity`（`SessionKeys`・`SessionPolicy`、認証の入口・画面・再登録のサービスの参照、覚えた要求、A-01、空の復号）、ArchUnit の規則、画面の層のテストの補助、`SchemaCommentIntegrationTest`、設計文書（architecture_backend・architecture_frontend・ui_design・domain_model・data_model・test_strategy・ADR-011・ADR-012・release_plan） |
| SP | 0（技術タスク） |
| GitHub | なし。#49 に、33b で約束した「始めたときの秘密を置き換えの条件にする」を送った（開始準備の決定 2 の (6)） |
| 計画 | [Bolt 33c 計画](bolt_33c_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計文書 | architecture_backend（パッケージの図、規則の例外）、ADR-012 の決定 3・6、ADR-011、ui_design、domain_model（session の方針）、data_model、test_strategy、release_plan（33d の行、W5 を 21 本に）、33b の報告 | `318f11a`、`60057718` |
| 2. 属性の名前・URL・期間の置き場所 | `SessionKeys`・`SessionPolicy`・`session/package-info`、規則の例外、参照の付け替え（認証の入口・画面・再登録のサービス）、一致を守るテスト 4 本を消して `SessionKeysTest` に、`SessionLifetime` を消した、テンプレートは `SessionUrlsAdvice` のモデルで参照 | `ff142cd` |
| 3. 覚えた要求 | 属性の名前を `SessionKeys` で共有（決定 7） | `beecb5a` |
| 4. 小さな直し | A-01 の数値を `LockPolicy` から描く（:27 の文言のテストを先に）、空の復号の境界（本物の暗号文）、`tracking` のコメント | `beecb5a` |
| 5. T-128 の見張り | `PlaywrightText` に 5 か所を寄せ、`ui` から `Pattern.quote` を呼ばない規則 | `beecb5a` |
| 6. 品質と報告 | SonarQube、開発レビュー（2 つ）と直し、この報告 | `d111746`、`69878a9`、この報告のコミット |

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `check` 2233 件、失敗 0（33b の終わりは 2229 件）。`uiTest` 244 件、失敗 0 |
| 承認ゲートの通過 | 計画は人が承認した。ステップ 1〜6 は止めずに進め、ステップ 3 で計画の打ち切りの条件に当たって 1 回諮った |
| 人の変更依頼 | 0 |
| リードタイム | 約 1 時間 5 分（計画の承認 20:34 から）。計画の目安 4.5 時間の内 |
| 変異の確かめ | 3 つ（議題 1） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check` | 緑（ArchUnit の規則の例外は決めた 1 つだけ。見張りを 4 つ足した） |
| `uiTest` | 緑（振る舞いを変えていないので、既存のシナリオがそのまま通った） |
| `documentationTest` | 緑 |
| SonarQube | Quality Gate PASS（新しいコードのカバレッジ 95.1%、重複 0.06%、新しい指摘 0） |
| CI | `69878a9` まで push した。この報告のコミットの push で確かめる |
| `okf:check` | ERROR 0 |

## 開発レビュー

振る舞いを変えないので、プログラマーとテスターをまとめた観点と、アーキテクトの観点の 2 つで行った（`git diff d196f41a6..60057718e`）。高の指摘はなかった。振る舞いは変わっていない（名前・URL・期間は旧の定数と一字一句同じ）ことを確かめた。

| # | 観点 | 重要度 | 指摘 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | アーキテクト | 中 | 例外の根拠（ふるまいを持たない）を見張るテストがない | `application.internal.session` にメソッド・定数でないフィールド・Spring の部品を置かない規則を足した（違反で落ちることを確かめた） |
| 2 | アーキテクト | 中 | `application/internal/package-info.java` と規則の Javadoc が古い | 直した（規則の名前は ADR が参照するので変えない。議題 4） |
| 3 | アーキテクト・テスター | 中・低 | 計画に「`@Bean RequestCache`」と消した `SessionLifetime` が残る | 計画を直した |
| 4 | テスター | 中 | `SAVED_REQUEST` を文字列で固定するだけで、Spring の既定と同じかを見ていない | 既定の cache が保存する名前と照らすテストにした |
| 5 | テスター | 中 | `SchemaCommentIntegrationTest` が `tracking` を本当に見ているかを確かめていない | 結果のスキーマの集合が業務のスキーマと一致することを確かめた |
| 6 | テスター | 低 | `RecoveryLoginTest` が期待値を `SessionPolicy` から作り、言い直しになっている | 15 分を直接書いた |
| 7 | テスター | 低 | `AcceptanceStepArchitectureTest` の Javadoc が「`ui` は対象にしない」のまま、見張れない範囲（エスケープなしの連結）がある | Javadoc を直した |
| 8 | アーキテクト | 低 | because と ADR の文言のずれ（URL もある）、architecture_frontend の上限の置き場所 | そろえた |
| 9 | アーキテクト・テスター | 低 | 期限の境界の判定の 4 か所、テンプレートの「15 分」「8 時間」の直書き | 33d の行に足した |
| 10 | アーキテクト・テスター | 低 | 全画面に効く Advice が 4 つになった、`SessionKeys` の名前（URL も持つ）、画面だけの属性の名前の値のテスト | 後に回す（既知の課題） |

### SonarQube の結果

開発レビューの前と直しの後の 2 回とも、新しい指摘 0 で PASS だった。

## 判断と学び

- 型で Bean を探す仕組み（Spring Security の `RequestCacheConfigurer`）は、設定で渡さなくても Bean を作るだけで振る舞いが変わる。「既定には渡さない」という決定は、Bean を作らない形でしか守れなかった。計画の打ち切りの条件に「既存の戻り先が変わるなら止める」を書いていたので、実装の前に止められた
- テンプレートの式の制限（静的なクラスを参照できない）は、計画の段階では見えなかった。値をモデルで渡す前例（`CurrentActorAdvice`・`SessionTimerAdvice`）に倣った
- 層の規則に例外を足すときは、例外を許した根拠（ここでは「ふるまいを持たない」）を別の規則で見張る

### ふりかえり（KPT）

- Keep
  - 開始準備の 2 つの整合性検証で、RequestCache の前提の誤り・一致を守るテストの数・参照する側の漏れを着手前に見つけた
  - 計画の打ち切りの条件が働き、振る舞いを変える前に止めて諮れた
  - 値を固定するテストを先に書き、移した後も振る舞いが同じことを確かめた
- Problem
  - 決定 4 を決めるときに、Spring Security が型で Bean を探すことを確かめていなかった
  - 例外の根拠を見張る規則を、開発レビューで言われるまで足していなかった
- Try
  - T-134: フレームワークの部品を Bean にする決定の前に、その型を Bean で探す仕組みがないかを確かめる（Spring Security の `getBeanOrNull` など）
  - T-135: 層の規則に例外を足すときは、例外を許した根拠を見張る規則を同じステップで足す

### 既知の課題（後に持ち込む）

- 33d の行（TOTP の部品の整理と、期限の境界の判定・テンプレートの期間の数値。→ 期限の境界の判定・テンプレートの期間の数値は 33e）
- 全画面に効く `@ControllerAdvice` が `identity.interfaces.web` に 4 つ（まとめるかは後で検討）
- `SessionKeys` は名前だけでなく URL も持つ（命名）。画面だけが使う属性の名前 2 つに値を固定するテストがない（→ 33e）
- #49（33b の「始めたときの秘密を置き換えの条件にする」を送った）

## 次の Bolt

Bolt 33d（W5 の続き）。TOTP の部品の整理（TOTP の定数、SVG、回復コードの形、`normalize` の型、A-07 と A-08 の重複、認証の結果に経路、session の ID の値オブジェクト）と、33c の開発レビューから送った期限の境界の判定・テンプレートの期間の数値。範囲と時間を開始準備で見直す（→ 認証の結果に経路、session の ID、期限の境界、期間の数値は 33e に分けた）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-15 | 終了報告を承認した（議題 1〜4。議題 3 の人に諮らずに変えたこと 3 つを事後に承認。議題 4 の (b)・(c) は 33d の開始準備で扱う） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-16 | 議題 4 の (b)・(c)、既知の課題（画面だけが使う属性の名前 2 つの値のテスト）、次の Bolt の経路・session の ID・期限の境界・期間の数値の行き先に「→ 33e」を書き添えた（Bolt 33d の開始準備の決定 3・6。human:kakimomokuri が決定） | anthropic/claude-opus-5-5 |
| 2026-10-15 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 33c 計画](bolt_33c_plan.md)、[Bolt 33b 終了報告](bolt_33b_report.md)
- [リリース計画](release_plan.md)（W5）
- [バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、[ドメインモデル](../../design/cargo-tracker/domain_model.md)、[テスト戦略](../../design/cargo-tracker/test_strategy.md)
- [ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)、[ADR-011](../../adr/cargo-tracker/011-mfa-totp.md)
