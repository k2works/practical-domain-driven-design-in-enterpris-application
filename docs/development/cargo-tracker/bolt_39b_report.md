---
type: Plan
title: "Bolt 39b 終了報告 - 外部原本の取込と分類の縦（採用・重複・隔離、開始の拒否と開始判定）"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-03T04:25:15Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-03T04:25:15Z }
---

# Bolt 39b 終了報告 - 外部原本の取込と分類の縦（採用・重複・隔離、開始の拒否と開始判定）

## 承認の議題（T-36。先に確かめてほしいこと）

外部原本の取込のサービスを作り、形式の正しい行を冪等性キーで採用・重複・隔離に分けるようにしました。承認済みの情報源がない取込は拒否し、パイロットの開始判定（`PilotReadiness`）を照会できるようにしました。計画の確認ポイント 14（許可リストの変更）は人に個別に諮り、ほかのゲートは推奨のとおり承認として記録しています（Recommend 承認の指示による）。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **SpotBugs の抑止の追加**: `config/spotbugs/exclude.xml` に `externaldata.application.internal` を足した（取込のサービスがリポジトリを持つことへの EI_EXPOSE_REP2。identity・notification の application.internal と同じ形）。層の規則・ADR に当たらず前例と同じ形なので、推奨のとおり進めた | `config/spotbugs/exclude.xml` |
| 2 | **テストのトランザクションの穴**: 継ぐ側のテストのクラスに付けた `@Transactional` は、契約から継いだテストのメソッドに効かない（Spring の TestContext は宣言したクラスとその上の階層だけを見る）。この Bolt の 2 つの契約は契約のクラスに移した。前例の identity の契約のテスト 5 組（`UserStanding`・`UserAssignment`・`AuthenticationFactorRepositories`・`AuditRecordSuspension`・`KpiObservation` の H2・MyBatis）と `H2DevProfileSmokeTest` は共有の DB に行を残している。範囲の外なので W7 の締めに持ち込む（T-231） | 下の「既知の課題」 |
| 3 | **取込の表の追記専用【前の決定の変更】**: Bolt 39a3 の「追記専用は 3 表」を 4 表に変えた（`ingestion` の件数は更新しない）。計画の承認で推奨のとおり決めた | `V20261103130000__mark_external_data_ingestion_append_only.sql`、data_model |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39b 回（W7 の 5 本目。初案の 39b を 39b・39b2・39b3・39b4 に縦に分けた 1 本目） |
| 期間 | 2026-11-03（計画の承認 `daea6c856` から終了報告まで） |
| 対象 | 取込のサービス `IngestionService`、分類 `Classification`・冪等性キー `IdempotencyKey`、情報源・取込・外部原本・受信記録のポートと MyBatis の実装、開始判定 `PilotReadinessQueryService`、開発データ、受入シナリオ |
| GitHub | [#18](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/18)（US-14。クローズは 39h） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 39b 計画](bolt_39b_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | 39b を 4 本に分けた送り先（release_plan・開発戦略・architecture_backend・system_usecase・ui_design・ADR-004）、取込の件数の意味と追記専用（data_model）、用語集のリポジトリと結果の型と分類（domain_model）、`PilotReadiness` の理由の文言。W7 の確認ポイント 20 は列の読み違い（SP ではなく受入条件の数）なので取り下げた | `f5d026c40` |
| 2. 情報源の読み取りと開始判定、開発データ | `DataSource`・`DataSourceRepository`（MyBatis と契約のテスト）、`PilotReadinessQueryService`、組み立て `ExternalDataConfiguration`、データ責任者の開発利用者と情報源 3 つ（承認済み 2・候補 1）、ModularityTest の許可リスト（human:kakimomokuri の決定） | `93000f4e0` |
| 3. 分類の規則と取込のサービス | `IngestionService`（拒否の順は役割 → 情報源の有無 → 承認の状態。拒否のときは何も保存しない。原本の保存に成功してから受信記録）、`Classification.classify`、`IdempotencyKey`、`IngestFileCommand`、`IngestionOutcome`、メモリの実装 | `4e198d306` |
| 4. MyBatis の読み書き | 取込・外部原本・受信記録の MyBatis の実装（追記専用の表は INSERT と数えるだけ）、3 つのポートの契約のテスト（メモリ・H2・PostgreSQL）、取込の表の追記専用の印、取込のサービスの組み立て | `01e1a54ed` |
| 5. 統合と受入シナリオ | `features/external_data/ingest_external_records.feature`（AC2 3 本、AC3・AC4 1 本ずつ）とステップ定義、`ExternalDataIngestionIntegrationTest`（巻き戻っても原本は残る、件数）、受入テストの規則 | `70ec2f075` |
| 6. 品質ゲートと開発レビュー | 下の「品質ゲート」「開発レビューの指摘と扱い」 | `a366d927f` |
| 7. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` の `test` 3716 件、失敗 0（Bolt 39a3 の 3646 件から +70）。`documentationTest` 5 件、失敗 0。1 回目の `check` は SpotBugs の EI_EXPOSE_REP2 3 件で落ちた（議題 1 で直した） |
| 変異 | V1〜V10 のすべてが落ちた。V2（冪等性キーから外部版を外す）は見込みの取込のサービスの単体では落ちなかったので、外部版の違う行を採用にするテストを足し、落ちることを確かめ直した。V4（承認の状態の判定）は契約のテストだけが守る。V9 は取込の行を受信記録より先に書くので DB の列の変異を作れず、結果の件数の変異で確かめた |
| SonarQube | 1 回目は ERROR（テストの違反 12 件: 変数名 `record` 8、lambda の中の呼び出し 3、アサーションの chain 1）。直した後は Quality Gate が PASS（新しいコードのカバレッジ 95.6%、重複 0.027%、違反 0） |
| `okf:check` | ERROR 0 |
| CI | `a366d927f` で `check`・`ui`・`deploy-demo` が成功した（デモ環境に取込の表の追記専用の印と開発データが入った） |

### 開発レビューの指摘と扱い

1 視点（アーキテクト・テスター・プログラマーを兼ねる）。高 2、中 2、低 4。

| # | 指摘 | 扱い |
| :--- | :--- | :--- |
| 1 | 高: `DataSourceRepositoryContract` の継ぎ手の `@Transactional` が効かず、承認済みの情報源が PostgreSQL に残る | 直した（契約のクラスに移した）。移すとテストのトランザクションが初めて効き、同じセッションの MyBatis のキャッシュが前の件数を返したので、統合テストで JDBC の INSERT の後にキャッシュを消す |
| 2 | 高: 前例の identity の契約 5 組も同じ穴。継ぐ側だけの `@Transactional` を拒む規則を足す | 範囲の外。W7 の締めに持ち込む（T-231） |
| 3 | 中: `H2DevProfileSmokeTest` の承認済みの件数 2 は、ほかのテストが行を残すと崩れる | 直した（開発データの 3 つの ID で絞る） |
| 4 | 中: 取込のサービスの単体の追跡番号 `TRK-0001` が `TrackingNumber` の形式に合わない（T-230） | 直した（`CTABCDEFGH2345` など。分類の単体はハッシュの入力だけなので残した） |
| — | 低: 計画のメモリの実装の置き場所、行 9b の取り込み直しのテスト | 直した |
| — | 低: 隔離の注記の時刻の書式（UTC・マイクロ秒） | 39b2（誤りの理由を業務の言葉で）に送る |
| — | 低: 分類の数だけ件数のクエリを流す | 39b4（PERF-06）で 1 本にするか決める |
| — | 懸念: 外部原本のポートの約束が Spring の `DuplicateKeyException` を名指しする | 39b3 で名前付きの例外にするか決める |

## 計画から変えたこと

- 39b2 以降に送る形の確認: CSV からコマンドを組み立てる処理は 39b2（形式の誤りと一緒に決める）。39b はテストとシナリオがコマンドを直接作る
- `DataSource` の record は id・提供元・原本種別・承認の状態だけにし、承認者・承認の時刻・版は持たせない（YAGNI）
- `IngestedRow` は `@Command` の規則に当たるので `IngestFileCommand` の入れ子の record にした
- 受信記録の取込の ID は必須にした（39b に取込によらない受信がない）
- メモリの実装は `externaldata.acceptance` ではなく test の `externaldata.domain.model.aggregates` に置いた（前例 `InMemoryExternalPayloadStorage`）
- 行 9b の同時の取込は契約と単体で確かめ、統合テストは足さない

## デモ項目

- 動画は撮らない（画面は 39c）
- マニュアルは変えない（見える振る舞いは開発データの利用者と情報源だけ。画面の章は 39c で新しく作る）

## 判断と学び

- 継ぐ側のテストのクラスに付けた `@Transactional` は、契約から継いだテストに効かない。行が残っても件数を差で確かめていたので見逃していた。トランザクションを効かせると、JDBC で入れて MyBatis で読むテストは同じセッションのキャッシュに当たる
- ステップ 3〜5 で `check` を流さなかったので、SpotBugs の指摘をステップ 6 で初めて見た。ステップの承認の前に、そのステップで足した本体のクラスに静的解析を当てる

### ふりかえり（KPT）

- Keep: 計画の初稿を先にコミットし、2 つの検証で組み立ての Bean の不足（規則に当たる）を実装の前に見つけた。変異を 10 個当て、落ちなかった V2 にテストを足した
- Problem: サブエージェントへの指示の最初に heredoc の禁止を書いても、ステップ 2 とステップ 5 の担当が 1 回ずつ使い、標準入力を待って止まった（T-204・T-227 の再発）。継ぐ側の `@Transactional` が効かない穴を前例から写した
- Try: T-231: 継ぐ側だけに付けた `@Transactional` を拒む ArchUnit の規則と、前例の契約 5 組の直し（W7 の締めで扱いを決める）。T-232: 本体のクラスを足したステップは、承認の前に `spotbugsMain` を流す。T-233: heredoc の禁止を指示の文ではなく仕組み（Bash の前のフックで `<<` を拒むなど）で守る案を W7 の締めで諮る

### 既知の課題（後に持ち込む）

- 39b2: CSV からコマンドを組み立てる処理、形式の誤りの隔離と値の列、隔離の注記の時刻の書式、OQ-02 で業務責任者に確かめる 6 点、照合で参照されない原本を数える仕組みと VARCHAR の上限
- 39b3: 外部原本のポートの約束の技術の例外、採用値と DE-12〜DE-14、identity の監査の listener
- 39b4: 分類ごとの件数のクエリ、行ごとのコミットの量と接続の数（1 取込の間に接続 2 本）、原本を全体で数えるテストを書かない規約
- W7 の締め: T-231（前例の identity の契約 5 組と `H2DevProfileSmokeTest` の残りの行、規則）、T-233（heredoc）。JDBC で入れて MyBatis で読む契約の統合テストのキャッシュの問題がほかのコンテキストに残っていないか
- 行 9 の統合テストは、テストのあいだだけ承認済みの情報源をコミットする。テストを並行に流すようにしたら件数を差で数えるテストとぶつかりうる

## 次の Bolt

Bolt 39b2（形式の誤りの隔離と値の列）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-03 | 推奨のとおり承認として記録した（議題 1〜3。Recommend 承認の指示による。human:kakimomokuri） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-11-03 | 初版作成 | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 39b 計画](bolt_39b_plan.md)
- [Bolt 39a3 終了報告](bolt_39a3_report.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[データモデル](../../design/cargo-tracker/data_model.md)
- [ADR-004](../../adr/cargo-tracker/004-external-data-ingestion.md)
