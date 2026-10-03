---
type: Playbook
title: "アプリケーション開発環境セットアップ手順書 - cargo-tracker"
description: "cargo-tracker（A 社国際貨物輸送管理システム）を、開発者の PC で起動・テスト・品質チェックするための手順を示す。"
tags: [operation,playbook,setup]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-03T01:57:26Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-02T01:37:59Z }
  - { by: human:kakimomokuri, at: 2026-10-02T07:02:38Z }
  - { by: human:kakimomokuri, at: 2026-10-02T11:26:07Z }
  - { by: human:kakimomokuri, at: 2026-10-02T13:47:11Z }
  - { by: human:kakimomokuri, at: 2026-10-03T00:50:25Z }
  - { by: human:kakimomokuri, at: 2026-10-03T02:07:18Z }
---

# アプリケーション開発環境セットアップ手順書 - cargo-tracker

## 概要

cargo-tracker（A 社国際貨物輸送管理システム）を、開発者の PC で起動・テスト・品質チェックするための手順を示す。テンプレート（`docs/template/アプリケーション開発環境セットアップ手順書.md`）の構成に従い、このプロジェクトにあるものだけを書く。

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `apps/cargo-tracker`（Java 25、Spring Boot 4.1、Spring Modulith のモジュラーモノリス） |
| 正とする文書 | 品質チェックのコマンドは [開発戦略](../../development/cargo-tracker/development_strategy.md) の「品質チェックのコマンド」、使う技術と版は [技術スタック](../../design/cargo-tracker/tech_stack.md) を正とする。本書は環境を用意して動かす手順だけを書く |
| 確認日 | 2026-10-02（本書のコマンドはこの日に実際に動かして確かめた） |

## 1. 前提条件

| ツール | 版 | 用途 | 確かめ方 |
| :--- | :--- | :--- | :--- |
| JDK | 25（Amazon Corretto を推奨） | ビルド・実行。Gradle の toolchain が 25 を求める | `java -version` |
| Docker | 動いていること | 統合テスト・画面の層のテスト・`bootTestRun` の PostgreSQL 18.6（Testcontainers） | `docker info` |
| Node.js | 版は問わない（技術スタックに定めがない。確認日は v24 で動かした） | SonarQube と文書の検査（ルートの Gulp のタスク）を使うときだけ要る | `node --version` |

Gradle は Gradle Wrapper（`./gradlew`、9.8.0）を使うため、入れなくてよい。

## 2. プロジェクトの取得

```bash
git clone https://github.com/k2works/practical-domain-driven-design-in-enterpris-application.git
cd practical-domain-driven-design-in-enterpris-application
npm install                                           # ルートの Gulp のタスクを使う場合
git config blame.ignoreRevsFile .git-blame-ignore-revs  # 書式だけのコミットを git blame で飛ばす（一度だけ）
cd apps/cargo-tracker
```

## 3. 起動

### H2 で起動する（日常の開発）

開発体験を優先し、ローカルの起動は H2 を PostgreSQL 互換モードで使う（ADR-007）。Docker は要らない。

```bash
./gradlew bootRun
```

`dev` プロファイル（`application-dev.properties`）で起動し、Flyway のマイグレーションを当てる。権限による保護（追記専用の表）は H2 では働かない。

IDE（IntelliJ IDEA など）から `CargoTrackerApplication` を直接起動するときは、実行構成の Active profiles に `dev` を入れる。入れないと H2 の設定と H2 コンソールが有効にならない（`/h2-console` が 404 になる）。

### PostgreSQL で起動する（本番に近い確認）

```bash
./gradlew bootTestRun
```

テストのクラスパスの `TestCargoTrackerApplication` が、Testcontainers で PostgreSQL 18.6 を起動して接続する。追記専用の表の権限（`afterMigrate`）も働く。終了するとコンテナも止まる。

### 開く URL

| URL | 画面 | 備考 |
| :--- | :--- | :--- |
| <http://localhost:8080/> | 画面の入口の一覧 | 荷主の画面と社内の画面へのリンク。認証（US-18）を入れたら、ログインの画面に置き換える |
| <http://localhost:8080/customer/transport-requests/new> | 見積依頼の作成（C-03 の 1 画面の形） | 荷主の画面。必須条件を入れて提出すると（必要書類は任意。PDF・PNG・JPEG で 1 件 10 MB まで、商業送り状・梱包明細・その他 3 件）、見積依頼の詳細（C-04）に業務番号（例: `TR-2026-0001 版 1`）と「提出しました」が出る。内部の ID（UUID）は画面にもアドレスバーにも出さない（D-4）。希望到着期限は日本時間で `2026-11-02 09:00` の形で入れる |
| <http://localhost:8080/customer/transport-requests> | 見積依頼の一覧（C-02） | 荷主の画面。自社の見積依頼だけを、最初の提出時刻の新しい順に示す。業務番号から詳細（C-04）を開ける。差し戻された見積依頼は「差戻し（お客様の対応待ち）」で出て、詳細で理由と不足事項を読み、「編集して出し直す」から版 2 を出し直せる |
| <http://localhost:8080/staff/transport-requests> | 見積依頼の受付一覧（S-02） | 社内の画面。審査中の見積依頼を提出時刻の古い順に示す。業務番号から審査（S-03）を開き、確定・差戻しできる |
| <http://localhost:8080/staff/kpi-observations> | KPI 計測記録の一覧（S-22 の前身の仮の画面） | 社内の画面。提出の後、非同期の配信を経て業務番号と提出時刻が表示される。提出時刻はまだ UTC だけで表示する（BR-10 との差。S-22 の本実装で直す） |
| <http://localhost:8080/h2-console> | H2 コンソール（`bootRun` のときだけ） | JDBC URL は `jdbc:h2:mem:cargotracker`、利用者は `sa`、パスワードは空 |

添付した書類のファイルは、ローカルのファイルシステムの `cargotracker.document-storage.base-dir` に置く（既定は一時ディレクトリの下の `cargo-tracker/documents`。Bolt 7）。DB は H2 のインメモリなので、`bootRun` をやり直すと書類の記録は消え、ファイルだけが残る。気になるときはこのディレクトリを消してよい。本番の S3 の実装は運用準備（W10、#28）で作る。

認証はまだない。荷主企業と提出者は、`application.properties` の `cargotracker.provisional-actor.*`（仮の主体）の固定値で記録される。認証は US-18 の Bolt で入れ、この設定を消す。

社内の画面（`/staff/**`）にも認証はまだなく、誰でも開ける。ローカルの開発環境だけで動かす。審査の判断者は `cargotracker.provisional-actor.staff-user-id`（仮の営業担当者）で記録される。

荷受人は、企業マスターができるまで `cargotracker.provisional-consignees.*`（仮の荷受人の一覧、3 社）から選ぶ。`.properties` は ISO-8859-1 で読まれるため、名前の日本語は Unicode のエスケープで書く。企業マスターは US-16 の Bolt で入れ、この設定を消す。

## 4. テストと品質チェック

| 目的 | コマンド（`apps/cargo-tracker` で実行） | 時間の目安 |
| :--- | :--- | :--- |
| すべての検証（書式・静的解析・ユニット・アーキテクチャ・業務ルール層の受入シナリオ・統合・Web・H2 のスモーク・本番の依存・カバレッジの閾値・用語集の整合） | `./gradlew check` | 約 2 分 |
| 用語集とコードの整合だけ | `./gradlew documentationTest` | 数秒。設計文書やソースの Javadoc を変えたときに動く |
| 書式をそろえる | `./gradlew spotlessApply` | 数秒 |
| 画面の層の受入シナリオ（`@ui`。Playwright と axe-core） | 初回だけ `./gradlew playwrightInstall`、以降 `./gradlew uiTest` | 約 2 分 |
| 設計ドキュメントの生成 | `./gradlew jigReports` | 十数秒 |

SonarQube の品質ゲートは、リポジトリのルートで次を実行する。ローカルの SonarQube（既定は <http://localhost:9000>。ポートを変えた場合は `.env` の `LOCAL_SONAR_PORT` か `SONAR_HOST_URL`）と、`.env` の `SONAR_TOKEN`・`SONAR_PROJECT_KEY` が要る。手順は [SonarQube ローカル環境セットアップ手順書](../../reference/SonarQubeローカル環境セットアップ手順書.md) に従う。

```bash
npx gulp sonar-local:status   # 起動しているか
npx gulp sonar-local:check    # スキャンして Quality Gate を判定する（不合格なら失敗で終わる）
npx gulp sonar-local:issues   # 指摘の一覧
```

## 5. 生成物の場所

| 生成物 | 場所 | 作るコマンド |
| :--- | :--- | :--- |
| テストの結果 | `build/reports/tests/test/index.html`、`build/reports/tests/uiTest/index.html` | `check`、`uiTest` |
| 受入シナリオ（Cucumber） | `build/reports/cucumber/cucumber.html`（業務ルール層）、`build/reports/cucumber-ui/cucumber.html`（画面の層） | `check`、`uiTest` |
| カバレッジ（JaCoCo） | `build/reports/jacoco/test/html/index.html` | `check` |
| 静的解析 | `build/reports/checkstyle/`、`build/reports/spotbugs/main.html` | `check` |
| JIG（用語集、パッケージ関連、業務ルール一覧） | `build/jig/index.html` | `jigReports` |
| Spring Modulith のモジュール図 | `build/spring-modulith-docs/` | `test`（`ModuleDocumentationTest`。`check` でも動く） |

生成物はリポジトリにコミットしない。CI（`.github/workflows/cargo-tracker-ci.yml`）では、成果物 `cargo-tracker-reports`・`cargo-tracker-ui-reports` に 14 日残る。

## 6. ディレクトリ構造

```text
apps/cargo-tracker/
├── build.gradle                  依存、品質チェック、カバレッジの閾値（CI の閾値の正）
├── config/                       Checkstyle の規則、SpotBugs の除外（理由つき）
└── src/
    ├── main/java/com/example/cargotracker/
    │   ├── quotation/            見積りコンテキスト（interfaces・application・domain・infrastructure）
    │   ├── identity/             アクセス・監査コンテキスト
    │   ├── shared/               共有カーネル（domain）と注釈の語彙（annotation.ddd）
    │   └── platform/             技術の部品（MyBatis の型ハンドラー）
    ├── main/resources/
    │   ├── db/migration/         Flyway（common と {vendor}）
    │   ├── db/callback/          Flyway のコールバック（PostgreSQL の権限）
    │   └── templates/            Thymeleaf の画面
    └── test/
        ├── java/                 ユニット・アーキテクチャ・受入シナリオのステップ定義・統合・Web・画面の層
        └── resources/features/   受入シナリオ（日本語 Gherkin）
```

パッケージ構成と依存の規則は [バックエンドアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、テストの階層は [テスト戦略](../../design/cargo-tracker/test_strategy.md) を参照する。

## 7. Git の規約

コミットは Conventional Commits に従い、1 コミット 1 変更にする（`git-commit` スキル）。push すると、`apps/cargo-tracker` の変更で CI の `check` と `ui` の 2 つのジョブが動く。

## 8. よくあるつまずき

| 症状 | 原因と対処 |
| :--- | :--- |
| 統合テストや `bootTestRun` が Docker に接続できない | Docker が動いていない。Docker Desktop などを起動する |
| `uiTest` がブラウザを見つけられない | `./gradlew playwrightInstall` を一度実行する |
| `check` が書式で失敗する（`spotlessCheck`） | `./gradlew spotlessApply` で書式をそろえ、書式だけの変更は別のコミットにする |
| `sonar-local:check` が NONE で失敗する | 解析の反映を待つ設定（`sonar.qualitygate.wait`）が働いていない可能性がある。`npx gulp sonar-local:status` でサーバーを確かめる |
