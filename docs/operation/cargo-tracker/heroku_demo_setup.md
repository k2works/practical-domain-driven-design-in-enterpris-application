---
type: Playbook
title: "Heroku デモ環境セットアップ手順書 - cargo-tracker"
description: "cargo-tracker を dev プロファイルのまま Heroku（Container Registry、Eco dyno）へ配備し、関係者が触って確かめるデモ環境を作る・更新する・止める手順を示す。"
tags: [operation,playbook,setup,heroku]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-06T08:05:00Z }
---

# Heroku デモ環境セットアップ手順書 - cargo-tracker

## 概要

関係者がブラウザで触って確かめるためのデモ環境を、Heroku に作る手順です。位置づけと制約は [ADR-013](../../adr/cargo-tracker/013-heroku-demo-environment.md) を参照してください。

| 項目 | 内容 |
| :--- | :--- |
| アプリ名 | `cargo-tracker-mono-demo` |
| URL | （初回のセットアップの後に記入する） |
| プロファイル | `dev`（H2 のインメモリ、開発用の利用者） |
| dyno | Eco（512 MB）、web 1 つ、region `us` |
| 配備 | Container Registry（`apps/cargo-tracker/Dockerfile`） |
| 費用 | Eco の定額、月 5 USD（アカウントの全 Eco dyno に共通） |

> **注意**: アクセスの制限はありません。URL を知っていれば、だれでも開発用の利用者でログインできます。本物の荷主・利用者・取引のデータを入れないでください。URL は関係者にだけ伝えてください。データは dyno の再起動で初期状態に戻ります。

## 1. 前提条件

（ステップ 3 で記入する: Heroku のアカウントと Eco の契約、Heroku CLI、Docker、ログイン）

## 2. 初回のセットアップ

（ステップ 3 で記入する: `npx gulp heroku:setup`）

## 3. 配備

（ステップ 3 で記入する: develop の CI が緑のコミットから `npx gulp heroku:deploy`）

## 4. 確認

（ステップ 4 で記入する: `npx gulp heroku:status`、`heroku:open`、開発用の利用者でのログイン）

## 5. ログ

（ステップ 3 で記入する: `npx gulp heroku:logs`）

## 6. デモの前に

（ステップ 4 で記入する: `npx gulp heroku:restart` で初期状態に戻す。スリープからの起動の時間）

## 7. 停止と削除

（ステップ 3 で記入する: `heroku ps:scale web=0`、`heroku apps:destroy`）

## 8. 制約とよくあるつまずき

（ステップ 4 で記入する: R14（メモリ超過）、R10（起動のタイムアウト）、Router の 30 秒のタイムアウトと書類の添付、データの消失）

## 関連ドキュメント

- [ADR-013 デモ環境](../../adr/cargo-tracker/013-heroku-demo-environment.md)
- [アプリケーション開発環境セットアップ手順書](application_development_setup.md)
- [Bolt 15 計画](../../development/cargo-tracker/bolt_15_plan.md)
