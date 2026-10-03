---
type: Design
title: "cargo-tracker 技術スタック"
description: "cargo-tracker のバックエンド・画面・データベース・テスト・ビルド・インフラの技術、バージョン、サポート期限、選定理由、アップグレード計画。"
tags: [design, tech-stack]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-03T05:47:24Z }
stale_after: 2027-04-01T00:00:00Z
verified:
  - { by: human:kakimomokuri, at: 2026-10-01T07:29:04Z }
  - { by: human:kakimomokuri, at: 2026-10-01T09:01:38Z }
  - { by: human:kakimomokuri, at: 2026-10-02T01:37:43Z }
---

# cargo-tracker 技術スタック

## 位置づけ

本書は、[バックエンド](architecture_backend.md)・[フロントエンド](architecture_frontend.md)・[インフラストラクチャ](architecture_infrastructure.md) の各アーキテクチャ設計の引継ぎ（AB-01、AB-06、AF-01、AI-01）に答え、採用する技術を決める。開発ガイドライン [第 3 章](../../article/03-spring-modular-monolith.md) の Spring Platform による実装を第一候補とし、同章の実装で実際に使われている版を出発点にした。

本書の判断は次の ADR に記録する。

| ADR | 決定 |
| :--- | :--- |
| [ADR-006](../../adr/cargo-tracker/006-java-spring-boot-platform.md) | Java 25 LTS・Spring Boot 4.1・Spring Modulith でアプリケーションを構築し、画面は Thymeleaf + htmx 2 で描画する |
| [ADR-007](../../adr/cargo-tracker/007-postgresql-mybatis-flyway.md) | 開発環境は H2、ステージング・本番は PostgreSQL 18 を使い、永続化は MyBatis、スキーマは Flyway で管理する |
| [ADR-008](../../adr/cargo-tracker/008-aws-container-platform.md) | AWS の ECS Fargate・RDS・S3 で実行し、Terraform と GitHub Actions で構築・配備する |
| [ADR-009](../../adr/cargo-tracker/009-bdd-cucumber.md) | 開発戦略として BDD を採用し、受入条件を Cucumber（日本語 Gherkin）で実行可能な仕様にする |

バージョンとサポート期限は 2026-10-01 に endoflife.date と Maven Central で確認した。サポート期限が近いため、本書は 2027-04-01 までに見直す（`stale_after`）。

## 選定の方針

| 方針 | 内容 |
| :--- | :--- |
| ガイドラインに揃える | 第 3 章の構成（Spring Boot、MyBatis、Flyway、Thymeleaf + htmx、ArchUnit）を基本にし、変える場合は理由を残す |
| 版は BOM に任せる | Spring Boot の依存管理（BOM）が管理する版をそのまま使い、個別に版を指定しない。脆弱性の修正版が BOM より先に出た場合だけ上書きし、BOM が追いついたら上書きを外す |
| LTS とサポート期限 | 言語・DB は LTS または長いサポート期限の版を選ぶ。パイロット期間（2027 年）にサポートが切れる版は、切れる前のアップグレードを計画に入れる |
| 出たばかりの版を避ける | 正式版から間もないメジャーバージョンは、事例が揃うまで採用しない（例: htmx 4） |
| BDD で受入条件を実行する | ユーザーストーリーの受入条件を日本語 Gherkin のシナリオにし、Cucumber で自動実行する（ADR-009）。シナリオを生きたドキュメントとして扱う |
| 開発体験を優先する | ローカルでのアプリ起動は H2 にし、Docker なしで素早く起動できるようにする。SQL の正しさは PostgreSQL のテストで担保する（ADR-007） |
| 構成を増やさない | ADR-001 の単一デプロイに合わせ、session ストア・メッセージブローカーなどの追加ミドルウェアを持たない |

## バックエンド

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 言語 | Java（Amazon Corretto） | 25 LTS | アプリケーション実装 | 2032-10-31（Corretto 25） | LTS。第 3 章と同じ。AWS 上で動かすため AWS が保守する配布版を使う |
| フレームワーク | Spring Boot | 4.1.x（2026-10 時点 4.1.1） | アプリケーション基盤、自動構成、Actuator | 2027-07-31（OSS サポート） | 第 3 章と同じ。4.2 が出たら追従する（アップグレード計画） |
| フレームワーク | Spring Framework（MVC・トランザクション・イベント） | 7.x（Boot 管理） | 受信、宣言的トランザクション、アプリケーションイベント | Boot に従う | 第 3 章のとおり、アプリケーションサービスをトランザクション境界にし、BC 間通知にアプリケーションイベントを使う |
| モジュール境界・イベント | Spring Modulith | 2.1.x（2026-10 時点 2.1.1） | モジュール構造の検証、イベント発行記録（JDBC）、未完了イベントの再配信 | Boot 4.1 に従う | ADR-003 の「永続化したドメインイベント」を自作せずに実現する。第 3 章のプラットフォーム要求（Domain Events）にも挙がっている |
| 認証・認可 | Spring Security | 7.x（Boot 管理） | フォームログイン、役割による認可、CSRF、session 管理、ロック | Boot に従う | BR-14・BR-15 の固定役割・ロック・session 失効を標準機能で実現する |
| 多要素認証 | TOTP ライブラリ（候補: `dev.samstevens.totp`） | 未定 | TOTP の生成・検証、登録用 QR | — | BR-14。Spring Security 7 の多要素認証の仕組みと組み合わせる。採用ライブラリは最初の Bolt のスパイクで決める（要確認） |
| session ストア | Spring Session JDBC | Boot 管理 | 複数インスタンス間の session 共有 | Boot に従う | 追加ミドルウェア（Redis 等）を持たずに、DB に session を置く。BR-07・BR-14 の即時失効を DB の削除で実現できる |
| 入力検証 | Jakarta Bean Validation（Hibernate Validator） | Boot 管理 | 画面入力の形式検証 | Boot に従う | 形式の検証だけに使い、業務規則はドメインに置く |
| 永続化 | MyBatis（mybatis-spring-boot-starter） | 4.1.0 | 集約の永続化、照会 | — | 第 3 章と同じ。SQL を明示的に管理し、版・追記専用・スキーマ分割を SQL で確実に表現できる（ADR-007） |
| 運用 | Spring Boot Actuator・Micrometer | Boot 管理 | ヘルスチェック、メトリクス | Boot に従う | 第 3 章の Readiness Patterns |
| 定期処理の排他 | ShedLock（shedlock-spring、shedlock-provider-jdbc-template） | 7.10.x（2026-10 時点 7.10.1、Spring Framework 7 対応） | イベントの再配信・予約サガの再試行・日次の定期処理を 1 インスタンスに限る | — | 複数インスタンス（AVL）で定期処理が二重に動かないようにする。Spring Modulith は再配信の排他を提供しない（ADR-003） |
| メール送信 | Spring Boot Mail（spring-boot-starter-mail）+ Amazon SES の SMTP | Boot 管理 | 荷主への通知（US-22）、password の再設定、荷受人への招待 | — | 通知コンテキストの送信アダプター。SES の API を直接使わず SMTP で送り、ローカルではメールを受けるだけのコンテナで確かめる |
| 多言語 | Spring の MessageSource + Thymeleaf のメッセージ | Boot 管理 | 荷受人向けの画面と招待メールの英語表示（US-10、US-19） | — | 追加のライブラリを使わない |

## 画面

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| テンプレート | Thymeleaf（+ thymeleaf-extras-springsecurity） | 3.1.x（Boot 管理） | サーバーサイドレンダリング | — | ADR-005、第 3 章と同じ |
| 部分更新 | htmx | 2.0.x（2026-10 時点 2.0.11） | 部分更新、確認中・処理中の表示更新 | — | 第 3 章と同じ系列。htmx 4.0 は 2026-08-28 に正式版が出たばかりで、2 から 4 への移行には破壊的変更があるため見送る（アップグレード計画） |
| CSS | Bootstrap | 5.3.x（2026-10 時点 5.3.8） | レイアウト、フォーム、部品 | — | 業務画面の部品が揃う。アクセシビリティ（BR-13）を満たすかは部品ごとに確認し、色だけで状態を表す部品は使わない |
| 静的資産 | WebJars（webjars-locator-lite） | Boot 管理 | htmx・Bootstrap の配信 | — | フロントエンドのビルドを持たない（ADR-005） |

## データベース

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| RDBMS | PostgreSQL | 18 | 本番・ステージング・CI のリポジトリテスト・E2E | 2030-11-14（コミュニティ）／2031-02-28（RDS 標準サポート） | 新規構築のため、最も長くサポートされる版を選ぶ。スキーマ分割、TIMESTAMPTZ（BR-10 の UTC 時点）、行単位のロック（イベント再配信の排他）を使う |
| 開発用 DB | H2（PostgreSQL 互換モード、インメモリ） | 2.x（Boot 管理） | ローカルでのアプリ起動・画面確認 | — | 開発体験を優先する（ADR-007）。`developmentOnly` 依存とし、本番の成果物に含めない |
| 開発の補助 | Spring Boot DevTools | Boot 管理 | ローカル起動での自動再起動・ライブリロード、テンプレートのキャッシュの無効化 | — | 画面と Java の変更を起動し直さずに確かめる（2026-10-03、human:kakimomokuri）。`developmentOnly` 依存とし、本番の成果物に含めない |
| マイグレーション | Flyway（spring-boot-flyway + flyway-database-postgresql） | Boot 管理 | スキーマの版管理 | — | 第 3 章と同じ。Spring Boot 4 では `spring-boot-flyway` が無いとマイグレーションが静かに実行されない点に注意する（第 3 章） |
| JDBC ドライバ | PostgreSQL JDBC | Boot 管理 | DB 接続 | — | 脆弱性の修正版が先に出た場合だけ上書きする |

環境ごとの DB の使い分けは次のとおり（ADR-007）。

| 用途 | DB |
| :--- | :--- |
| ローカルでのアプリ起動・画面確認 | H2（PostgreSQL 互換モード、インメモリ） |
| ドメイン・アプリケーション層のユニットテスト、Cucumber の業務ルールのシナリオ | DB を使わない（リポジトリをテスト用の実装に差し替える） |
| リポジトリ・MyBatis マッパー・イベント配信・取込の統合テスト | Testcontainers の PostgreSQL 18 |
| E2E（画面を通す Cucumber シナリオ） | PostgreSQL 18 |
| ステージング・本番 | Amazon RDS for PostgreSQL 18 |

**SQL の正しさは H2 で判断しない。** マイグレーションとマッパーの SQL は H2 と PostgreSQL の両方で動く範囲で書き、H2 で起動できることを CI のスモークテストで確かめる。

## テスト

| カテゴリ | 技術 | バージョン | 用途 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| テストフレームワーク | JUnit Jupiter | 6.x（Boot 管理） | ユニット・統合テスト | Spring Boot 4 の標準 |
| アサーション・モック | AssertJ、Mockito | Boot 管理 | 集約・値オブジェクトの検証、ポートのモック | Spring Boot の標準 |
| 統合テスト | Testcontainers（PostgreSQL） | 2.0.x（Boot 管理） | 実 PostgreSQL でのリポジトリ・イベント配信・取込のテスト | SQL の正しさは実 DB で確かめる |
| Web テスト | Spring MockMvc | Boot 管理 | 画面のレンダリング、認可、開示制御（BR-07）の検証 | 顧客向け HTML に料金・契約条件・書類が含まれないことを確認する |
| アーキテクチャテスト | ArchUnit、Spring Modulith（ApplicationModules の検証） | ArchUnit 1.5.x、Modulith 2.1.x | パッケージ依存、ドメインのフレームワーク非依存、モジュール間の依存 | 第 3 章と同じ。モジュール間の依存は Modulith、層の規則は ArchUnit で検証する |
| イベントのテスト | Spring Modulith Test（Scenario） | 2.1.x | サガ・イベント配信の結合テスト | ADR-003 の「購読前に停止しても失われない」を確かめる |
| 受入テスト（BDD） | Cucumber-JVM（cucumber-java、cucumber-spring、cucumber-junit-platform-engine）+ JUnit Platform Suite | 8.0.x（2026-10 時点 8.0.3。cucumber-bom で揃える） | 受入条件のシナリオ（日本語 Gherkin）の自動実行 | ADR-009。8.0 系は JUnit 6・Spring Framework 7 に対応している。Spring Boot の BOM が管理しないため、cucumber-bom で版を揃える |
| E2E | Playwright for Java | 1.63.x | 主要フローの E2E。画面を通す Cucumber シナリオのステップ定義からも使う | htmx の部分更新を含む画面を実ブラウザで確かめる |
| アクセシビリティ | axe-core（Playwright 連携） | 4.13.x | 自動のアクセシビリティ検査 | BR-13・NFR-ACCESS-01。自動検査で拾えない項目は支援技術による手動確認で補う |

テストの配分・カバレッジ目標は、テスト戦略（後続工程）で決める。

シナリオの置き場所と実行の階層は次を基本とし、詳細はテスト戦略で決める。

| 項目 | 方針 |
| :--- | :--- |
| フィーチャーファイル | `src/test/resources/features/<context>/` にコンテキストごとに置き、`# language: ja` で書く。ストーリー ID（例: `@US-04`）と業務ルール ID（例: `@BR-10`）をタグにする |
| ステップ定義の階層 | 業務ルールのシナリオは application 層の入力ポートを直接呼ぶ（速い）。画面を通す必要があるシナリオだけ Playwright で実ブラウザを操作する |
| 生きたドキュメント | Cucumber の実行結果（HTML・JSON）を CI の成果物として保存し、受入条件の達成状況を示す |

## ビルド・品質

| カテゴリ | 技術 | バージョン | 用途 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| ビルド | Gradle（Groovy DSL）+ Gradle Wrapper | 9.x（2026-10 時点 9.8.0） | ビルド、依存管理、依存のロック | 第 3 章と同じ。依存をロックし、脆弱性スキャンが依存を確実に読めるようにする |
| 静的解析 | Checkstyle、SpotBugs | Checkstyle 14.3.0、SpotBugs 4.10.4（Gradle プラグイン 6.5.12） | コーディング規約、バグパターン | SpotBugs は Java 25 のクラスファイルを読める版であること。規則は `apps/cargo-tracker/config/` |
| 書式 | Spotless（palantir-java-format） | Spotless 8.10.3、palantir-java-format 2.98.0 | Java の書式の統一と検査 | Bolt 2 で導入（2026-10-02 に human:kakimomokuri が承認）。書式を版で固定し、上げるときは書式だけのコミットに分ける |
| 品質ゲート（Gradle） | SonarQube の Gradle プラグイン | 7.5.0.8588 | ローカルの SonarQube へのスキャン | `npx gulp sonar-local:check` から動かす |
| カバレッジ | JaCoCo | 0.8.15 | カバレッジの計測 | Java 25 対応版 |
| 品質管理 | SonarQube（Community） | — | 品質ゲート | `operating-qt` スキルの前提 |
| 脆弱性スキャン | Trivy | — | 依存・コンテナイメージのスキャン | 依存のロックファイルとイメージの両方を検査する |
| 設計との乖離検出 | JIG、SchemaSpy | JIG 2026.9.1、SchemaSpy 7.0.2 | コード（JIG）と DB スキーマ（SchemaSpy）から設計図を生成 | 設計書と実装の乖離を生成物の差分で検出する。ER 図は jig-erd から SchemaSpy に変えた（2026-10-03、human:kakimomokuri。PostgreSQL 18 に Flyway で当てた実スキーマから表定義まで出せるため）。どちらも `npx gulp docs:generate` で作り、公開サイトに載せる |

## インフラ

| カテゴリ | 技術 | バージョン | 用途 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| クラウド | AWS | — | 実行基盤 | 本プロジェクトのテンプレートと運用スキルが AWS を前提にしている（ADR-008） |
| リージョン | 東京（ap-northeast-1） | — | 本番・ステージング | 仮置き。荷主の所在地と越境移転（NFR-PRIVACY-01）を非機能要件で確認する（要確認） |
| コンテナ実行 | Amazon ECS on Fargate | — | アプリケーションの実行（2 タスク以上） | オーケストレーターを自前で運用しない（ADR-001） |
| データベース | Amazon RDS for PostgreSQL | 18 | 本番・ステージングの DB | 自動バックアップ・時点復旧・Multi-AZ を使う。Multi-AZ の要否は非機能要件で決める |
| オブジェクトストレージ | Amazon S3（バージョニング・Object Lock） | — | 外部原本ファイルの保存 | BR-12、PV-01 の外部原本消失 0 件。保持期間は非機能要件で決める |
| エッジ | Application Load Balancer、AWS WAF、ACM | — | TLS 終端、WAF、ヘルスチェック | — |
| イメージ | Amazon ECR | — | コンテナイメージの保管・スキャン | — |
| シークレット | AWS Secrets Manager | — | DB 接続情報等 | — |
| メール | Amazon SES（東京リージョン） | — | 通知・再設定・招待のメール送信。バウンスと苦情を記録する | 送信の失敗を通知の記録と運用の監視に使う |
| 可観測性 | Amazon CloudWatch（Logs・Metrics・Alarms） | — | 構造化ログ、業務メトリクス、アラート | Micrometer から CloudWatch へ送る。分散トレースは導入しない（インフラ設計） |
| IaC | Terraform | 1.x | 環境の構築 | `operating-provision` スキルの前提 |
| CI/CD | GitHub Actions（AWS へは OIDC で認証） | — | ビルド・テスト・配備 | AGENTS.md。長期のアクセスキーを持たない |
| ローカル | H2、ローカルファイルシステム | — | アプリの起動に外部のコンテナを必要としない | 開発体験を優先する。外部原本はローカルのファイルシステムに保存するアダプターで代替する。Docker は統合テスト（Testcontainers）と E2E にだけ使う |

## バージョン管理とアップグレード計画

| 技術 | 現行 | 次の版 | 予定時期 | 理由・影響 |
| :--- | :--- | :--- | :--- | :--- |
| Spring Boot（+ Modulith・Security） | 4.1.x | 4.2.x | 4.2 正式版の公開後、次の Bolt 計画で（2026-11 以降の見込み） | 4.1 の OSS サポートは 2027-07-31 に切れ、パイロット期間と重なる |
| htmx | 2.0.x | 4.x | パイロット後に評価 | 4.0 は 2026-08 に出たばかりで破壊的変更がある |
| Java | 25 LTS | 次の LTS | 次の LTS の公開後 1 年以内に評価 | Corretto 25 は 2032-10 までサポートされるため急がない |
| PostgreSQL | 18 | — | 2030 年までに計画 | RDS 標準サポートは 2031-02-28 まで |
| 依存全般 | — | — | Bolt の開始時に更新を確認する | 依存の更新漏れを機械で検出する（Gradle の依存更新レポート） |

## 後続工程への引継ぎ

| ID | 引継ぎ内容 | 引継ぎ先 |
| :--- | :--- | :--- |
| TS-01 | TOTP ライブラリの決定と、Spring Security 7 の多要素認証との統合方法 | 最初の Bolt（スパイク） |
| TS-02 | Multi-AZ の要否、RPO/RTO、S3 Object Lock の保持期間、リージョンとデータ所在地 | 非機能要件 |
| TS-03 | テストの配分とカバレッジ目標、ArchUnit と Modulith の検証ルール、Cucumber シナリオの階層とタグの規約 | テスト戦略 |
| TS-04 | 開発環境・CI・AWS 環境の構築手順 | `operating-setup`、`operating-cicd`、`operating-provision` |
| TS-05 | 負荷テストのツール（Gatling または k6）の選定と追加 | リリース計画（最初の性能テストの Bolt） |

## AI の仮定と要確認

- チームは Java と Spring に習熟していると仮定した。開発ガイドラインが Spring Platform を前提にしていることと、兄弟プロジェクト（cargo-tracker の Java 版）の実績を根拠にしている。
- リージョンは東京を仮置きした。欧州の荷主の個人情報を扱う場合、越境移転の要件でリージョン構成が変わる可能性がある。
- TOTP ライブラリは候補を挙げただけで、評価していない。
