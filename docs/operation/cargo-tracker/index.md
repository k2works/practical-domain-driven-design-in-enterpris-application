# cargo-tracker — 運用

cargo-tracker プロジェクトの運用ドキュメントです。

## ドキュメント一覧

### 環境セットアップ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [アプリケーション開発環境セットアップ手順書](application_development_setup.md) | 開発者の PC での起動（H2 の `bootRun`、PostgreSQL の `bootTestRun`）・テスト・品質チェック・生成物の確認 | 承認済み |
| [Heroku デモ環境セットアップ手順書](heroku_demo_setup.md) | dev プロファイルのデモ環境（Heroku、ADR-013）の作成・配備・確認・停止・削除 | 承認済み |
| 開発環境セットアップ手順書 | 開発環境のインフラ構築手順 | 未作成 |
| AWS ステージング環境セットアップ手順書 | ステージング環境の構築手順 | 未作成（運用準備 W10、#28） |
| AWS プロダクション環境セットアップ手順書 | 本番環境の構築手順 | 未作成（運用準備 W10、#28） |

### 運用コマンド

詳細と前提は [アプリケーション開発環境セットアップ手順書](application_development_setup.md) を参照してください。
Gradle のタスクは `apps/cargo-tracker` で、Gulp のタスクはリポジトリのルートで実行します。

| コマンド | 概要 |
| :--- | :--- |
| `./gradlew bootRun` | H2 で起動する（日常の開発） |
| `./gradlew bootTestRun` | Testcontainers の PostgreSQL で起動する（本番に近い確認） |
| `./gradlew check` | すべての検証（書式・静的解析・テスト・カバレッジの閾値・用語集の整合） |
| `./gradlew uiTest` | 画面の層の受入シナリオ（Playwright と axe-core） |
| `./gradlew jigReports` | 設計ドキュメントの生成（JIG） |
| `npx gulp docs:generate` | ER 図（SchemaSpy）と JIG を生成する |
| `npx gulp docs:build` | 生成ドキュメントを作ってからドキュメントサイトをビルドする |
| `npx gulp sonar-local:start` | ローカルの SonarQube を起動する（クラウドの実行環境では上書きを自動で重ねる） |
| `npx gulp sonar-local:check` | SonarQube でスキャンして品質ゲートを判定する |
| `npx gulp deploy:demo:status` | Heroku のデモ環境の状態と動いているコミット（配備は develop の CI が自動で行う。[Heroku デモ環境セットアップ手順書](heroku_demo_setup.md)） |
| `npx gulp deploy:demo` | CI が使えないときに、手元からデモ環境へ配備する |
| `npx gulp deploy:demo:ci-key` | CI の配備の API キー（期限 90 日）を更新する。`deploy:demo:ci-key:revoke-old` で古いキーを失効させる |

### インフラ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [インフラストラクチャアーキテクチャ](../../design/cargo-tracker/architecture_infrastructure.md) | デプロイ形態・環境構成・データ保護・可観測性・CI/CD の設計 | 承認済み（設計） |
| IaC（Terraform）・ランブック | AWS 環境、監視、運用タスクとランブック | 未作成（運用準備 W10、#28） |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
