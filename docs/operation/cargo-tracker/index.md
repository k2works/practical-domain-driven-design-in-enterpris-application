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
| `npx gulp sonar-local:profile` | 品質プロファイルを `sonarqube.config.json` のとおりにそろえる（User Token が要る） |
| `npx gulp sonar-local:check` | 品質プロファイルをそろえ、SonarQube でスキャンして品質ゲートを判定する |
| `npx gulp deploy:demo:status` | Heroku のデモ環境の状態と動いているコミット（配備は develop の CI が自動で行う。[Heroku デモ環境セットアップ手順書](heroku_demo_setup.md)） |
| `npx gulp deploy:demo` | CI が使えないときに、手元からデモ環境へ配備する |
| `npx gulp deploy:demo:ci-key` | CI の配備の API キー（期限 90 日）を更新する。`deploy:demo:ci-key:revoke-old` で古いキーを失効させる |
| `DEMO_BOLT=bolt-19 DEMO_ISSUE=8 npx gulp issue:attach-demo` | Bolt のデモ項目の動画を、受入の証跡として Issue のコメントに添付する（下の「Issue への受入動画の添付」） |
| `npx gulp issue:attach-demo:all` | これまでの Bolt の動画を、下の添付先の表のとおりにまとめて添付する（添付済みの Bolt は飛ばす） |

### Issue への受入動画の添付

Bolt のデモ項目（画面の層の受入シナリオの `@demo-<Bolt>/<名前>`）を録画した動画（`docs/assets/demo/<Bolt>/*.webm`）を、ストーリーの Issue のコメントに添付し、Issue の中で再生できるようにする。gh CLI の `--attach`（v2.99.0 から。`gh issue comment`・`create`・`edit` と `gh pr` の同じコマンド。動画は `.webm`・`.mp4`・`.mov` で 1 件 100 MB まで、1 回 50 件まで）を使う。コメントには動画ごとにシナリオ名と受入条件のタグを見出しに書き、動画は本文の参照を gh がアップロード先の URL に置き換えて再生できる形で出る。

- 前提: gh v2.99.0 以上で、リポジトリに書き込める権限で `gh auth login` 済みであること。Claude Code のクラウドの実行環境の gh は版が古く認証もないため、添付は開発者の PC で行う
- 先に `DEMO_DRY_RUN=1` を付けて、本文と添付する動画を確かめる。アップロードは取り消せない（コメントは消せるが、アップロードした動画の URL は残る）
- 終了報告の承認の後に、その Bolt の動画を添付する。コメントの末尾に目印（`<!-- issue-attach-demo:<Bolt> -->`）を置き、同じ Issue に同じ Bolt の目印のコメントがあれば添付しない（2 回実行しても重ならない）

```bash
DEMO_BOLT=bolt-19 DEMO_ISSUE=8 DEMO_DRY_RUN=1 npx gulp issue:attach-demo   # 本文と添付を確かめる
DEMO_BOLT=bolt-19 DEMO_ISSUE=8 npx gulp issue:attach-demo                  # 添付する
DEMO_DRY_RUN=1 npx gulp issue:attach-demo:all                               # これまでの Bolt をまとめて確かめる
npx gulp issue:attach-demo:all                                              # これまでの Bolt をまとめて添付する
```

これまでの Bolt の動画の添付先（各 Bolt の計画の Issue 欄のうち R0.1 の Issue。`ops/scripts/issue_demo.js` の `BOLT_ISSUES` と同じにする。新しい Bolt は終了報告の承認の後に両方に足す）:

| Bolt | Issue | Bolt | Issue |
| :--- | :--- | :--- | :--- |
| bolt-01・bolt-03 | #1（US-01 ウォーキングスケルトン） | bolt-10・bolt-11 | #4（US-03） |
| bolt-04・bolt-07・bolt-08 | #2（US-01） | bolt-12 | #5（US-24） |
| bolt-05・bolt-06 | #3（US-02） | bolt-14 | #6（US-18） |
| bolt-09 | #37（Bolt 6〜8 レビューの返済） | bolt-17 | #7（US-06） |
| bolt-19 | #8（US-07） | bolt-20 | #5（US-24） |
| bolt-21 | #9（US-21） | bolt-23b・bolt-24・bolt-25 | #10（US-04） |

### インフラ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [インフラストラクチャアーキテクチャ](../../design/cargo-tracker/architecture_infrastructure.md) | デプロイ形態・環境構成・データ保護・可観測性・CI/CD の設計 | 承認済み（設計） |
| IaC（Terraform）・ランブック | AWS 環境、監視、運用タスクとランブック | 未作成（運用準備 W10、#28） |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
