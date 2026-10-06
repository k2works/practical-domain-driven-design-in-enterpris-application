---
type: Plan
title: "Bolt 15 終了報告 - dev プロファイルによる Heroku のデモ環境"
description: "15 回目の Bolt の終了報告。cargo-tracker を dev プロファイルのまま Heroku（Container Registry、Eco dyno）に配備した公開のデモ環境、H2 をデモのイメージにだけ入れる Dockerfile の構成、Gulp のタスク deploy:demo:*、手順書と ADR-013、3 視点の運用レビューと対応、デモ項目と動画、仮説 H1〜H3 の結論、ふりかえりをまとめる。"
tags: [development,bolt-report,operation]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-06T11:00:00Z }
---

# Bolt 15 終了報告 - dev プロファイルによる Heroku のデモ環境

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 根拠 | 却下・修正のときに戻す箇所 |
| :--- | :--- | :--- | :--- |
| 1 | [ADR-013](../../adr/cargo-tracker/013-heroku-demo-environment.md)（提案）の採否。公開のデモ（D-54）、常時起動（D-55）、H2 をデモのイメージにだけ入れるビルドの構成（確認ポイント 13）を含む | 計画・確認ポイント 13・D-54〜D-56 はすべて承認済み。ADR はそれを 1 つの決定にまとめた | 却下なら `heroku apps:destroy`、`deploy_demo.js`・Dockerfile の `demo`・`copyDemoLibs` を消す |
| 2 | `log-runtime-metrics` を、ステップ 3 の R14 の調査のときに AI の判断で有効にした（レビューの R-17 で `setup` に入れることを承認する前） | メモリの値をログに出すだけの設定で、いつでも外せる。R14 の原因の確かめに要った | `heroku labs:disable log-runtime-metrics` と `deploy_demo.js` の `setup` |
| 3 | スリープからの起動の時間を測らずに打ち切った | 計画の打ち切りの順の 2 番目。30 分の計測の間（18:05〜18:27 JST）に関係者のアクセスがあり、dyno がスリープしなかった | 次に 30 分以上アクセスのない時間に測り、手順書の「デモの前に」に書く |
| 4 | ロールバックの確かめで、デモ環境を v5 に戻し v7 に戻した（release v8・v9） | 手順書に書いた手順が動くかを確かめた。各 1 分ほどデータが初期状態に戻った | — |
| 5 | Try T-44〜T-46（下の KPT） | ふりかえり | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 15 回 |
| 期間 | 2026-10-06 17:00 JST（計画の承認のコミット `0d5428b5`）〜 19:55（本報告の初版）。計画の作成は 16:49〜17:00。18:10〜19:38 は CI と人の待ち（間に手順書の図の追加の依頼 18:40〜18:45） |
| 対象 | 技術タスク（SP 0）。[#38 [技術] dev プロファイルによる Heroku のデモ環境](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/38) |
| 計画 | [Bolt 15 計画](bolt_15_plan.md) |
| ゴール | 関係者は公開の URL で dev プロファイルの cargo-tracker を操作でき、開発用の利用者でログインして、提出から審査まで試せる。開発者は手順書の Gulp のタスク 1 つで配備し、状態とログを確かめられる。データは再起動で初期状態に戻る |
| 環境 | <https://cargo-tracker-mono-demo-883bf0b92807.herokuapp.com/>（release v9、`DEMO_REVISION` は `5657b21d`） |

## 成果

| ステップ | 状態 | コミット | 誤りの検出と修正（検出の手段） |
| :--- | :--- | :--- | :--- |
| 1. ADR 013 の案・手順書の骨組み・#38 | 完了 | `41ff69d2` | — |
| 2. 実行用の Dockerfile とローカルの起動 | 完了 | `6822406f`、`d85b351b` | bootJar に H2 がなく dev で起動しない（ローカルのコンテナの起動）→ 人に諮り確認ポイント 13。展開した jar の名前の誤り（起動の失敗） |
| 3. Heroku のアプリと初回の配備（外部連携の承認ゲート） | 完了 | `9fb1dd24`、`e0480628`、`3643f65b` | push の `unsupported`（containerd の OCI の media type）→ `oci-mediatypes=false`。R14（Heroku のログ）→ `-Xmx300m` |
| 4. デモ項目と手順書 | 完了（スリープの計測は打ち切り） | `1028af52`、`5657b21d` | Chrome の拡張機能につながらず、スクラッチの Playwright で録画した |
| 5. 運用レビューと終了報告 | 本報告 | `08400a70`、`c05d04ad`、`0c8f5ec6` | 3 視点のレビュー（下）。ガードの確かめで `git stash` が試す対象のコードまで退避していた（出力の不一致）→ コミットしてから確かめ直した |

### 計画からの変更

| 変更 | 理由 | 承認 |
| :--- | :--- | :--- |
| `build.gradle` に `copyDemoLibs` を足し、Dockerfile を `build`・`runtime`・`demo` の 3 つのステージにした（確認ポイント 13） | H2 は `developmentOnly` で bootJar に入らず、AT-06 がそれを守る。計画の確認ポイント 9（アプリの設定を変えない）の外の問題 | 人が 3 つの案から選び、計画の変更として承認 |
| タスクを `heroku:*`（`heroku.js`）から `deploy:demo:*`（`deploy_demo.js`）にし、`build`・`push`・`release`・`stop`・`start` を足した | 運用スクリプト作成ガイドの命名 | ステップ 3 の承認ゲート |
| `docker build` を `docker buildx build --provenance=false --sbom=false --output type=image,…,oci-mediatypes=false` にした | Heroku の Container Registry は Docker v2 の manifest だけを受ける | 実装の詳細（承認の議題に含めない） |
| `JAVA_TOOL_OPTIONS` を `-XX:MaxRAMPercentage=60` から `-Xmx300m` にした | dyno で R14。ローカルの 512 MB の制限では出なかった | 実装の詳細（ADR-013 に理由を書いた） |
| 配備のガードを develop・`origin/develop`・`push` に広げ、SHA を残した。ADR-013 を公開を前提に書き直した | 運用レビュー（D-54〜D-56） | 人の承認 |
| 手順書に構成図と配備の流れの図を足した | 人の依頼 | 人の依頼 |

### デモ項目の結果

| # | デモ | 結果 | 動画 |
| :--- | :--- | :--- | :--- |
| 1 | 公開の URL を開き、荷主担当者でログインする | https のまま A-01 から荷主のホームへ移った | [submit-review-on-heroku.webm](../../assets/heroku-demo/bolt-15/submit-review-on-heroku.webm){:target="_blank"} |
| 2 | 荷主が見積依頼を提出し、営業が受付一覧から開いて審査を確定する | TR-2026-0001 を提出し、営業が審査を確定して見積りの作成へ移った。営業から荷主の画面は「権限がありません」 | 同上 |
| 3 | `deploy:demo:restart` の後に開き直す | 再起動の前は受付一覧に TR-2026-0001 があり、後はなかった | — |
| 4 | `deploy:demo:status`・`deploy:demo:logs` | Eco の web 1 つ、release、プロファイル `dev` だけ、`DEMO_REVISION`、URL。R14 なし | — |

動画は Chrome の拡張機能につながらなかったため、スクラッチパッドに入れた Playwright（Node）で公開の URL を録画した。`./gradlew demoVideo` は `docs/assets/demo` を消して撮り直すため、その外の `docs/assets/heroku-demo` に置いた。動画は AI が再生して確かめていない。

## 指標

| 指標 | 値 |
| :--- | :--- |
| 承認ゲートの通過 | 6 回（計画、計画の変更（確認ポイント 13）、外部連携、レビューの対応の範囲、終了報告（本報告）、ほかに図の追加の依頼） |
| 人の変更依頼 | 1（アプリ名を `cargo-tracker-mono-demo` に）。ほかに追加の依頼 1（手順書の図） |
| 人に諮った問題 | 2（H2 の入れ方、レビューの論点 3 つ） |
| 起動の時間（Eco） | 4.8〜15.5 秒（R10 の 60 秒の内） |
| メモリ（`memory_total`） | 起動の後 355 MB、操作の後 382 MB、最後の確かめで 375 MB（512 MB の内） |
| 配備の時間 | 46 秒（依存のキャッシュあり）、9.7 分（`build.gradle` を変えた後。build が 8.5 分） |
| イメージ | `runtime` 563 MB、`demo` 568 MB |

### 時間の内訳

| ステップ | 目安 | 実績 |
| :--- | :--- | :--- |
| 1 | 30 分 | 5 分（17:00〜17:05） |
| 2 | 60 分 | 30 分（17:02〜17:31。人の判断の待ちを含む） |
| 3 | 45 分 | 21 分（17:31〜17:52。人のログインの待ちを除く） |
| 4 | 45 分 | 10 分（17:52〜18:02。スリープの計測は打ち切り） |
| 5 | 40 分 | 約 45 分（レビュー 18:00〜18:04、対応 18:04〜18:10、図 18:40〜18:45、配備し直しとロールバックの確かめ 19:38〜19:52、本報告） |
| 合計 | 220 分 | 約 110 分（待ちを除く） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `./gradlew check verifyProductionClasspath`（ローカル） | 緑（4 分 20 秒、ステップ 2） |
| CI（`c05d04ad`） | check・ui ともに success |
| `./gradlew uiTest`（ローカル） | 本報告の承認の前に結果を書く |
| `runtime` のイメージに H2 がない | dev で起動すると `org.h2.Driver` で失敗（ステップ 2） |
| `okf:check` | ERROR 0 |
| PlantUML（計画・手順書の図） | `plantuml -checkonly` が通った |

## 仮説の結論

| # | 仮説 | 結論 |
| :--- | :--- | :--- |
| H1 | dev に Heroku 向けの設定を足さず、環境変数だけで動かせる。https のリダイレクトが保たれる | **成り立った（条件付き）**。アプリの設定ファイルは変えず、Config Vars と起動の引数だけで動いた。公開の URL で、ログインの前後のリダイレクトは https のまま。ただし dev を動かすには、イメージに H2 を足す必要があった（アプリの設定ではなくイメージの構成の問題） |
| H2 | ヒープを絞れば Eco（512 MB）で R14 を出さずに動く | **成り立った（値の決め方は誤った）**。`-XX:MaxRAMPercentage=60` では dyno で R14 が出た。ローカルの `--memory=512m` では 358 MiB で出なかったため、ローカルの確かめは dyno のメモリの扱いを写せていなかった。`-Xmx300m` で 355〜382 MB |
| H3 | Container Registry でモノレポのまま配備できる | **成り立った**。`apps/cargo-tracker` を context にしたイメージを送れた。ただし Docker 29 の containerd のイメージストアでは `oci-mediatypes=false` が要る。`runtime` のステージは W10 の出発点にできるが、Heroku の都合を `demo` に移す必要がある（R-11） |

## 運用レビューと対応

[Bolt 15 運用成果物レビュー](../../review/cargo-tracker/bolt_15_review_20261006.md)（xp-architect・xp-tester・xp-project-manager）。R-01〜R-27、人の決定 D-54〜D-56。

| 対応 | 指摘 |
| :--- | :--- |
| この Bolt で直した | R-01〜R-10、R-15〜R-23、R-25（ADR-013 の書き直し、ガードの拡張と SHA、ロールバック、所有者と週次の確かめ、`Sync`、`log-runtime-metrics`、`JSON.parse`、`container:login` の案内、リスク台帳）。R-09（スリープの値）は計測を打ち切ったため「未計測」と書いた |
| W10 に回した | R-11（`runtime` から Heroku の都合を外す）、R-12（レイヤーの分割）、R-13（イメージの自動の検証と forward headers のテスト）、R-14（`git archive` からの context）、R-24（digest の固定ほか）、R-26（`execFileSync`） |
| 対応しない | R-27（動画にアドレスバーは映らない） |

直した後に、`deploy:demo` で配備し直し（release v6・v7、`DEMO_REVISION` は `5657b21d`）、ロールバック（v8 で v5 へ、v9 で v7 へ）を確かめ、荷主・営業のログインと 403 を公開の URL で確かめた。

## 判断と学び

### 人の決定

- 2026-10-06: dev プロファイルの Heroku のデモ環境を、アクセスの制限なし・Container Registry・Eco dyno・Bolt 15 で作る。アプリ名は `cargo-tracker-mono-demo`
- 2026-10-06: H2 は `copyDemoLibs` と Dockerfile の `demo` のステージで、デモのイメージにだけ入れる（確認ポイント 13）
- 2026-10-06: D-54（公開を前提に ADR-013 を直す）、D-55（常時起動）、D-56（直す範囲と W10 に回す範囲）

### 既知の課題（W10 の運用準備 #28 に持ち込む）

- `runtime` のステージを exec 形式にし、`PORT`・`sh -c`・`EXTRA_CLASSPATH` を `demo` に移す（R-11）
- `extract --layers` でレイヤーを分ける（R-12）。ベースイメージを digest で固定する（R-24）
- CI で `runtime` のイメージに H2 がないこと、`demo` のイメージが起動することを確かめる。forward headers の振る舞いを `@SpringBootTest(webEnvironment=RANDOM_PORT)` で固定する（R-13）
- Fargate では `-XX:MaxRAMPercentage` を使い、`-Xmx300m` を写さない（ADR-013）
- `readonlyRootFilesystem` では書類の `/tmp` にボリュームが要る（ADR-010 の S3 まで）

### ふりかえり（KPT）

- Keep
  - 起動しない問題を見つけたとき、確認ポイント 9 のとおりに止め、試作の計測（起動の時間・メモリ・H1）を添えて案を出した。人は 1 回で選べた
  - 本番の成果物の約束（AT-06）を変えずに済む構成を選び、`runtime` のイメージが H2 なしで落ちることを失敗の側から確かめた
  - 取り消せない削除をタスクにせず、ロールバックは手順書に書いて実際に確かめた
- Problem
  - ローカルの `--memory=512m` の確かめで、Eco のメモリは足りると判断したが、dyno では R14 が出た。確かめた環境と目標の環境のメモリの扱いの違いを見ていなかった
  - ガードの確かめで `git stash` を使い、試す対象の変更まで退避して、古いコードで「通った」と見えた。出力に期待のエラーがないことに気づいて確かめ直した
  - 「URL は関係者にだけ伝える」という約束を、リポジトリと GitHub Pages が公開であることを確かめずに ADR と手順書に書いた。レビューで指摘された
- Try
  - T-44: 実行環境の資源の制約（メモリ・CPU・時間の上限）に関わる設定は、ローカルの模擬で決めず、目標の環境で計測してから決める（AI、すぐ）
  - T-45: 守りの部品（ガード・検証）を確かめるときは、試す対象の変更をコミットしてから確かめる。`git stash` で作業ツリーを切り替えない（AI、すぐ）
  - T-46: 公開の範囲に関わる約束（「関係者にだけ」「社内だけ」）を書く前に、置き場所（リポジトリ・ドキュメントのサイト）の公開の範囲を確かめる（AI、すぐ）

## 次の Bolt

- W2 の締め（リリース計画の進捗状況と実績スケジュールの更新、GitHub との同期）の後、W3（US-06・US-07 の経路設計、US-24 AC4・AC5、US-21 AC1）。範囲は人が決める。
- 週次の見直しで、デモ環境の `deploy:demo:status` と `deploy:demo:logs` を確かめる（D-55）。

## 更新履歴

| 日付 | 内容 | 作成 | 承認 |
| :--- | :--- | :--- | :--- |
| 2026-10-06 | 初版（ステップ 1〜5 の結果、レビューと対応、仮説の結論、KPT） | anthropic/claude-opus-5-5 | — |

## 関連ドキュメント

- [Bolt 15 計画](bolt_15_plan.md)
- [Bolt 15 運用成果物レビュー](../../review/cargo-tracker/bolt_15_review_20261006.md)
- [ADR-013 デモ環境](../../adr/cargo-tracker/013-heroku-demo-environment.md)
- [Heroku デモ環境セットアップ手順書](../../operation/cargo-tracker/heroku_demo_setup.md)
- [リリース計画](release_plan.md)（W3）、[開発戦略](development_strategy.md)
