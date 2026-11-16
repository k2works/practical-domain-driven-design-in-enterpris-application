---
type: Plan
title: "Bolt 54・54-1 計画 - 運用タスクとランブックの設計（実行の経路・実行者・タスクの一覧）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T04:23:12Z }
---

# Bolt 54・54-1 計画 - 運用タスクとランブックの設計

[W10 計画](iteration_plan-W10.md)の Bolt 54・54-1（技術、SP 0、見積り 200 + 180 分）の**設計**。人が「`operating-script` の手順で設計する（推奨）」を選んだ（2026-11-16）。**セキュリティ（DB の操作・認証情報・管理者の作成）にあたるので、この設計を人が承認するまで実装しない**。

**状態**: 設計は承認済み（2026-11-16、human:kakimomokuri が D-1〜D-5 を「推奨で一括承認」。D-3 は招待リンクの方式で、最初の 1 行は起動時の一度きりの初期化コードで作る）。実装はこれから。`validating-design`: A 終盤の技術 Bolt（テストの入口は手順の机上訓練・Gulp タスクのドライラン）、B `operation.md`「運用タスクの一覧」「DB 利用者と権限の初期化」「アカウントの運用」と CLAUDE.md「環境操作は運用手順書に従う」に従う、C Bolt 51（Terraform）の環境・変数と整合。

## 前提（確かめた事実）

- 運用タスクの名前は `operation.md` が `db:init`・`db:grants:verify`・`deploy:release`・`deploy:rollback`・`maintenance:on/off`・`audit:export:verify`・`kpi:baseline:register` などと定めている。既存の運用スクリプトは `ops/scripts/*.js`（`ssh.js`・`vault.js`・`deploy_demo.js` ほか）。
- RDS と ECS は非公開サブネットにあり（Bolt 51）、手元から直接つなげない。
- ステージング・本番の DB に触る操作は、運用手順書のタスクだけで行う（CLAUDE.md）。独自の一時スクリプトは作らない。
- 最初のシステム管理者と管理範囲の割り当て（W6 から W10 に移した。Bolt 34b）、情報源の `SUSPENDED` から `APPROVED` に戻す手順（D-102。「停止の前後に S-17 で隔離を確かめる」を入れる）、監査記録の抽出（AUD-UI-01。US-17 を W11 で作るまでの代わり）、`audit:export:verify`、基準値の登録（US-21。画面は S-22 にあるので、タスクは不要の見込み）が対象。

## 決めたいこと（人の判断）

| # | 論点 | 選択肢 | 推奨 |
| :--- | :--- | :--- | :--- |
| D-1 | DB への経路 | (a) SSM Session Manager の踏み台（小さな EC2。インバウンドなし）を Terraform に足し、ポートフォワードして手元の `psql` で操作する。(b) ECS の one-off タスク（運用用のイメージに `psql`）を `run-task` で動かす。(c) ECS Exec でアプリのタスクに入る（アプリのイメージに `psql` がない） | (a)。操作の記録が SSM に残り、手元の `psql` で足りる。Terraform の追加は小さい（EC2・IAM・SSM のドキュメント）。AWS の Session Manager Plugin は手元に入っている |
| D-2 | 実行者と承認 | 本番の DB 操作は、システム管理者の役割の人が、IAM の運用用ロール（MFA 必須）で行う。タスクは `--env production` で確認の入力（環境名の打ち直し）を求め、実行を `docs/operation` の実行記録に書く | このとおり |
| D-3 | 最初のシステム管理者の作成 | (a) 運用タスク `admin:bootstrap` が、パスワード再設定の仕組み（招待リンク。`USER_INVITATION` の用途）で最初の 1 人を作り、初回の password は本人がリンクで設定する。DB には平文も hash も置かない。(b) 初期 password を Secrets Manager に置き、アプリの起動モードで読む | (a)。W6 の利用者の登録と招待（US-16）の仕組みを使い、秘密を手順に出さない。ただし最初の 1 人は誰も承認できないので、`user_account` への最小の INSERT（役割 `SYSTEM_ADMIN`・メール）だけを `db:` のタスクで行い、招待のメールはアプリの仕組みで送る必要がある。**この最初の 1 行の作り方は、アプリのコード（起動時の一度きりの初期化）か SQL かを、実装の前に決める** |
| D-4 | 情報源の `SUSPENDED` → `APPROVED` | タスク `external-source:resume`（引数: 情報源の ID、理由）。実行前に S-17 の判断を待つ隔離の件数を表示して確認を求め、実行の後も件数を表示する。変更は `external_source` の状態だけ。監査の記録も書く | このとおり |
| D-5 | タスクの名前 | `operation.md` の名前（`db:init` など）に `--env staging|production` を付ける。`operating-script` の `{カテゴリ}:{環境}:{アクション}` とは違うが、既存の `okf:check`・`sonar-local:scan` と同じ形で、環境は引数にする | `operation.md` の名前を使う（設計文書が正） |

## タスクの一覧（実装は承認後）

| Bolt | タスク | 内容 |
| :--- | :--- | :--- |
| 54 | `db:init` | マイグレーション用・アプリケーション用の DB 利用者の作成（DA-02）。パスワードは Secrets Manager の値を `psql` に渡し、画面とログに出さない |
| 54 | `db:grants:verify` | 追記専用の表の権限を確認する |
| 54 | `admin:bootstrap` | 最初のシステム管理者（D-3）と管理範囲の割り当て |
| 54 | `external-source:resume` | 情報源の `SUSPENDED` から `APPROVED`（D-4） |
| 54-1 | `audit:export:verify` | 監査記録の書き出しとハッシュの確認（AUD-03、AUD-UI-01） |
| 54-1 | `deploy:preflight` | 配備前の確認表（D-124 から D-172 の確認待ちの件数、`okf:check`、ステージングのヘルス、直近の `check` の結果）を表示する |
| 55-1 | `db:restore:pitr`・`recovery:reconcile`・`db:failover:drill` | 時点復旧の手順（実施は環境ができてから） |
| 52-1 | `deploy:release`・`deploy:rollback`・`maintenance:on/off` | 配備のワークフローを呼ぶ薄いラッパー、直前のタスク定義への戻し |

password 再設定の残りのリスク（#49。SEC-08・SEC-20）は、アプリのコードの変更を伴うので、別の Bolt の計画で扱う（W10 では内容の整理だけ）。

## 承認の議題

D-1〜D-5 の選択。特に D-3 の最初の 1 行の作り方（アプリのコードか SQL か）は、認証・認可にあたるので、人が決める。

## 計画

- [ ] (1) 人が D-1〜D-5 を選ぶ
- [ ] (2) 選んだ経路で Terraform に踏み台を足す（Bolt 51 の続き）
- [ ] (3) タスクを `operating-script` の構造で作り、ドライラン（`--dry-run`）で確かめる。運用手順書（`docs/operation/cargo-tracker/`）に載せる
- [ ] (4) 配備前の確認表と実行記録の様式を書く

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版（設計の draft） | anthropic/claude-sonnet-5-5 |
