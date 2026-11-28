---
type: Plan
title: "Bolt 16 終了報告 - デモ環境への CI からの配備"
description: "16 回目の Bolt の終了報告。develop の cargo-tracker CI の check・ui が緑になった後に deploy-demo ジョブで Heroku のデモ環境へ自動で配備する仕組み、Environment demo と API キー、3 視点の運用レビューと対応、途中で起きた AI の誤り（キーの登録のコマンドの崩れ、Accept のヘッダー、キーの更新のタスクの再帰）と対処、仮説 H1〜H3 の結論、ふりかえりをまとめる。"
tags: [development,bolt-report,operation]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-07T02:55:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-07T02:53:59Z }
---

# Bolt 16 終了報告 - デモ環境への CI からの配備

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 根拠 | 却下・修正のときに戻す箇所 |
| :--- | :--- | :--- | :--- |
| 1 | ADR-013 の改訂（CI からの自動配備を主にし、手元の `deploy:demo` を CI が使えないときの手段にする。キーの境界とワークフローのレビュー、期限 90 日）と ADR-008 の注記 | 計画の確認ポイント 5、D-57・D-58 は承認済み。改訂の中身を確かめてほしい | ADR-013 の「ステータス」の改訂の注記と決定の表 |
| 2 | 計画から変えたこと: ステップ 2 を push せずにステップ 3 の後で push した、スモークを自分の release を待つ形にした、配備をワークフローの取り消しの外に置いた、最新の配備の対象でなければ飛ばす、失敗したら直前の release に戻す | レビュー（D-59）と、ステップ 2 で見つけた問題 | `.github/workflows/cargo-tracker-ci.yml` の `deploy-demo` |
| 3 | AI の誤りで、90 日のキーが 7 つ作られた（`deploy:demo:ci-key` の再帰）。AI が止め、最も新しい 1 つを残して人が失効させた | 下の「見つけて直した問題」 | — |
| 4 | Heroku のアカウントに残る CI のキーは 1 件（2026-10-07 作成、期限 2027-01-05）。更新の Issue [#40](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/40)（期日 2026-12-22） | D-57 | — |
| 5 | Try T-47〜T-49（下の KPT） | ふりかえり | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 16 回 |
| 期間 | 2026-10-07 09:54 JST（計画の初版）〜 11:55（本報告の初版）。計画の承認は 09:57 |
| 対象 | 技術タスク（SP 0）。[#39 [技術] デモ環境への CI からの配備](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/39) |
| 計画 | [Bolt 16 計画](bolt_16_plan.md) |
| ゴール | develop にアプリの変更を push すると、check・ui が緑になった後に `deploy-demo` がデモ環境へ配備し、`DEMO_REVISION` が push したコミットになり、ログインの画面が 200 を返す。キーは人だけが扱い、develop の外からは使えない |
| 環境 | <https://cargo-tracker-mono-demo-883bf0b92807.herokuapp.com/>（release v19、`DEMO_REVISION` は `d49af974`） |

## 成果

| ステップ | 状態 | コミット | 誤りの検出と修正（検出の手段） |
| :--- | :--- | :--- | :--- |
| 1. ADR-013・手順書・#39 | 完了 | `595d2b3b` | — |
| 2. `deploy-demo` と actionlint | 完了 | `1ce566ef` | 古い dyno の 200 でスモークが通ってしまう（設計の見直し）→ 最新の release の dyno を待つ。push すると Environment が規則なしで自動で作られる（設計の見直し）→ push をステップ 3 の後に |
| 3. Environment・secret・最初の配備（外部連携・セキュリティの承認ゲート） | 完了 | — | 人が貼ったキーの登録のコマンドが端末の折り返しで崩れ、空の secret が登録された（CI の `Password required`）。2 回目も崩れた（貼った出力）→ AI がスクラッチに置いた短いスクリプトで登録 |
| 4. develop の外の確かめと手順書 | 完了 | `70bb65ea` | — |
| 5. 運用レビューと終了報告 | 本報告 | `82adf2ef`、`213e43a6`、`31681047`、`d9052b07`、`d49af974` | 下の「見つけて直した問題」 |

### 見つけて直した問題

| 問題 | 検出 | 対処 |
| :--- | :--- | :--- |
| formation の更新に `Accept:` を付けず、メディアタイプだけをヘッダーに渡した（AI の誤り） | push の CI の `deploy-demo` が 400 | 手元で `Accept:` なし 400・あり 200 を確かめて直した。release の前の失敗で、デモは動き続けた |
| `deploy:demo:ci-key` が、Issue の本文のバッククォートをシェルの文字列に埋め込み、`gh issue create` のときにシェルが `npx gulp deploy:demo:ci-key` を実行して再帰した（AI の誤り。レビューの R-26 を W10 に回していた） | 人の実行の後、キーが 6 つ増えて Issue がない → AI がプロセスの一覧で入れ子の実行を見つけた | AI がプロセスを止めた。外部コマンドを `execFileSync` の引数の配列にし、再入を止める守りを足した。最も新しいキーで配備が通ることを確かめ、人が古いキーを失効させた |
| `execArgs` が、出力を端末に流すときの `null` に `.trim()` を呼んだ（AI の誤り） | 人の `revoke-old` の実行で、1 件目の失効の後に TypeError | `null` を空文字にした。人が再実行して残りを失効させた |
| ワークフローの再実行は、その実行のときの定義で動く | 古い実行の再実行の確かめ | 最新かを確かめる処理が入った `213e43a6` より前の実行は再実行しない、と手順書に書いた |

### デモ項目の結果

| # | デモ | 結果 |
| :--- | :--- | :--- |
| 1 | develop にアプリの変更を push する | `70bb65ea`・`31681047` の push で check・ui の後に `deploy-demo` が動いた（45 秒・35 秒）。Environment `demo` の配備の履歴に URL が出る |
| 2 | `npx gulp deploy:demo:status` | `DEMO_REVISION` が push・手動の実行のコミット（いまは `d49af974`） |
| 3 | develop の外のブランチから手動で実行する | `deploy-demo` が skipped（`if`） |
| 追加 | 古い実行を再実行する | 「より新しい配備の対象があります」の警告で配備を飛ばした |
| 追加 | 同じ develop で手動の実行を 2 つ続ける | 前の実行の check・ui が取り消され、`deploy-demo` は skipped |

## 指標

| 指標 | 値 |
| :--- | :--- |
| 承認ゲートの通過 | 4 回（計画、外部連携・セキュリティ、レビューの対応の範囲、終了報告（本報告）） |
| 人の変更依頼 | 0 |
| 人の作業 | キーの登録 3 回（2 回はコマンドの崩れで失敗）、`ci-key` 1 回、`revoke-old` 2 回 |
| `deploy-demo` の時間 | 2 分 41 秒（キャッシュなし）、29〜45 秒（キャッシュあり） |
| push から配備まで | 約 6〜15 分（check・ui を含む。ui が最も長い） |
| CI のキー | 作成 10（使われなかったもの 2・再帰 7・残した 1）、失効 9、残り 1 |

### 時間の内訳

| ステップ | 目安 | 実績 |
| :--- | :--- | :--- |
| 1 | 20 分 | 8 分（09:58〜10:06） |
| 2 | 40 分 | 12 分（10:03〜10:15） |
| 3 | 40 分 | 22 分（10:16〜10:38。キーの登録のやり直しを含む） |
| 4 | 25 分 | 17 分（10:38〜10:55） |
| 5 | 40 分 | 60 分（10:55〜11:55。レビューの対応、400 と再帰と TypeError の対処を含む） |
| 合計 | 165 分 | 約 120 分（CI の待ちを含む） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| actionlint（`rhysd/actionlint:1.7.12`） | exit 0 |
| CI（`d49af974` の手動の実行） | check・ui・`deploy-demo` が success |
| デモ環境 | release v19、web の dyno が up、R14 は 0 件 |
| `okf:check` | ERROR 0 |
| キーの露出 | キーの値はリポジトリ・ログ・会話に出ていない（会話に出たのは長さ 65 だけ） |

## 仮説の結論

| # | 仮説 | 結論 |
| :--- | :--- | :--- |
| H1 | `needs` と Environment `demo` で、手元のガードと同じ約束を CI だけで守れる | **成り立った（境界は人の側）**。テストを通ったその SHA だけを配備でき、手元より直接的になった。develop の外は `if` で止まり、Environment の規則は API の読み返しで確かめた（`if` を外した実行で Environment が拒むことは試していない）。ただし develop に push できれば、ワークフローを書き換えてキーを使える。境界は「k2works のアカウントで develop に push するもの」で、ADR-013 に書いた（D-58） |
| H2 | Heroku CLI なしで、`docker/build-push-action` と Platform API で release できる | **成り立った**。formation の更新と Config Vars の更新、releases・dynos の読み取り、`POST releases` での戻しで足りた。メディアタイプの指定（`Accept:`）を誤ると 400 |
| H3 | キャッシュがあれば配備のジョブは 5 分以内 | **成り立った**。キャッシュありで 29〜45 秒、なしで 2 分 41 秒 |

## 運用レビューと対応

[Bolt 16 運用成果物レビュー](../../review/cargo-tracker/bolt_16_review_20261007.md)（xp-architect・xp-tester・xp-project-manager）。R-01〜R-23、人の決定 D-57〜D-59。

| 対応 | 指摘 |
| :--- | :--- |
| この Bolt で直した | R-01（失敗したら直前の release に戻す）、R-02（取り消しの外へ）、R-03（境界を ADR に）、R-04（期限 90 日と Issue #40）、R-05（キーの更新のタスク）、R-06（古いコミットを出さない）、R-07・R-08（スモーク）、R-09（ADR-008 の注記）、R-10（本報告）、R-13〜R-20。R-26（`execFileSync`）は、キーの更新のタスクで先に行った |
| W10 に回した | R-11（配備のジョブを分け、イメージを 1 回だけビルドして昇格させる。release を 1 回にする）、R-21（スモークの単体テスト）、R-23（`runtime` の検証）、専用の Heroku アカウント（D-57） |
| 対応しない | R-22（スモークの 2 回目の観測） |
| 終了報告の承認で行う | R-12（ADR-013・手順書の `verified`） |

## 判断と学び

### 人の決定

- 2026-10-07: develop の CI が緑なら自動で配備する。API キーは Environment `demo` の secret。Bolt 16 として W3 の前に置く
- 2026-10-07: D-57（いまのキーのまま期限 90 日、更新はタスクと Issue）、D-58（develop の直接 push は変えず、境界を ADR に書く）、D-59（直す範囲）

### 既知の課題（W10 の運用準備 #28 に持ち込む）

- 配備のジョブを分け、イメージを 1 回だけビルドして昇格させる。`DEMO_REVISION` をイメージに入れて release を 1 回にする（R-11）
- スモークを `.github/scripts/` に出して単体テストを書く（R-21）
- デモの配備のキーを、アプリの collaborator だけの専用の Heroku アカウントのものにする（D-57）
- `deploy_demo.js` のほかのタスクの外部コマンドも `execFileSync` の配列にする（R-26 の残り）

### ふりかえり（KPT）

- Keep
  - push すると Environment が規則なしで自動で作られることに、push の前に気づいて順序を入れ替えた
  - 古い dyno の 200 にだまされないスモークにし、レビューの後に自分の release と `DEMO_REVISION` まで確かめる形にした
  - 再帰に気づいたとき、まずプロセスを止め、残ったキーのうちどれが secret に入っているかを配備で確かめてから、失効の手順に進んだ
- Problem
  - 人に端末で実行してもらう長いコマンドをチャットに書き、折り返しで 2 回崩れた
  - シェルの文字列に人の文章（バッククォートを含む）を埋め込み、タスクが自分自身を再帰で呼んだ。レビューで `execFileSync` の配列を勧められていたのに、W10 に回していた
  - 新しい補助関数の `null` の扱いを、試さずに人に実行してもらった
  - formation の更新の呼び出しを、引数の形を変えた後に試さずに push した
- Try
  - T-47: 人に実行してもらうコマンドは、1 行で短く収まる形（Gulp のタスクか、置き場所の短いスクリプト）にする。長いパイプを貼ってもらわない（AI、すぐ）
  - T-48: 外部コマンドに人の文章や変数を渡すときは、シェルを通さない（`execFileSync` の引数の配列）。レビューで指摘されたシェルへの埋め込みは後回しにしない（AI、すぐ）
  - T-49: 人に実行してもらう前に、外部への作用のない部分（引数の組み立て・戻り値の扱い）を手元で試す。外部 API の呼び出しの形を変えたら、読み取りか冪等な呼び出しで形を確かめてから push する（AI、すぐ）

## 次の Bolt

- W2 の締め（リリース計画の進捗の更新、GitHub との同期）の後、W3（US-06・US-07 の経路設計、US-24 AC4・AC5、US-21 AC1）。範囲は人が決める。
- 2026-12-22 までに #40 でキーを更新する。

## 更新履歴

| 日付 | 内容 | 作成 | 承認 |
| :--- | :--- | :--- | :--- |
| 2026-10-07 | 初版（ステップ 1〜5 の結果、レビューと対応、見つけて直した問題、仮説の結論、KPT） | anthropic/claude-opus-5-5 | — |
| 2026-10-07 | 承認の議題 1〜5（ADR-013 の改訂と ADR-008 の注記、計画からの変更、使われなかったキーの失効、残ったキーと #40、Try T-47〜T-49）を承認し、Bolt 16 を終えた（人の変更依頼 0） | anthropic/claude-opus-5-5 | human:kakimomokuri |

## 関連ドキュメント

- [Bolt 16 計画](bolt_16_plan.md)
- [Bolt 16 運用成果物レビュー](../../review/cargo-tracker/bolt_16_review_20261007.md)
- [ADR-013 デモ環境](../../adr/cargo-tracker/013-heroku-demo-environment.md)、[ADR-008](../../adr/cargo-tracker/008-aws-container-platform.md)
- [Heroku デモ環境セットアップ手順書](../../operation/cargo-tracker/heroku_demo_setup.md)
- [Bolt 15 終了報告](bolt_15_report.md)
