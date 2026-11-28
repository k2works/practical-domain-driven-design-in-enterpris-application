---
type: Plan
title: "Bolt 39h 終了報告 - 版の逆転と既存の採用値との矛盾を取込で差異に分類する"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-12T12:30:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-12T12:21:30Z }
---

# Bolt 39h 終了報告 - 版の逆転と既存の採用値との矛盾を取込で差異に分類する

## 承認の議題（T-36。先に確かめてほしいこと）

取込で、同じ外部 ID の最後に取得した採用値より前に取得した外部版（版の逆転）と、主要実績の追跡番号がこの外部 ID の採用値と違う外部版（既存の採用値との矛盾）を差異（`DISCREPANCY`）に分類するようにしました（US-14 AC5、ED-INV-03）。差異は外部原本と受信記録と注記を残しますが、採用値も DE-12・DE-13・DE-14 も作らず、DE-31（`ExternalRecordDiscrepancyFound`）を出し、identity の監査の listener が `EXTERNAL_RECORD_DISCREPANCY_FOUND` を書きます。S-16 の分類別の件数は 4 行になり、理由の領域は「隔離・差異の理由」になりました。計画の確認ポイント 1〜9 は承認済み（2026-11-12）のとおりに実装しました。スキーマ・認可・外部連携・ArchUnit の例外・新しいコンテキスト間の依存は変えていません（監査の書き手の表の呼び出しの名前に `externalRecordDiscrepancyFound` を足しただけで、書き手は同じ listener のまま。Bolt 39d2 の行 13 と同じ扱い）。

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **要確認の案内の文を決定 (7) の一覧の外で直した**: 見出しを「隔離・差異の理由」にしたので、要確認の案内「下の「隔離の理由」で理由とどうすればよいかを確かめてください。」の参照先が無くなる。「下の「隔離・差異の理由」で…」に直した（画面・ui_design・マニュアル・テスト）。12 章の追跡番号が見つからない実績の案内にも、追跡番号を直すと差異になるので手入力の道を括弧で足した | この形で承認する | `result.html`、ui_design の S-16、マニュアル 12 章 |
| 2 | **性能テストのデータは外部 ID ごとに外部版が 1 つだけ**（行 13）: PERF-06 の 1 万件のデータ（航海・主要実績）は行ごとに外部 ID が違い外部版は「1」だけ。照会は行ごとに 1 本増えるが、複数の外部版の採用値をたどる組は測っていない。CI は push していないので PERF-06 は流していない | 39h4（または W7 の締め）で複数の外部版の組を足すかを決める。PERF-06 は push の後の Linux の CI で確かめる | `ExternalDataIngestionPerformanceTest` |
| 3 | **メモリの実装の組み立てを計画から変えた**: 計画は「メモリの実装は外部原本と受信記録のリポジトリを引く（コンストラクターの呼び手 6 か所）」。メモリの受信記録のリポジトリが外部原本のリポジトリを持っているので、そこに `externalRecordOf` を足して引き、呼び手 6 か所は変えなかった | 記録として承認する | `InMemoryReceivedRecordRepository`、`InMemoryAdoptedValueRepository` |
| 4 | **取込の隔離と差異を読む照会の名前は `quarantinedOf` のまま**: SQL を隔離と差異に広げ（:49）、Javadoc を直したが名前は変えていない | 39h2 の `UndecidedQuarantine` の名前の見直しと一緒に決める | `ReceivedRecordRepository.quarantinedOf` |
| 5 | **規律の違反 1 件**: 作業の途中で、意図しないコマンド `python - --version` を 1 回流した（標準入力につなぐ形。何も書かずに 2 分の時間切れで背景に回り、止めた）。ほかは標準入力・ヒアドキュメント・リポジトリの外（スクラッチパッドを除く）への書き込みを使っていない | 記録として承認する。Try に入れた | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39h 回（W7 の 35 本目） |
| 期間 | 2026-11-12（計画の承認 `ee45658d8` から終了報告まで） |
| 対象 | 外部データ: `DiscrepancyRule`・`DiscrepancyReason`・`ExternalIdAdoptedValue`・`ExternalRecordDiscrepancyFound`（新規）、`Classification`、`IngestionService`、`AdoptedValueRepository`（`findByExternalId`）とメモリ・MyBatis の実装（`AdoptedValueMapper` の Java と XML）、`ReceivedRecordMapper.xml`、`IngestionSummaryQueryService`・`IngestionSummaryView`、`ExternalDataIngestionController`・`UndecidedRecordController`、`result.html`。identity: `ExternalRecordAuditEventHandler`・`AuditRecord`・`AuditAction`。マニュアル 12 章・付録 B・用語集と第 12 章のキャプチャ 2 枚 |
| GitHub | [#18](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/18)（US-14。クローズは 39h4） |
| SP | 0（US-14 の 8 SP は 39h4 で数える） |
| ブランチ | develop |
| 計画 | [Bolt 39h 計画](bolt_39h_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model（用語の 4 行・ED-INV-02/03・DE-31・Bolt 39h で決めたこと）、data_model、ui_design（S-16 とワイヤーフレーム、追跡番号の違う隔離の文）、test_strategy、ADR-004、requirements_definition、system_usecase、architecture_backend、user_story、development_strategy、release_plan（39h〜39h4 の行・:892・:895・:914・台帳）。サブエージェントに任せ、名前を先に渡した。変えた 4 つの図は plantuml で描画を確かめた。ステップ 2 の前にコミットした | `3e219fb42` |
| 2. 守る既存のテスト | Red を書く前に計画の一覧の Java のテスト・業務ルール層の受入シナリオ・横断の検査を流し、1,485 件・失敗 0 を確かめた（T-217）。性能テストのデータは外部 ID ごとに外部版 1 つ（議題 2） | — |
| 3. Red | 受入シナリオ（`classify_discrepancies.feature`）と状態の組み合わせの単体・契約・統合（行 1〜11）を同じ段で書き、テストのコンパイルが 95 件で落ちることを確かめた（新しい型・定数 `DISCREPANCY`・`findByExternalId`・要約のコンストラクターの引数がない）。期待が変わる既存の組を直した（下の「計画から変えたこと」） | `59470af88`（Red と Green を同じコミット。T-254） |
| 4〜8. 規則・分類・照会・取込と監査・S-16・案内の文 | Green の後に、期待が変わる既存の組 3 件（分類別の件数の表が 3 行の組 2 件、監査の配信の新しい組の外部版の誤り）を直した。`LivingGlossaryConsistencyTest` で `ExternalIdAdoptedValue` の Javadoc の最初の語を用語集に合わせた | `59470af88` |
| 9. 画面の層のシナリオと動画 | 行 12（`discrepancy_ui.feature`、`@demo-bolt-39h/discrepancy`）。1 回目はステップの assertThat の型の誤りでコンパイルが落ち、直して通った | `4ca249107`、`0c90fb714` |
| 10. マニュアル | 12 章（分類の説明・4 行の表・「隔離・差異の理由」・差異の 2 行・差異の理由の小節・追跡番号の違う隔離の案内・新しい外部版が差異になる場合・今は一覧に出ないこと）、付録 B、用語集。サブエージェントに任せ、実装の文を渡した。キャプチャ 2 枚を撮り直した | `0c90fb714` |
| 11. 品質ゲート | 下の「品質ゲート」。SonarQube の違反 4 件を直した。ui_design の要確認の文を実装に合わせた | `2c569bbf2`、`fd53b4364` |
| 12. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check`（`--continue`）: `test` 5,818 件、失敗 0（Bolt 39g4c の 5,734 件から +84）。`documentationTest` 5 件、失敗 0。`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck`・`spotbugsMain` を含めて成功。最後の直し（SonarQube の 4 件）の後に SonarQube の `test`（全件 5,818 件・失敗 0）と `spotlessJavaCheck`・`checkstyleMain`・`checkstyleTest`・`spotbugsMain` を流し直した（すべて成功） |
| `uiTest` | 226 シナリオ、失敗 0（JUnit の XML では 452 件、うち 226 件はシナリオの外枠で skipped。39g4c の 225 から +1）。最後の直し（SonarQube の 4 件）の後の 2 回目も 226 シナリオ、失敗 0 |
| `manualScreenshots` | 成功。`12-ingestion-result`・`12-ingestion-review`（分類別の件数の表が 4 行、見出し「隔離・差異の理由」）だけを残し、日付だけ変わったキャプチャ 54 件は HEAD に戻した |
| `demoVideo` | 成功。`bolt-39h/discrepancy.webm` だけを残し、撮り直された既存の動画 93 件は HEAD に戻した |
| 変異 | 計画の 15 個にメモリの実装の M15m を足した 16 個を、スクラッチパッドの使い捨てのスクリプトで 1 つずつ当て、決めたテストを流した。落ちたかどうかは JUnit の XML の落ちたテストの名前で決め、名前のない exit 1 は「起動の失敗」と分けた（39g2 の Try）。改行は対象のファイルに合わせた。1 回目はスクリプトが gradlew を見つけられず全部が起動の失敗になったので、呼び方を直して流し直した。16 個すべて落ちた |
| SonarQube | 1 回目は Quality Gate が FAIL（新しいコードの違反 4 件。監査の要約の `;externalId=` の文字列の重複、コントローラーのテストの 1 つの主語への複数の検証 3 件）。直した後の 2 回目は PASS（新しいコードのカバレッジ 96.2%、重複 0.01724%、違反 0。全体のカバレッジ 96.0%、未解決のイシュー 0） |
| ArchUnit | `LayerArchitectureTest`・`DddAnnotationArchitectureTest`・`MapperSchemaArchitectureTest`・`PublicApiArchitectureTest`・`AcceptanceStepArchitectureTest` を含む `test` が通った。規則の例外は足していない（監査の書き手の表の呼び出しの名前に `externalRecordDiscrepancyFound` を足した）。Modulith の検証も通った（`allowedDependencies` は変えていない） |
| マイグレーション | なし（スキーマを変えない。決定 (2)）。差異の受信記録は H2 と PostgreSQL（Testcontainers）の契約のテストで書けた |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| CI・PERF-06 | **流していない**。指示により push していないので、CI と Linux の `performanceTest` は走っていない（議題 2） |

### 変異

| # | 変異 | 落ちたテスト（行） |
| :--- | :--- | :--- |
| M1 | 逆転の比べ方を「以前」にする | `DiscrepancyRuleTest` 行 1 (c)、`IngestionServiceTest` 行 3 (f) と既存の外部版の組、受入シナリオ |
| M2 | 逆転を見ない | `DiscrepancyRuleTest` 行 1 (d)(f)(g)(h3)、`IngestionServiceTest` 行 3 (b)(c)(i)(j)(k)(l)(m)・行 5 (a) |
| M3 | 矛盾を見ない | `DiscrepancyRuleTest` 行 1 (e)(k2)、`IngestionServiceTest` 行 3 (e)・行 5 (b)、受入シナリオ |
| M4 | 逆転の相手を外部版の文字列の順で選ぶ | `DiscrepancyRuleTest` 行 1 (h3)（規則の層で縛った） |
| M5 | 両方のとき矛盾を理由にする | `DiscrepancyRuleTest` 行 1 (f) |
| M6 | 採用値のない外部原本をハッシュだけで分類する | `ClassificationTest` 行 2 (e)(f)、`IngestionServiceTest` 行 3 (i)(j)、受入シナリオ |
| M7 | その外部版に採用値があっても差異を先に見る | `ClassificationTest` 行 2 (c)(d)、`IngestionServiceTest` 行 3 (h)・(k) と既存の隔離の組 |
| M8 | 採用値のない外部原本で理由がないときも差異にする | `ClassificationTest` 行 2 (g)(h)、`IngestionServiceTest` 行 9b |
| M9 | 差異でも採用値を作る | `IngestionServiceTest` 行 3 (b)(e)(i)(j)・行 5、受入シナリオ 4 本 |
| M10 | 逆転の相手を公開した時刻の順の採用値にする | `DiscrepancyRuleTest` 行 1 (j)、`IngestionServiceTest` 行 3 (k) |
| M11 | 照会が提供元・原本種別で絞らない | H2・PostgreSQL の契約の行 7 (e)(f)(g) |
| M12 | 照会が判断の採用値を含めない | H2・PostgreSQL の契約の行 7 (d) |
| M13 | 差異で DE-31 を出さない | `IngestionServiceTest` 行 5 (a)(b)、受入シナリオ 2 本 |
| M14 | S-16 の表から差異の行を落とす | `ExternalDataIngestionControllerTest` 行 6・行 8 (a)(b) |
| M15 | 理由の照会を隔離だけに戻す（SQL） | H2・PostgreSQL の `ExternalDataRecordRepositoriesContract` の Bolt 39h の行 8 |
| M15m | 同じ（メモリの実装） | `InMemoryExternalDataRecordRepositoriesTest` の Bolt 39h の行 8 |

### 文書の突き合わせ（T-197・T-251）

注記の 2 つの文、S-16 の「差異」の行・「隔離・差異の理由」・残りの件数の文、追跡番号の違う隔離の案内の文は、計画の決定 (7) の文から写し、`IngestionServiceTest`・受入シナリオ・`ExternalDataIngestionControllerTest`・`UndecidedRecordControllerTest`・画面の層のステップ・ui_design・マニュアル 12 章・付録 B で同じ文にした。要確認の案内の文は見出しに合わせて直した（議題 1）。キャプチャ `12-ingestion-result` で 4 行の表と見出しを目で確かめた。

## 計画から変えたこと

- 行 1 に組を 3 つ足した（(h3) 外部版 10・G3 と外部版 9・G1 に G2、(k)・(k2) 公開した時刻の順と取得時刻の順が違う 2 つの採用値に CTA・CTB）。M4 を規則の層で落とし、矛盾の相手が D-105 の順であることを縛るため
- 行 3 (k) の「V1 に戻った」採用値は、V1 の取込の受信記録でなく V1 の内容の違う原本の隔離の受信記録に直接置いた（採用値は受信記録ごとに 1 つなので、採用の判断と同じ形にした）
- メモリの実装のコンストラクターの呼び手は変えなかった（議題 3）
- 照会の 1 行の型はマッパーの入れ子の record（`AdoptedValueMapper.ExternalIdAdoptedValueRow`。前例 `ReceivedRecordMapper.UndecidedQuarantineRow`）にし、新しいファイルを作らなかった
- 期待が変わる既存の組: `IngestionServiceTest` の Bolt 39d4 の行 5 (d) は名前を直した（同じ取得時刻で値を直した組は採用のまま）、`AdoptedValueRepositoryContract` 行 15 のメソッドの一覧に `findByExternalId`、`ExternalMilestoneDeliveryIntegrationTest` の行 13 は DE-13 を直接出す形、分類別の件数が 3 行の組 2 件（コントローラー・PostgreSQL）を 4 行に、結果の画面の見出しと残りの件数の文を見る既存の組（コントローラー・画面の層のステップ・性能テスト）。`ExternalDataRecordRepositoriesContract` :271・:860・:892 と `ReceivedRecordDecisionTest` :74-90 は分類の値を回すだけなので、直さずに通った
- 性能テストの監査の配信を待つ件数に差異（DE-31）を足した（今のデータでは 0 件）
- 要確認の案内の文とマニュアル 12 章の 1 か所（議題 1）
- DE-31 は domain_model のイベントの表で DE-14 の直後に置いた（表は業務の順で、DE-30 は最後の行ではなかった。番号は DE-31 のまま）
- 守る既存のテストのうち画面の層のシナリオは Red の前に個別には流していない（`uiTest` はシナリオを選んで流せない。39g3〜39g4c と同じ）
- 動画は送らずに済んだ

## デモ項目

- 動画: [`@demo-bolt-39h/discrepancy`](../../assets/demo/bolt-39h/discrepancy.webm){:target="_blank"}（データ責任者が東京からロッテルダムへの航海を後に取得した外部版 2 で取り込み、経路設計の航海に反映された後に、前に取得した外部版 1 で到着の違う 1 行を取り込むと、取込の結果に「差異 1」と版の逆転の理由が出る。経路設計の航海は外部版 2 のまま）
- マニュアル: 12 章・付録 B・用語集を直し、第 12 章のキャプチャ 2 枚を撮り直した
- デモ環境の画面は開いて確かめていない（push していないので配備もしていない）

## 判断と学び

- 比べる相手を照会でなく規則が選ぶ形にしたので、逆転（提供元の時計）と矛盾（公開した順）で相手を変える決定 (3) を、SQL の並びに頼らずに単体の組で縛れた（M4・M10 は規則の層で落ちた）
- 分類の決め方に「その外部版に採用値があるか」を入れたので、差異の外部原本への再送（再び差異）と、39b3 より前の採用値を持たない外部原本（今の規則のまま）を同じ関数で分けられた
- 見出しの語を変えると、その語を参照する別の文（要確認の案内）も変わる。決定の文の一覧に入っていなくても、参照の検索で拾う

### ふりかえり（KPT）

- Keep: ステップ 1 を先にコミットしてからステップ 2 に入った（39g4c の議題 5 を返した）。設計の文書とマニュアルの直しをサブエージェントに任せ、名前と実装の文を先に渡した（用語の行と Javadoc の最初の語の食い違いは 1 件だけ）。変異のスクリプトを落ちたテストの名前で判定したので、起動の誤りを変異の生き残りと取り違えなかった
- Problem: 意図しないコマンド（`python -`）を 1 回流した（議題 5）。変異のスクリプトの 1 回目が起動の誤りで全部空振りした。画面の層のステップで AssertJ と Playwright の `assertThat` を取り違えてコンパイルが落ちた
- Try: 確かめのつもりのコマンドも、流す前に標準入力につながる形（`-` の引数）でないかを見る。変異のスクリプトは本番の前に 1 つだけ流して起動を確かめる。見出しや語を変えるときは、その語を参照する文を検索して決定の一覧と突き合わせる

### 既知の課題（後に持ち込む）

- **39h2**: S-17 の一覧・件数に差異、判断のファクトリーが差異を受け採用は拒む、判断の画面の文、`UndecidedQuarantine` と `quarantinedOf` の名前（議題 4）、S-16 のリンクの文、マニュアル 12.2
- **39h3**: 判断の画面にこの外部 ID の採用値を示し、古い外部版の隔離の採用を警告する。S-17 の取込で絞る入口
- **39h4**: G-06、受入条件の照合の表、W7 のデモの動画、#18 のクローズと 8 SP。性能テストの複数の外部版の組（議題 2）
- **配備の前**: 39b3 より前の採用値を持たない外部原本の件数を運用手順書のタスクで数える
- **Release 1.0 のパイロットの前**: 業務の判断 (ay)〜(bd) を業務責任者とデータ責任者に確かめる（台帳 D-141〜D-146）
- PERF-06 と CI は push の後に確かめる

## 次の Bolt

39h2（S-17 で差異を一覧に出して却下する）。release_plan の順序のとおり。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-12 | 初版作成 | anthropic/claude-opus-5-5 |
| 2026-11-12 | 推奨のとおり承認として記録した（議題 1〜5。以降は推奨で進める指示による。性能テストの複数の外部版の組は 39h4 か W7 の締めで諮る。T-247・配備前の件数の確認は W7 の締めへ。human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
