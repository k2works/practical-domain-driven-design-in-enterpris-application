---
type: Runbook
title: "運用タスク手順書 - cargo-tracker のステージング・本番（W10 Bolt 54）"
description: "ステージング・本番の DB 利用者の作成、権限の確認、配備前の確認、配備、戻しの運用タスク（Gulp）の使い方。未実行のコードであること、まだ作っていない運用タスクと、その理由を明記する。"
tags: [operation, runbook]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T04:27:12Z }
---

# 運用タスク手順書 - cargo-tracker のステージング・本番

ステージング・本番への操作（DB への接続、配備、戻し）は、**ここに書いたタスクだけ**で行う（CLAUDE.md「環境操作は運用手順書に従う」）。独自の一時スクリプトは作らない。設計は [Bolt 54 計画](../../development/cargo-tracker/bolt_54_plan.md)、実装は `ops/scripts/operation.js`。

> **注意: このタスクは、実際の AWS 環境に対してまだ一度も実行していない。** AWS のアカウントが未用意だからである（[ADR-020](../../adr/cargo-tracker/020-aws-account-region-environments.md)）。引数の検査・本番の確認の規則・ドライランの表示だけを、テスト（`node --test ops/scripts/operation.test.js`）と実行で確かめた。最初の実行は、必ず `--dry-run` の後に、ステージングで行う。

## 共通

- 環境は `--env staging|production`（必須）。
- 本番は `--confirm production`（環境名の打ち直し）がないと動かない。
- `--dry-run` は、実行する内容だけを表示する（何もしない）。
- 手元に AWS CLI（`aws`）、Session Manager Plugin、`psql`、`gh` が要る。
- `.env`（Git に入れない）に次を置く。値は Terraform の出力（`bastion_instance_id`・`database_endpoint`・`database_master_secret_arn`）から取る。

| 変数 | 内容 |
| :--- | :--- |
| `OPS_AWS_REGION` | リージョン（既定 `ap-northeast-1`） |
| `OPS_STAGING_BASTION_ID`・`OPS_PRODUCTION_BASTION_ID` | 踏み台の EC2 のインスタンス ID |
| `OPS_STAGING_DB_HOST`・`OPS_PRODUCTION_DB_HOST` | RDS のエンドポイント |
| `OPS_STAGING_DB_MASTER_SECRET_ARN`・`OPS_PRODUCTION_DB_MASTER_SECRET_ARN` | RDS が管理するマスターの認証情報の ARN |
| `OPS_STAGING_DB_NAME`・`OPS_PRODUCTION_DB_NAME` | DB 名（既定 `cargotracker`） |

DB への経路は、SSM Session Manager の踏み台のポートフォワードである（インバウンドなし。操作は SSM のセッションの記録に残る）。パスワードは画面とログに出さない。

## タスク

| タスク | 内容 | 変更するか |
| :--- | :--- | :--- |
| `npx gulp db:init --env staging` | DB 利用者 `cargo_tracker_app`・`cargo_tracker_migrator` を作り、パスワードを生成して Secrets Manager（`cargo-tracker-<env>/db/app`・`/db/migrator`）に置く。すでにあれば作らない。権限の付与は Flyway の afterMigrate のコールバックが行う（DA-02） | する（利用者の作成、Secrets Manager への登録） |
| `npx gulp db:grants:verify --env staging` | 追記専用の表（印 `[append-only]`）に、アプリケーション利用者の UPDATE・DELETE が付いていないかを確かめる。1 件でもあれば失敗 | しない |
| `npx gulp deploy:preflight --env staging` | 作業ツリーが clean、HEAD の CI が成功、ECS の running と desired が同じ、を確かめる | しない |
| `npx gulp deploy:release --env staging --ref develop` | 配備のワークフロー `cargo-tracker-deploy.yml` を起動する。本番（`--env production --confirm production`）は本番への昇格を含み、GitHub の Environment `production` の必須レビュアーの承認が先に要る | する（配備） |
| `npx gulp deploy:rollback --env staging` | ECS のサービスを、直前のタスク定義へ戻し、安定するまで待つ。ECS のサーキットブレーカーの自動の戻しとは別の、人が判断する戻し | する（配備の戻し） |

## 配備前の確認表（W7 の議題 3）

配備（`deploy:release`）の前に、人が次を確かめる。

1. `deploy:preflight` が成功する。
2. `db:grants:verify` が成功する（移行の後。追記専用の表の保護が外れていない）。
3. 確認待ちの業務の判断（D-124・D-130〜D-172）の件数と、パイロットの前に確かめると決めたもの（D-170 の 24 時間、D-171 の週の区切り、D-166 の通知の宛先）を、業務責任者・プロダクト責任者と確かめる。
4. ステージングで主要フロー（見積依頼 → 経路 → 予約 → 追跡）が動く（受入シナリオ `@ui` をステージングへ向けて流す。環境ができてから）。
5. `/actuator/health`（管理用のポート 8081）が UP を返す。ALB のヘルスチェックが healthy になっている。
6. 本番は、ステージングで確かめたイメージの digest をそのまま昇格する（再ビルドしない）。

## まだ作っていない運用タスクと理由

| タスク（operation.md） | 理由 |
| :--- | :--- |
| `admin:bootstrap`（最初のシステム管理者） | 作成する側の「操作者」がいない場合の、作成者・監査の記録の扱いを決める必要がある（認証の中核）。単独の Bolt で設計から行う（人は D-3 で招待リンクの方式を選んだ） |
| `external-source:resume`（情報源の `SUSPENDED` → `APPROVED`） | 状態を変えるときに監査の記録（`audit_record`）を残す経路がない。SQL で状態だけを変えると、監査のない変更が残る。アプリの操作として設計する。手順に入れる確認「停止の前後に、その情報源の判断を待つ隔離を S-17 で確かめる」は、そのときに書く |
| `audit:export:verify`（AUD-03、AUD-UI-01） | 書き出しの形式とハッシュの仕様が、まだ設計に書かれていない |
| `maintenance:on`・`maintenance:off` | メンテナンス表示の仕組みがアプリにない |
| `db:restore:pitr`・`recovery:reconcile`・`db:failover:drill` | 時点復旧の手順は Bolt 55-1。環境ができてから訓練する |
| `kpi:baseline:register` | 画面（S-22）にある。タスクは不要 |

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版（draft。未実行のコード） | anthropic/claude-sonnet-5-5 |
