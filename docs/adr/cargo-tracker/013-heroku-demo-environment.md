---
type: ADR
title: "ADR-013: 関係者が触って確かめるデモ環境は、dev プロファイルのまま Heroku の Eco dyno で動かす"
description: "ステージング・本番（ADR-008、AWS）とは別に、dev プロファイル（H2 のインメモリ・開発用の利用者）の cargo-tracker を Heroku の Container Registry で配備し、アクセスの制限をしない公開のデモ環境にする決定。"
tags: [adr, operation, demo]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-06T08:05:00Z }
---

# ADR-013: 関係者が触って確かめるデモ環境は、dev プロファイルのまま Heroku の Eco dyno で動かす

ステージング・本番とは別に、関係者がブラウザで触って確かめるためのデモ環境を、dev プロファイルのまま Heroku で動かす。データは永続化せず、アクセスの制限もしない。

日付: 2026-10-06

## ステータス

提案（Bolt 15 の終了報告の承認で採否を決める）

## コンテキスト

- これまでの成果は、開発者の PC の `./gradlew bootRun` と録画でしか見せられない。業務責任者や荷主の代わりの関係者が、自分のブラウザで操作して確かめる場がない。
- ステージング・本番は AWS（ECS Fargate・RDS・S3）で、運用準備（W10、#28）で作る（[ADR-008](008-aws-container-platform.md)）。W10 まで待つと、W3〜W9 の画面を関係者が触れない。
- dev プロファイルは H2 のインメモリ DB で動き、`db/dev-data` の開発用の企業と利用者（荷主担当者 2 人・営業担当者 1 人）を入れる。ログインの画面で開発用の利用者を選んで、固定の password のままログインできる（[ADR-012](012-authentication-principal-and-session.md)、ADR-011 の決定 3）。
- 開発用の設定が dev の外で入ったら、`DevLoginGuard` が起動を止める（守りの 2 層目）。dev で動かす環境では、この守りは働かない。
- 書類の保存先はローカルのファイルシステム（`java.io.tmpdir`）で、S3 の実装は W10（[ADR-010](010-required-document-storage-transaction.md)）。
- 2026-10-06 に human:kakimomokuri が、dev プロファイルの Heroku のデモ環境を、アクセスの制限なし・Container Registry・Eco dyno で作ると決めた（[Bolt 15 計画](../../development/cargo-tracker/bolt_15_plan.md)）。

## 決定（案）

**デモ環境を、ステージング・本番とは別の環境として Heroku に置く。**

| 要素 | 採用 |
| :--- | :--- |
| 位置づけ | 関係者が触って確かめるための環境。ステージング・本番ではなく、非機能要件（可用性・性能・データ保護）の対象にしない |
| プロファイル | `dev`（`SPRING_PROFILES_ACTIVE=dev`）。アプリの設定ファイルは変えず、Heroku 向けの値は Config Vars と起動の引数で渡す |
| 実行 | Heroku の Common Runtime、Eco dyno（512 MB）1 つ。アプリ名 `cargo-tracker-mono-demo`、region `us` |
| 配備 | Container Registry。`apps/cargo-tracker/Dockerfile` で手元でビルドしたイメージ（`linux/amd64`）を `heroku container:push`・`release` で送る。Gulp のタスク `heroku:*` だけで操作する |
| データ | H2 のインメモリと dyno の `/tmp`。dyno の再起動（自動の再起動、配備、スリープ）で `db/dev-data` の初期状態に戻る。永続化しない |
| アクセス | 制限しない。URL を知っていれば、だれでも開発用の利用者でログインできる |
| 運用の約束 | 本物の荷主・利用者・取引のデータを入れない。URL は関係者にだけ伝える。配備は develop の CI が緑のコミットからだけ行う |
| やめ方 | `heroku ps:scale web=0` で止める。要らなくなったら `heroku apps:destroy` で消す |

### 代替案

| 案 | 却下理由 |
| :--- | :--- |
| W10 の AWS のステージングまで待つ | W3〜W9 の画面を関係者が触れず、フィードバックが遅れる |
| AWS にデモ用の小さな環境を作る | Terraform・ECR・ECS・ALB を W10 より前に作ることになり、Bolt の範囲を大きく超える |
| デモ環境用のプロファイル（`demo`）を作り、PostgreSQL と認証を本番に近くする | 利用者を作る手段（US-16）がまだなく、ログインできない。アプリの変更が要る |
| Basic 認証などでアクセスを制限する | 人の決定で入れない。データは架空で再起動で消えるため、制限の手間に見合わないと判断した |

## 影響

### ポジティブ

- 関係者が、W3 以降の画面を自分のブラウザで触って確かめられる。
- デモの前に再起動すれば、いつも同じ初期データから始められる。
- 実行用の Dockerfile は、W10 の ECR・ECS でも出発点にできる。

### ネガティブ

- 固定の password の開発用の利用者で、だれでもログインでき、だれでも書類を添付できる。不適切なデータが入ったら、再起動か停止で消す。
- データが予告なく消える（Heroku は dyno を 1 日 1 回以上再起動する）。長い操作を続けて見せるデモには向かない。
- Eco dyno は 30 分アクセスがないとスリープし、次のアクセスで起動を待つ。
- 月 5 USD（Eco の定額）が人の Heroku のアカウントに付く。
- `DevLoginGuard` の守りは働かない。dev のプロファイルが公開の環境で動くことを、この ADR で認める。

## コンプライアンス

- デモ環境の Config Vars の `SPRING_PROFILES_ACTIVE` が `dev` だけで、`staging`・`prod` を含まないことを `heroku:status` で確かめる。
- ステージング・本番の手順書・IaC に、Heroku のデモ環境の設定や URL を混ぜない。
- 手順書（[Heroku デモ環境セットアップ手順書](../../operation/cargo-tracker/heroku_demo_setup.md)）に、本物のデータを入れない運用と、止め方・消し方を書く。

## 参考資料

- [Bolt 15 計画](../../development/cargo-tracker/bolt_15_plan.md)
- [ADR-008 AWS の ECS Fargate・RDS・S3 で実行する](008-aws-container-platform.md)
- [インフラストラクチャアーキテクチャ](../../design/cargo-tracker/architecture_infrastructure.md)
- [Heroku Container Registry & Runtime](https://devcenter.heroku.com/articles/container-registry-and-runtime)
