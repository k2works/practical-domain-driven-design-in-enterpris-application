---
type: Runbook
title: "運用タスク手順書 - cargo-tracker のステージング・本番（W10 Bolt 54）"
description: "ステージング・本番の DB 利用者の作成、権限の確認、配備前の確認、配備、戻しの運用タスク（Gulp）の使い方。未実行のコードであること、まだ作っていない運用タスクと、その理由を明記する。"
tags: [operation, runbook]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T22:45:06Z }
---

# 運用タスク手順書 - cargo-tracker のステージング・本番

ステージング・本番への操作（DB への接続、配備、戻し）は、**ここに書いたタスクだけ**で行う（CLAUDE.md「環境操作は運用手順書に従う」）。独自の一時スクリプトは作らない。設計は [Bolt 54 計画](../../development/cargo-tracker/bolt_54_plan.md)、実装は `ops/scripts/operation.js`。

> **注意: このタスクは、実際の AWS 環境に対してまだ一度も実行していない。** AWS のアカウントが未用意だからである（[ADR-020](../../adr/cargo-tracker/020-aws-account-region-environments.md)）。引数の検査・本番の確認の規則・ドライランの表示だけを、テスト（`node --test ops/scripts/operation.test.js`）と実行で確かめた。最初の実行は、必ず `--dry-run` の後に、ステージングで行う。

## 共通

- 環境は `--env staging|production`（必須）。
- 本番は `--confirm production`（環境名の打ち直し）がないと動かない。
- `--dry-run` は、実行する内容だけを表示する（何もしない）。
- 手元に AWS CLI（`aws`）、Session Manager Plugin、`psql`、`gh` が要る。
- **AWS の認証**: 運用用の IAM ロールで `aws sso login`（または同等のプロファイル）を済ませる。必要な権限は、Secrets Manager の読み取りと作成（`cargo-tracker-<env>/db/*`）、SSM の `StartSession`（踏み台への `AWS-StartPortForwardingSessionToRemoteHost`）、ECS の `DescribeServices`・`ListTaskDefinitions`・`UpdateService`。本番は MFA を必須にする（bolt_54_plan.md の D-2）。**ロールの定義はまだ Terraform に書いていない**（持ち越し H-9。アカウントの用意の後に決める）。
- **GitHub の認証**: `gh auth login` を済ませる。配備のワークフローの起動と CI の結果の読み取りに使う。
- **GitHub の Environment `production`**: 必須のレビュアーを付ける設定は、リポジトリの設定で人が行う。付いていないと、本番への昇格が承認なしで進む。配備の前に、付いていることを確かめる。
- ローカルのポート 15432 が空いていること。トンネルが残っているとタスクは失敗する（別の環境の DB につながるのを防ぐ）。
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
| `npx gulp db:init --env staging` | DB 利用者 `cargo_tracker_app`・`cargo_tracker_migrator` を作り、アプリケーション利用者に **DB の 1 文の timeout（`statement_timeout = 5s`）**を設定し、パスワードを生成して Secrets Manager（`cargo-tracker-<env>/db/app`・`/db/migrator`）に置く。すでにあれば作らない。権限の付与は Flyway の afterMigrate のコールバックが行う（DA-02） | する（利用者の作成、Secrets Manager への登録） |
| `npx gulp admin:bootstrap --env staging --email <メール> --name <表示名> [--company-name "A 社"]` | **最初のシステム管理者**を作る（H-1）。企業（なければ OPERATOR）・利用者（使えないハッシュ）・役割 SYSTEM_ADMIN・管理範囲（自社）・監査記録（reason `INITIAL_ADMINISTRATOR`）を 1 つのトランザクションで作る（`ops/sql/admin_bootstrap.sql`。PostgreSQL で確かめた）。すでにシステム管理者がいると何もせず失敗する。**作った後、本人にログイン画面の「password を忘れた場合」から password を設定してもらい、初回のログインで TOTP を登録してもらう**（平文もハッシュも手順に出ない） | する（企業・利用者・役割・管理範囲・監査記録の作成） |
| `npx gulp external-source:resume --env staging --source-id <情報源 ID> --reason "<理由>"` | 停止中の情報源を承認済みに戻す（H-2、D-102）。状態（APPROVED・版 +1）と監査記録（`DATA_SOURCE_RESUMED`）を 1 つのトランザクションで書く（`ops/sql/external_source_resume.sql`。PostgreSQL で確かめた）。停止中でなければ失敗する。**前後に、その情報源の判断を待つ隔離の件数を S-17 で確かめる**（SQL も前の件数を表示する）。停止中に取り込んだ隔離は自動では変わらない（データ責任者が S-17 で判断する） | する（情報源の状態・監査記録） |
| `npx gulp db:grants:verify --env staging` | 追記専用の表（印 `[append-only]`）に、アプリケーション利用者の UPDATE・DELETE が付いていないかを確かめる。1 件でもあれば失敗 | しない |
| `npx gulp deploy:preflight --env staging` | 作業ツリーが clean、HEAD の CI が成功、ECS の running と desired が同じ、を確かめる | しない |
| `npx gulp deploy:release --env staging --ref develop` | 配備のワークフロー `cargo-tracker-deploy.yml` を起動する。本番（`--env production --confirm production`）は本番への昇格を含み、GitHub の Environment `production` の必須レビュアーの承認が先に要る | する（配備） |
| `npx gulp deploy:rollback --env staging` | ECS のサービスを、**イメージの違う直前の版**へ戻し、安定するまで待つ（`maintenance:on`・`off` が作った版はイメージが同じなので飛ばす。戻し先はイメージを digest で固定した版に限り、Terraform が登録しただけの版は選ばない）。メンテナンス表示は今の状態を保つ。ECS のサーキットブレーカーの自動の戻しとは別の、人が判断する戻し。**実環境では未実行** | する（配備の戻し） |
| `npx gulp maintenance:on --env staging`・`maintenance:off` | メンテナンス表示の切り替え（ADR-023。計画停止）。今のタスク定義の環境変数 `CARGOTRACKER_MAINTENANCE_ENABLED` だけを変えた版を登録して入れ替え、安定するまで待つ（イメージは変えない）。有効の間、画面の要求はすべて 503 と案内になる。ヘルスチェックは通る。**定期処理とイベントの配信は止まらず、DB への書き込みは続くことがある**。有効の間は OBS-02（5xx の割合）の警告と重大の警報が鳴る（想定どおり）。**有効の間に配備した版も表示を引き継ぐ**ので、終わったら必ず `maintenance:off` する。待ちが時間切れになっても入れ替えの依頼は受け付けられているので、再実行せずに状態を確かめる。**実環境では未実行** | する（配備） |

### 失敗したとき

| タスク | 失敗したときの対応 |
| :--- | :--- |
| `db:init` | 再実行してよい。Secrets Manager の秘密を正とし、すでにある秘密のパスワードを使い、DB の利用者はなければ作り、あればパスワードを秘密に合わせる。トンネルが開けないときは、認証（`aws sso login`）と踏み台の ID を確かめる |
| `db:grants:verify` | 失敗は、追記専用の表にアプリケーション利用者の UPDATE・DELETE が付いているという意味。**配備を止める**。Flyway の afterMigrate のコールバック（`afterMigrate__grant_app_user.sql`）が動いたかを確かめる |
| `deploy:preflight` | 失敗したら配備しない。表示された理由（未コミット、push 前、CI の失敗、ECS が不安定）を直してから再実行する |
| `deploy:release` | ワークフローが失敗したら、ECS のサーキットブレーカーが自動で前のタスク定義へ戻す。戻ったことを `deploy:preflight` で確かめる。戻らないときは `deploy:rollback` |
| `deploy:rollback` | 戻し先がないとき（タスク定義が 1 つしかない）は失敗する。その場合は、人が AWS コンソールで状況を確かめる |

## 配備前の確認表（W7 の議題 3）
**`terraform apply` とタスク定義の版（Bolt 51-4）。** ECS のサービスが動かすタスク定義の版は Terraform の管理の外で、`apply` はサービスの版を変えない（`ignore_changes`）。`apply` がタスク定義を変えると新しい版が登録されるだけで、**サービスには次の配備（`deploy:release`）まで反映されない。** タスク定義を変える `apply` の後は、`maintenance:on`・`off` や `deploy:rollback` より先に配備する（これらは今動いている版を元に新しい版を登録することがあり、それが family の最新になると、次の配備に Terraform の変更が入らない）。`apply` の前に `plan` を読むことは変わらない。**実環境では未確認。**

**警告の配布先（W10-1 の Bolt 57）。** `terraform apply` の前に、変数 `operations_alert_emails`・`data_alert_emails`・`tracking_alert_emails` を指定する（既定値はない。値は運用責任者が決める）。SNS のメールの購読は、届いた確認のメールを承認するまで有効にならない（承認されない購読は数日で消える）。宛先を変えたときは、SNS の画面で古い購読が残っていないかを確かめる。空の一覧を指定すると、購読のないトピックができる。`apply` の後に、3 つのトピックの購読が「確認済み」であることを確かめる。

配備（`deploy:release`）の前に、人が次を確かめる。

1. `deploy:preflight` が成功する。
2. `db:grants:verify` が成功する（移行の後。追記専用の表の保護が外れていない）。
3. 確認待ちの業務の判断（台帳の D-124・D-130〜D-172。[W9 完了報告書](../../development/cargo-tracker/iteration_report-W9.md)の「人に諮る議題」と、各 Bolt の計画の【業務の判断】にある）の件数と、パイロットの前に確かめると決めたもの（D-170 の 24 時間、D-171 の週の区切り、D-166 の通知の宛先）を、業務責任者・プロダクト責任者と確かめる。
4. ステージングで主要フロー（見積依頼 → 経路 → 予約 → 追跡）が動く（受入シナリオ `@ui` をステージングへ向けて流す。環境ができてから）。
5. `/actuator/health`（管理用のポート 8081）が UP を返す。ALB のヘルスチェックが healthy になっている。
6. 本番は、ステージングで確かめたイメージの digest をそのまま昇格する（再ビルドしない）。

## 時点復旧（RB-02）の手順（手動。草案。未訓練・未検証）

DB の論理障害・誤操作で、障害の直前の時点へ戻すときの手順。設計は [運用要件](../../design/cargo-tracker/operation.md) の RB-02、RCV-04（RTO 2 時間）、RCV-07（復旧後の照合）。**この手順の AWS CLI の記述は仕様から書いたもので、実際に実行して確かめていない。** タスク（`db:restore:pitr`・`recovery:reconcile`）はまだ作っていない（持ち越し H-6）。環境ができたら、ステージングで訓練して直す（RCV-08: 本番開始前に 1 回、その後は半年に 1 回）。

1. **書き込みを止める**: ECS の `desiredCount` を 0 にして、アプリを止める。止めた時刻を記録する。このとき OBS-10（重大）の警報が鳴る（想定どおり。関係者に先に知らせる）。**`maintenance:on` はこの手順の代わりにしない。** 止まるのは画面の要求だけで、定期処理と、起動のときのイベントの配り直しは DB に書き続ける（W10-1 の Bolt 59）。利用者には案内を別の手段で知らせる。
2. **戻す時点を決める**: 障害の直前の UTC の時刻を、監査記録とログで特定する。人が決め、運用責任者の承認を得る。
3. **別のインスタンスへ復旧する**（元のインスタンスは消さずに残す）:
   `aws rds restore-db-instance-to-point-in-time --source-db-instance-identifier cargo-tracker-<env>-db --target-db-instance-identifier cargo-tracker-<env>-db-restored --restore-time <UTC の時刻> --db-subnet-group-name cargo-tracker-<env>-db --vpc-security-group-ids <RDS のセキュリティグループ> --no-publicly-accessible`
   復旧したインスタンスが `available` になるまで待つ。
4. **中身を確かめる**: 踏み台のポートフォワードで復旧したインスタンスに入り、障害の前後の行（問題の記録が戻っているか、直前の主要な記録が残っているか）を確かめる。
5. **切り替える**: アプリのタスク定義の接続先（`SPRING_DATASOURCE_URL`）を復旧したインスタンスのエンドポイントにして配備する。**Terraform の管理から外れる**ので、切り替えの後に Terraform の状態と構成を人が揃える（`terraform import` か、元の識別子への入れ替え）。この揃え方は訓練で決める。
6. **復旧後の照合（RCV-07）**: 戻した時点より後に起きたことを、DB の外の記録（外部原本の S3、監査記録の書き出し、メールの送信の記録）と照合して再処理する。外部原本の取込は、取込の履歴にない原本を再取込する。照合の手順（`recovery:reconcile`）は設計がまだない。
7. **確認と再開**: `desiredCount` を戻し、ヘルスチェックと受入シナリオの主要フローを確かめる。利用者に再開を知らせる。
8. **記録**: 復旧した時点、所要時間（RTO と比べる）、照合の結果を残し、5 営業日以内に事後分析を行う。

**設計との差（Terraform）**: 設計は DB の自動バックアップと外部原本・監査記録の書き出しを大阪リージョンへ複製するとあるが、`ops/terraform/` にはまだ複製の設定がない（持ち越し H-9）。時点復旧の訓練の前に足す。

## 負荷テスト（k6。草案。未実行）

画面の応答時間を、非機能要件の PERF-01（p95 1.0 秒・p99 2.0 秒）と PERF-08（同時利用者 50 名）に対して測る。スクリプトは `ops/loadtest/screens.js`（k6）。**まだ一度も実行していない**（手元に k6 がなく、ステージングもない）。

1. 人がブラウザでステージングにログインし、Cookie `SESSION` の値を控える（ログインは TOTP を含むので、スクリプトではしない）。
2. 少ない VU で試す: `k6 run --vus 2 --duration 30s -e BASE_URL=https://<ステージング> -e SESSION_COOKIE=<値> ops/loadtest/screens.js`（`--vus`・`--duration` は `stages` を上書きする）。
3. 結果（p95・p99・失敗率）を残してから、既定の 50 VU の設定で流す。
4. 読み取りだけを行う。**本番には向けない**（スクリプトは URL に `production` を含むと拒否する）。

測れていないもの（持ち越し H-5）: 同じ追跡番号に 1 万行が集中する取込（DE-13）、同時の取込に要る接続のプールの大きさ、経路候補の算出（PERF-03）、取込（PERF-06 は `performanceTest` が別にある）。

## まだ作っていない運用タスクと理由

| タスク（operation.md） | 理由 |
| :--- | :--- |
| `audit:export:verify`（AUD-03、AUD-UI-01） | 書き出しの形式とハッシュの仕様が、まだ設計に書かれていない |
| `db:restore:pitr`・`recovery:reconcile`・`db:failover:drill` | 時点復旧の手順は、Release 1.1 の頭で計画する（Bolt 55-1 は W10 では未着手）。環境ができてから訓練する |
| `kpi:baseline:register` | 画面（S-22）にある。タスクは不要 |

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版（draft。未実行のコード） | anthropic/claude-sonnet-5-5 |
