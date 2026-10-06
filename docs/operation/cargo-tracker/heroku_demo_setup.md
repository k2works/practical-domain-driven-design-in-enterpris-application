---
type: Playbook
title: "Heroku デモ環境セットアップ手順書 - cargo-tracker"
description: "cargo-tracker を dev プロファイルのまま Heroku（Container Registry、Eco dyno）へ配備し、関係者が触って確かめるデモ環境を作る・更新する・止める手順を示す。"
tags: [operation,playbook,setup,heroku]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-06T09:00:00Z }
---

# Heroku デモ環境セットアップ手順書 - cargo-tracker

## 概要

関係者がブラウザで触って確かめるためのデモ環境を、Heroku に作る手順です。位置づけと制約は [ADR-013](../../adr/cargo-tracker/013-heroku-demo-environment.md) を参照してください。操作は Gulp のタスク `deploy:demo:*`（`ops/scripts/deploy_demo.js`）だけで行います。

| 項目 | 内容 |
| :--- | :--- |
| アプリ名 | `cargo-tracker-mono-demo` |
| URL | <https://cargo-tracker-mono-demo-883bf0b92807.herokuapp.com/> |
| プロファイル | `dev`（H2 のインメモリ、開発用の利用者） |
| dyno | Eco（512 MB）、web 1 つ、region `us`、stack `container` |
| 配備 | Container Registry。`apps/cargo-tracker/Dockerfile` の `demo` のステージ |
| Config Vars | `SPRING_PROFILES_ACTIVE=dev`、`JAVA_TOOL_OPTIONS=-Xmx300m -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m` |
| 費用 | Eco の定額、月 5 USD（アカウントの全 Eco dyno に共通） |

> **注意**: アクセスの制限はありません。URL を知っていれば、だれでも開発用の利用者でログインできます。本物の荷主・利用者・取引のデータを入れないでください。URL は関係者にだけ伝えてください。データは dyno の再起動で初期状態に戻ります。

### イメージの構成

`apps/cargo-tracker/Dockerfile` は 3 つのステージを持ちます。

| ステージ | 中身 | 使う場所 |
| :--- | :--- | :--- |
| `build` | `./gradlew bootJar copyDemoLibs` と、bootJar の `app.jar`・`lib/` への展開 | — |
| `runtime` | bootJar だけ。H2 を含まない（AT-06） | W10 の ECR・ECS の出発点 |
| `demo` | `runtime` に `build/demo-lib` の H2 を足す | このデモ環境 |

H2 は `developmentOnly` の依存で、本番の成果物の bootJar には入りません（ADR-007、AT-06）。`copyDemoLibs` が H2 だけを `build/demo-lib` に写し、`demo` のステージがクラスパスに足します。

## 1. 前提条件

| もの | 確かめ方 |
| :--- | :--- |
| Heroku のアカウントと Eco の契約 | Heroku の Billing で Eco dynos plan が有効 |
| Heroku CLI | `heroku --version` |
| Docker（buildx を含む） | `docker buildx version` |
| GitHub CLI（CI の結果を見る） | `gh auth status` |
| Node.js と依存 | リポジトリのルートで `npm install` 済み |

ログインは対話のため、人が行います。

```bash
heroku login
heroku container:login
```

## 2. 初回のセットアップ

リポジトリのルートで実行します。2 回目以降に実行しても、作成を飛ばして Config Vars を合わせるだけです。

```bash
npx gulp deploy:demo:setup
```

アプリの作成（`--stack container --region us`）と Config Vars の設定を行います。アプリ名・region・JVM の設定は `.env` の `DEMO_*` で変えられます（`.env.example` を参照）。

## 3. 配備

develop の CI が緑のコミットから配備します。

```bash
npx gulp deploy:demo
```

`deploy:demo:build` → `deploy:demo:push` → `deploy:demo:release` を順に行います。

- `build` は、作業ツリーに変更がないことと、アプリ（`apps/cargo-tracker`）を最後に変えたコミットの `cargo-tracker-ci.yml` が success であることを確かめてから、`demo` のステージをビルドします。確かめを飛ばすときだけ `DEMO_SKIP_GUARD=1` を付けます。
- イメージは `docker buildx build --provenance=false --sbom=false --output type=image,…,oci-mediatypes=false` で作ります。Heroku の Container Registry は Docker v2 の manifest だけを受けるためです。
- `release` の後に dyno の種類を Eco にします。

所要時間の目安は、依存のキャッシュがあれば 1 分弱です（初回のビルドは 3〜4 分）。

## 4. 確認

```bash
npx gulp deploy:demo:status   # dyno・release・プロファイル（dev だけ）・URL
npx gulp deploy:demo:open     # ブラウザで開く
```

ブラウザでは、ログインの画面の「開発用の利用者でログイン（開発環境だけ）」から、荷主担当者 2 人・営業担当者 1 人を選んでログインします。

| 確かめること | 期待する結果 |
| :--- | :--- |
| URL を開く | https のまま A-01（`/login`）へ移る |
| 荷主担当者でログイン | 荷主の輸送要求の一覧（`/customer/transport-requests`） |
| 営業担当者でログイン | 営業の受付一覧（`/staff/transport-requests`） |
| 営業担当者で荷主の画面を開く | 「権限がありません」（A-04） |
| `deploy:demo:status` のプロファイル | `dev` だけ（`staging`・`prod` を含まない。ADR-013 のコンプライアンス） |

## 5. ログ

```bash
npx gulp deploy:demo:logs     # tail。Ctrl+C で止める
```

メモリの値は `log-runtime-metrics` で 20 秒ごとにログに出ます（`sample#memory_total`）。無効になっていたら `heroku labs:enable log-runtime-metrics -a cargo-tracker-mono-demo` で有効にし、`deploy:demo:restart` します。

## 6. デモの前に

```bash
npx gulp deploy:demo:restart  # データを db/dev-data の初期状態に戻す
npx gulp deploy:demo:open
```

- 再起動すると、それまでに入れた輸送要求・見積り・添付した書類は消え、開発用の企業と利用者だけになります。起動は 5 秒前後です。
- Eco dyno は 30 分アクセスがないとスリープします。スリープからの最初のアクセスは、起動を待つため遅くなります（Bolt 15 の計測は終了報告を参照）。デモの数分前に一度開いておきます。

## 7. 停止と削除

```bash
npx gulp deploy:demo:stop     # dyno を 0 にする（公開と Eco の時間の消費を止める）
npx gulp deploy:demo:start    # dyno を 1 に戻す
```

デモ環境が要らなくなったら、アプリを消します。取り消せないため、タスクにはしていません。人が確かめてから手で実行します。

```bash
heroku apps:destroy -a cargo-tracker-mono-demo --confirm cargo-tracker-mono-demo
```

消した後は、Eco の契約（月 5 USD）がほかのアプリで要らないかを Heroku の Billing で確かめます。

## 8. 制約とよくあるつまずき

| 症状 | 原因と対処 |
| :--- | :--- |
| `docker push` が `error from registry: unsupported` | Docker の containerd のイメージストアが OCI の media type で送っている。`deploy:demo:build` を使う（`oci-mediatypes=false`、`--provenance=false`、`--sbom=false`）。`docker build` で作り直さない |
| ログに `Error R14 (Memory quota exceeded)` | ヒープが 512 MB を基準にしていない。`-XX:MaxRAMPercentage` ではなく `-Xmx300m` を使う（`deploy:demo:setup` で Config Vars を合わせる）。Bolt 15 の計測で、起動の後 355 MB、操作の後 382 MB |
| 起動で `ClassNotFoundException: org.h2.Driver` | `runtime` のステージのイメージを送った。`--target demo` で作る（`deploy:demo:build`） |
| 起動で `cargotracker.dev-login は dev プロファイルでだけ設定できる` | Config Vars の `SPRING_PROFILES_ACTIVE` に `staging`・`prod` が混ざっている。`dev` だけにする |
| `deploy:demo:build` が「CI が緑ではありません」で止まる | アプリの最後の変更の CI が終わっていないか、失敗している。`gh run list --workflow cargo-tracker-ci.yml` で確かめ、緑になってから配備する |
| 入れたデータが消えた | 仕様（H2 のインメモリ）。Heroku は dyno を 1 日 1 回以上再起動し、配備とスリープでも初期状態に戻る |
| 大きな書類の添付が失敗する | Heroku の Router は 30 秒で request を打ち切る。デモでは小さなファイルを使う |

## 関連ドキュメント

- [ADR-013 デモ環境](../../adr/cargo-tracker/013-heroku-demo-environment.md)
- [アプリケーション開発環境セットアップ手順書](application_development_setup.md)
- [Bolt 15 計画](../../development/cargo-tracker/bolt_15_plan.md)
- [Heroku Container Registry & Runtime](https://devcenter.heroku.com/articles/container-registry-and-runtime)
