---
type: ADR
title: "ADR-013: 関係者が触って確かめるデモ環境は、dev プロファイルのまま Heroku の Eco dyno で動かす"
description: "ステージング・本番（ADR-008、AWS）とは別に、dev プロファイル（H2 のインメモリ・開発用の利用者）の cargo-tracker を Heroku の Container Registry で配備し、だれでも開ける公開のデモ環境にする決定。H2 は Dockerfile の demo のステージにだけ入れ、本番の成果物（bootJar）と AT-06 は変えない。"
tags: [adr, operation, demo]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-06T09:30:00Z }
---

# ADR-013: 関係者が触って確かめるデモ環境は、dev プロファイルのまま Heroku の Eco dyno で動かす

ステージング・本番とは別に、関係者がブラウザで触って確かめるためのデモ環境を、dev プロファイルのまま Heroku で動かす。データは永続化せず、アクセスの制限もしない。だれでも開ける公開のデモとする。

日付: 2026-10-06

## ステータス

提案（Bolt 15 の終了報告の承認で採否を決める。却下するなら `heroku apps:destroy` まで行う）

## コンテキスト

- これまでの成果は、開発者の PC の `./gradlew bootRun` と録画でしか見せられない。業務責任者や荷主の代わりの関係者が、自分のブラウザで操作して確かめる場がない。
- ステージング・本番は AWS（ECS Fargate・RDS・S3）で、運用準備（W10、#28）で作る（[ADR-008](008-aws-container-platform.md)）。W10 まで待つと、W3〜W9 の画面を関係者が触れない。
- dev プロファイルは H2 のインメモリ DB で動き、`db/dev-data` の開発用の企業と利用者（荷主担当者 2 人・営業担当者 1 人）を入れる。ログインの画面で開発用の利用者を選んで、固定の password のままログインできる（[ADR-012](012-authentication-principal-and-session.md)、ADR-011 の決定 3）。開発用の利用者と password は、公開のリポジトリの README にある。
- 開発用の設定が dev の外で入ったら、`DevLoginGuard` が起動を止める（守りの 2 層目）。dev で動かす環境では、この守りは働かない。
- H2 は `developmentOnly` の依存で、本番の成果物の bootJar に入らない。AT-06（`verifyProductionClasspath`）がこれを守る（[ADR-007](007-postgresql-mybatis-flyway.md)）。bootJar をそのまま使うと、dev では `org.h2.Driver` がなく起動しない。
- 書類の保存先はローカルのファイルシステム（`java.io.tmpdir`）で、S3 の実装は W10（[ADR-010](010-required-document-storage-transaction.md)）。
- リポジトリは公開で、docs は GitHub Pages にも出る。デモ環境の URL を docs に書けば、だれでも知りうる。
- 2026-10-06 に human:kakimomokuri が、dev プロファイルの Heroku のデモ環境を、アクセスの制限なし・Container Registry・Eco dyno で作ると決めた（[Bolt 15 計画](../../development/cargo-tracker/bolt_15_plan.md)）。H2 の入れ方（確認ポイント 13）、公開を前提にすること（D-54）、常時起動（D-55）も人が決めた（[Bolt 15 運用成果物レビュー](../../review/cargo-tracker/bolt_15_review_20261006.md)）。

## 決定（案）

**デモ環境を、ステージング・本番とは別の、だれでも開ける公開の環境として Heroku に置く。**

| 要素 | 採用 |
| :--- | :--- |
| 位置づけ | 関係者が触って確かめるための環境。ステージング・本番ではなく、非機能要件（可用性・性能・データ保護）の対象にしない |
| プロファイル | `dev`（Config Vars の `SPRING_PROFILES_ACTIVE=dev`）。アプリの設定ファイルは変えず、Heroku 向けの値は Config Vars と起動の引数で渡す |
| 実行 | Heroku の Common Runtime、Eco dyno（512 MB）1 つ。アプリ名 `cargo-tracker-mono-demo`、region `us`、stack `container` |
| JVM | `-Xmx300m -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m`（Config Vars の `JAVA_TOOL_OPTIONS`） |
| イメージ | `apps/cargo-tracker/Dockerfile` の `demo` のステージ（下の「ビルドの構成」） |
| 配備 | Gulp のタスク `deploy:demo:*`（`ops/scripts/deploy_demo.js`）だけで操作する。`deploy:demo` は `docker buildx build`（Docker v2 の manifest）→ `docker push`（Container Registry）→ `heroku container:release` |
| 配備のガード | 作業ツリーに変更がなく、develop にいて、HEAD が `origin/develop` に含まれ、アプリを最後に変えたコミットの develop での CI が緑のときだけ、ビルドと push を行う。イメージにコミットの SHA のラベルを付け、release の後に Config Vars の `DEMO_REVISION` に残す |
| データ | H2 のインメモリと dyno の `/tmp`。dyno の再起動（自動の再起動、配備、スリープ、Config Vars の変更）で `db/dev-data` の初期状態に戻る。永続化しない |
| アクセス | 制限しない。URL は docs（手順書・README）に書き、だれでも開発用の利用者でログインできる公開のデモとする（D-54） |
| 稼働 | 常時起動にし、30 分アクセスがなければ Eco のスリープに任せる（D-55） |
| 運用の約束 | 本物の荷主・利用者・取引のデータを入れない。週次の見直しで `deploy:demo:status` と `deploy:demo:logs` を確かめる。不審な利用に気づいたら `deploy:demo:restart` か `deploy:demo:stop` をする |
| やめ方 | `deploy:demo:stop`（dyno を 0）で止める。W10 でステージングができたら要否を見直し、要らなければ `heroku apps:destroy` で消す（取り消せないため、タスクにせず人が手で行う） |

### ビルドの構成

本番の成果物の約束（ADR-007、AT-06）を守ったまま dev を動かすため、H2 はデモのイメージにだけ入れる（Bolt 15 の確認ポイント 13）。

| 部分 | 内容 |
| :--- | :--- |
| `copyDemoLibs`（`build.gradle`） | `developmentOnly` のうち `com.h2database` だけを `build/demo-lib` に写す（`Sync`）。版は Spring Boot の BOM で決まる。devtools は写さない |
| `build` のステージ | `./gradlew bootJar copyDemoLibs` の後、bootJar を `java -Djarmode=tools extract` で `app.jar` と `lib/` に展開する |
| `runtime` のステージ | 展開した bootJar だけ。H2 を含まない。uid 10001 の非 root で動かす。W10 の ECR・ECS の出発点 |
| `demo` のステージ | `runtime` に `build/demo-lib` を足し、`EXTRA_CLASSPATH` でクラスパスに加える |
| bootJar と AT-06 | 変えない |

### 代替案

| 案 | 却下理由 |
| :--- | :--- |
| W10 の AWS のステージングまで待つ | W3〜W9 の画面を関係者が触れず、フィードバックが遅れる |
| AWS にデモ用の小さな環境を作る | Terraform・ECR・ECS・ALB を W10 より前に作ることになり、Bolt の範囲を大きく超える |
| デモ環境用のプロファイル（`demo`）を作り、PostgreSQL と認証を本番に近くする | 利用者を作る手段（US-16）がまだなく、ログインできない。アプリの変更が要る |
| Basic 認証などでアクセスを制限する | 人の決定で入れない。データは架空で再起動で消えるため、制限の手間に見合わないと判断した |
| URL を docs に書かず秘匿する | リポジトリと GitHub Pages が公開で、git の履歴にも残るため、作り直さない限り秘匿できない。人の決定（D-54）で公開を前提にした |
| H2 を Dockerfile だけで Maven Central から取る | `build.gradle` は変えずに済むが、Spring Boot を上げたときに H2 の版を手で合わせる必要がある |
| デモ用の bootJar（`developmentOnly` を含む）を別に作る | 成果物の jar が 2 種類になり、本番の成果物に H2 を入れない約束が jar の選び方に頼る |

## 影響

### ポジティブ

- 関係者が、W3 以降の画面を自分のブラウザで触って確かめられる。
- デモの前に再起動すれば、いつも同じ初期データから始められる。
- 本番の成果物（bootJar）と AT-06 は変わらない。`runtime` のステージは、W10 の ECR・ECS の出発点にできる。
- 動いているコミットが `DEMO_REVISION` で分かり、CI が緑の develop のコミットだけが出る。

### ネガティブ

- 固定の password の開発用の利用者で、だれでもログインできる。荷主の画面から書類（1 件 10 MB、1 回 55 MB まで）を添付でき、営業の画面から取り出せるため、ファイルの置き場として悪用されうる。悪用されると、Heroku の利用規約により人の Heroku のアカウント全体（ほかのアプリを含む）が止められる恐れがある。気づく仕組みは週次のログの確かめだけで、対処は再起動か停止。
- dev は session の Cookie に Secure を付けない（`server.servlet.session.cookie.secure=false`）。アプリは http から https へ移さず、HSTS もないため、`http://` で開くと session の Cookie が平文で流れる。架空のデータで、だれでもログインできるため、影響は小さい。
- データが予告なく消える（Heroku は dyno を 1 日 1 回以上再起動する）。長い操作を続けて見せるデモには向かない。
- Eco dyno は 30 分アクセスがないとスリープし、次のアクセスで起動を待つ。
- メモリの余裕は 512 MB に対して約 130 MB（Bolt 15 の計測で操作の後 382 MB）。大きな書類や同時の操作で R14 が出たら、dyno の種類を人に諮る。
- `-Xmx300m` は Heroku の dyno だけの値。dyno では JVM が 512 MB の上限を基準にせず、`-XX:MaxRAMPercentage` で R14 が出た。cgroup の上限が JVM に見える Fargate（W10）では `-XX:MaxRAMPercentage` を使い、この値を写さない。
- 月 5 USD（Eco の定額）が人の Heroku のアカウントに付く。アカウント・費用・停止の手段が 1 人に集まる。
- `DevLoginGuard` の守りは働かない。dev のプロファイルが公開の環境で動くことを、この ADR で認める。
- `runtime` のステージに Heroku の都合（`PORT`、`sh -c`、`EXTRA_CLASSPATH`）が残る。W10 で `runtime` を exec 形式にし、これらを `demo` に移す（Bolt 15 レビュー R-11）。

## コンプライアンス

- `deploy:demo:status` で、`SPRING_PROFILES_ACTIVE` が `dev` だけで `staging`・`prod` を含まないこと、`DEMO_REVISION` が develop の CI が緑のコミットであることを確かめる。
- `runtime` のステージのイメージは H2 を含まない（dev で起動すると `org.h2.Driver` で失敗する）。bootJar の AT-06 は変えない。
- dev のプロファイルに H2 Console と devtools を入れない（公開の環境で動くため。入れると任意のコードの実行の経路になる）。
- ステージング・本番の手順書・IaC に、Heroku のデモ環境の設定を混ぜない。
- 手順書（[Heroku デモ環境セットアップ手順書](../../operation/cargo-tracker/heroku_demo_setup.md)）に、本物のデータを入れない運用、週次の確かめ、止め方・消し方、ロールバックを書く。

## 参考資料

- [Bolt 15 計画](../../development/cargo-tracker/bolt_15_plan.md)
- [Bolt 15 運用成果物レビュー](../../review/cargo-tracker/bolt_15_review_20261006.md)
- [ADR-007 開発環境は H2、本番は PostgreSQL 18](007-postgresql-mybatis-flyway.md)
- [ADR-008 AWS の ECS Fargate・RDS・S3 で実行する](008-aws-container-platform.md)
- [インフラストラクチャアーキテクチャ](../../design/cargo-tracker/architecture_infrastructure.md)
- [Heroku Container Registry & Runtime](https://devcenter.heroku.com/articles/container-registry-and-runtime)
