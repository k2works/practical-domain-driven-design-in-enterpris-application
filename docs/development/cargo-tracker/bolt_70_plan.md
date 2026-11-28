---
type: Plan
title: "Bolt 70 計画と報告 - Spring Boot 4.1.3 への更新と 4.2 の調査"
description: "Bolt 56-1 の報告の議題 6 の実施（技術、SP 0）。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T00:10:43Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-28T00:10:43Z }
---

# Bolt 70 計画と報告 - Spring Boot 4.1.3 への更新と 4.2 の調査

[Bolt 56-1 の報告](bolt_56-1_plan.md)の議題 6 の実施（技術、SP 0）。

**状態**: 方針は議題 6 の推奨として 2026-11-28 に承認され、実施は同日の人の指示（「承認の対象外実施」。直前に挙げた 3 件と解釈した）による。この報告は 2026-11-28 に承認された（human:kakimomokuri。「承認してコミットして push して」）。

## 計画と結果

- [x] (1) Spring Boot のプラグインを 4.1.1 から 4.1.3 に上げた（ほかのライブラリの版は変えていない）
- [x] (2) 全部のゲートを流した
- [x] (3) 4.2 への追従で動く版を、Maven Central の POM で調べた（更新はしていない）

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check`・`documentationTest`・`uiTest` | BUILD SUCCESSFUL（29 分 13 秒）。`test` 6,600 件、`uiTest` 252 シナリオ、`documentationTest` 11 件、失敗 0 |
| SonarQube | 流していない（Java のソースを変えていない） |
| デモ用のイメージの起動、デモ環境への配備 | 確かめていない |

## 4.2 の調査（2026-11-28。Maven Central の POM だけで調べた）

| 項目 | 内容 |
| :--- | :--- |
| Spring Modulith | 2.1.x は Boot 4.1.x が対象（今の 2.1.1 は 4.1.1。2.1.3 は 4.1.3）。**4.2.0 には 2.2.0** |
| MyBatis Spring Boot Starter | 最新の 4.1.0 は Boot 4.1.0 を対象に作られている。4.2 を対象にした版は、調べた時点の一覧にない |
| 管理される版の変化（4.1.3 → 4.2.0） | Spring Framework 7.0.11 → 7.1.0、Spring Security 7.1.3 → 7.2.0、Spring Session 4.1.2 → 4.2.0、Flyway 12.4.0 → 12.11.0、H2 2.4.240 → 2.5.252、JUnit 6.0.3 → 6.1.3、Micrometer 1.17.3 → 1.18.0 |
| 変わらない版 | Testcontainers、Jackson、Thymeleaf、PostgreSQL ドライバー、Tomcat |

4.2 に上げる Bolt で重点的に確かめる箇所（影響があるかは未確認）:

- Spring Security 7.2: 2 段階のログインは要素の権限を使っている（ADR-011）
- Spring Session 4.2: session の直列化の互換（ADR-012。壊れる場合は配備で session を消す）
- Spring Modulith 2.2.0: 発行記録の表と、Bolt 57-2 で使い始めた `EventPublicationRepository`
- H2 2.5: Bolt 58 で、H2 だけが `SELECT EXISTS` の形で違う結果を返した
- MyBatis Spring Boot Starter: 4.2 で動くか

推奨: MyBatis の 4.2 対応を確かめ、Release 1.1 の中で追従の Bolt を立てる。4.1 のサポート期限（2027-07-31）より前に終える。

Spring Modulith を 2.1.1 から 2.1.3 に上げるかは、今回の範囲に入れていない（推奨: 次の依存の更新で上げる）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-28 | 初版 | anthropic/claude-sonnet-5-5 |
| 2026-11-28 | 人の承認を記録した（human:kakimomokuri） | anthropic/claude-sonnet-5-5 |
