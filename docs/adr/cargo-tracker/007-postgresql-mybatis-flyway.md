---
type: ADR
title: "ADR-007: 開発環境は H2、本番は PostgreSQL 18 を使い、永続化は MyBatis、スキーマは Flyway で管理する"
description: "開発体験を優先してローカルのアプリ起動は H2、ステージング・本番と SQL を検証するテストは PostgreSQL 18 を使い、永続化に MyBatis、マイグレーションに Flyway を使う決定。"
tags: [adr, tech-stack]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-01T07:27:33Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-01T07:29:05Z }
---

# ADR-007: 開発環境は H2、本番は PostgreSQL 18 を使い、永続化は MyBatis、スキーマは Flyway で管理する

開発体験を優先してローカルは H2 で起動し、SQL の正しさは PostgreSQL のテストで担保する。

日付: 2026-10-01

## ステータス

承認済み（開発環境を H2、本番を PostgreSQL とする方針は human:kakimomokuri が 2026-10-01 に決定。本 ADR は方式を記録する）

## コンテキスト

- ADR-001 で、DB は 1 つにし、コンテキストごとにスキーマと DB 利用者を分けると決めた。
- ADR-002 で、集約は状態保存とし、版の不変性と追跡の主要実績の追記専用を守ると決めた。
- BR-10 は、時刻を UTC の時点として保存することを求める。
- 開発ガイドライン第 3 章は、MyBatis（JPA は不採用）と Flyway を使い、H2 はローカルでのアプリ起動だけに使う（`developmentOnly`）としている。
- 開発アプローチとして開発体験を優先し、開発環境は H2、プロダクション環境は PostgreSQL とする方針が決まった。初版の草案は全環境を PostgreSQL にし H2 を使わないとしていたが、この方針に合わせて改めた。
- H2 と PostgreSQL には方言差がある（TIMESTAMPTZ の扱い、部分インデックス、ロックの構文など）。H2 だけで SQL を確かめると、本番でだけ失敗する。
- PostgreSQL のコミュニティサポート期限（endoflife.date、2026-10-01 時点）: 18 は 2030-11-14、17 は 2029-11-08、16 は 2028-11-09。RDS の標準サポートは 18 が 2031-02-28 まで。

## 決定

| 用途 | DB |
| :--- | :--- |
| ローカルでのアプリ起動・画面確認 | **H2**（PostgreSQL 互換モード、インメモリ）。`developmentOnly` 依存とし、本番の成果物に含めない |
| リポジトリ・MyBatis マッパー・イベント配信・取込の統合テスト | Testcontainers の **PostgreSQL 18** |
| E2E（画面を通す Cucumber シナリオ） | **PostgreSQL 18** |
| ステージング・本番 | Amazon RDS for **PostgreSQL 18** |

- **永続化は MyBatis**（mybatis-spring-boot-starter 4.1.0）とし、JPA / Hibernate は使わない。
- **スキーマは Flyway** で版管理し、コンテキストごとにマイグレーションの置き場所とスキーマを分ける。
- マイグレーションとマッパーの SQL は、**H2 と PostgreSQL の両方で動く範囲**で書く。PostgreSQL にしかない機能がどうしても必要な場合は、ローカルでの代替手段とあわせて別の ADR で判断する。
- **SQL の正しさは H2 で判断しない。** CI では、PostgreSQL での統合テストに加えて、H2 でアプリが起動しマイグレーションが通ることをスモークテストで確かめる（方言差でローカルだけ起動できなくなることを防ぐ）。

### 代替案

| 案 | 却下理由 |
| :--- | :--- |
| 全環境を PostgreSQL にする（初版の草案） | ローカルの起動に Docker が必要になり、起動も遅くなる。開発体験を優先する方針に合わない |
| 全環境を H2 にする（テストも H2） | 方言差による不具合を本番まで検出できない |
| PostgreSQL 16 / 17 | 新規構築であり、サポート期限が最も長い 18 を選ばない理由がない |
| JPA / Hibernate | 版の追加・追記専用・スキーマ分割を、ORM の自動の更新・遅延読み込みと両立させる注意が要る。第 3 章も採用していない |

## 影響

### ポジティブ

- ローカルでは Docker なしで素早くアプリを起動でき、画面を直して確かめる間隔が短くなる。
- SQL の正しさは実際の PostgreSQL で確かめるため、方言差が本番障害になりにくい。
- 第 3 章と同じ構成になり、手本の実装をそのまま参照できる。

### ネガティブ

- SQL を H2 と PostgreSQL の共通部分で書く制約がかかる。
- H2 でだけ動く・PostgreSQL でだけ動く SQL を書くと、片方のテストでしか気づけない。両方を CI で確かめる必要がある。
- 統合テストと E2E には Docker が必要である。

## コンプライアンス

- 本番の実行クラスパスに H2・Hibernate ORM・JPA が含まれないことをビルドで検証する。
- リポジトリのテストは Testcontainers の PostgreSQL 18 で実行し、H2 でリポジトリのテストを書かない。
- CI で、H2 でアプリが起動しマイグレーションが通ることをスモークテストで確認する。
- 各コンテキストの DB 利用者が、自スキーマ以外に権限を持たないことを PostgreSQL の統合テストで確認する。

## 備考

- 著者: anthropic/claude-opus-5-5（AI の提案。human:kakimomokuri が 2026-10-01 に承認）
- 関連文書: [技術スタック](../../design/cargo-tracker/tech_stack.md)、[第 3 章](../../article/03-spring-modular-monolith.md)
- 関連 ADR: ADR-001、ADR-002、ADR-006
