---
type: ADR
title: "ADR-006: Java 25 LTS・Spring Boot 4.1・Spring Modulith でアプリケーションを構築する"
description: "アプリケーションを Java 25 LTS と Spring Boot 4.1 で構築し、モジュール境界の検証とイベント発行記録に Spring Modulith、画面に Thymeleaf + htmx 2 を使う決定。"
tags: [adr, tech-stack]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-01T07:14:57Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-01T07:29:04Z }
---

# ADR-006: Java 25 LTS・Spring Boot 4.1・Spring Modulith でアプリケーションを構築する

開発ガイドライン第 3 章の Spring Platform 構成を採用し、ADR-003 のイベントの永続化に Spring Modulith を使う。

日付: 2026-10-01

## ステータス

承認済み

## コンテキスト

- ADR-001〜005 で、モジュラーモノリス、4 パッケージ構成、永続化したドメインイベント、予約サガ、サーバーサイドレンダリングを決めた。言語・フレームワークは未定だった。
- 開発ガイドライン第 3 章は、Cargo Tracker を Spring Boot 4、Thymeleaf + htmx、ArchUnit で実装し、その版で実際に動かしている。
- ADR-003 は、コミット後・購読前にプロセスが止まってもイベントを失わないことを求める。Spring Modulith はイベント発行記録を DB に持ち、未完了のイベントを再配信する仕組みを提供する。
- 2026-10-01 時点のサポート期限（endoflife.date）:
  - Java 25: LTS。Amazon Corretto 25 は 2032-10-31、Eclipse Temurin 25 は 2031-09-30 まで。
  - Spring Boot 4.1: OSS サポートは 2027-07-31 まで。4.0 は 2026-12-31 まで。
- htmx は 2.0.x が安定系列で、4.0 が 2026-08-28 に正式版になったばかりである。

## 決定

**アプリケーションを次の構成で構築する。**

| 領域 | 採用 |
| :--- | :--- |
| 言語 | Java 25 LTS（Amazon Corretto） |
| 基盤 | Spring Boot 4.1.x（Spring Framework 7、Spring Security 7） |
| モジュール境界・イベント | Spring Modulith 2.1.x（モジュール構造の検証、JDBC によるイベント発行記録と再配信） |
| 画面 | Thymeleaf 3.1 + htmx 2.0.x + Bootstrap 5.3（WebJars で配信） |
| session | Spring Session JDBC |
| ビルド | Gradle 9.x |

Spring Boot は 4.2 の正式版が出たら追従する。依存の版は Spring Boot の BOM に任せ、脆弱性の修正版が先に出たときだけ上書きする。

### 代替案

| 案 | 却下理由 |
| :--- | :--- |
| Java 21 + Spring Boot 3.5 | Spring Boot 3.5 の OSS サポートは 2026-06-30 に終わっている |
| Spring Boot 4.0 | OSS サポートが 2026-12-31 で終わり、MVP 開発中に切れる |
| イベント発行記録を自作する | Outbox 表、再配信、排他、掃除を自分で作ることになる。Spring Modulith で足りる |
| Kotlin、他言語・他フレームワーク | 開発ガイドラインが Spring Platform を前提にしており、手本の実装をそのまま参照できなくなる |
| htmx 4.0 | 正式版から 1 か月余りで、2 からの移行に破壊的変更がある。パイロット後に評価する |
| Redis などによる session 共有 | ミドルウェアが増える。JDBC で足り、session の即時失効（BR-07、BR-14）も DB の削除で実現できる |

## 影響

### ポジティブ

- 開発ガイドライン第 3 章の構成・設定・落とし穴（例: Spring Boot 4 での Flyway 自動構成のモジュール分割）をそのまま参照できる。
- ADR-003 のイベントの永続化と再配信を、自作せずに実現できる。
- モジュール間の依存を Spring Modulith で、層の規則を ArchUnit で検証でき、ADR-001 の境界を機械で守れる。

### ネガティブ

- Spring Boot 4.1 のサポート期限（2027-07-31）がパイロット期間と重なるため、4.2 へのアップグレードを計画に入れる必要がある。
- Spring Modulith のイベント発行記録の表と、その掃除の方針が必要になる。
- 多要素認証（TOTP）の実装方法は未検証で、最初の Bolt でスパイクが要る。

## コンプライアンス

- ビルドの toolchain が Java 25 であること、Spring Boot のプラグインが 4.1 以降であることをビルド設定で固定する。
- Spring Modulith の `ApplicationModules.verify()` と ArchUnit のテストを CI で実行する。
- Bolt の開始時に依存の更新レポートを確認する。

## 備考

- 著者: anthropic/claude-opus-5-5（AI の提案。human:kakimomokuri が 2026-10-01 に承認）
- 関連文書: [技術スタック](../../design/cargo-tracker/tech_stack.md)、[第 3 章](../../article/03-spring-modular-monolith.md)
- 関連 ADR: ADR-001、ADR-003、ADR-005、ADR-007
