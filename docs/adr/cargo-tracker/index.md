# cargo-tracker — ADR

cargo-tracker プロジェクトの ADR ドキュメントです。

## ドキュメント一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](./001-modular-monolith.md) | モジュラーモノリスを採用する | 承認済み |
| [ADR-002](./002-domain-logic-pattern-per-context.md) | ドメインロジックのパターンを境界づけられたコンテキストごとに選ぶ | 承認済み |
| [ADR-003](./003-inter-context-integration.md) | コンテキスト間は冪等コマンド・永続化したドメインイベント・オーケストレーション型のサガで連携する | 承認済み |
| [ADR-004](./004-external-data-ingestion.md) | 外部原本は Inbox・隔離・照合の 3 段で取り込む | 承認済み |
| [ADR-005](./005-server-side-rendering.md) | 画面はサーバーサイドレンダリングとハイパーメディアで提供する | 承認済み |
| [ADR-006](./006-java-spring-boot-platform.md) | Java 25 LTS・Spring Boot 4.1・Spring Modulith でアプリケーションを構築する | 承認済み |
| [ADR-007](./007-postgresql-mybatis-flyway.md) | 開発環境は H2、本番は PostgreSQL 18 を使い、永続化は MyBatis、スキーマは Flyway で管理する | 承認済み |
| [ADR-008](./008-aws-container-platform.md) | AWS の ECS Fargate・RDS・S3 で実行する | 承認済み |
| [ADR-009](./009-bdd-cucumber.md) | BDD を採用し、受入条件を Cucumber で実行可能な仕様にする | 承認済み |
| [ADR-010](./010-required-document-storage-transaction.md) | 必要書類のファイルは DB のトランザクションの外で保存し、失敗したときは補償で消す | 承認済み（実装は W10） |
| [ADR-011](./011-mfa-totp.md) | 多要素認証は Spring Security 7 の要素の権限で組み、TOTP は java-otp で作る | 提案（W5 の US-18 の計画で採否を決める） |
| [ADR-012](./012-authentication-principal-and-session.md) | 認証の主体は共有カーネルの型にし、password の段を Spring Security の form login と Spring Session JDBC で作る | 承認済み |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
