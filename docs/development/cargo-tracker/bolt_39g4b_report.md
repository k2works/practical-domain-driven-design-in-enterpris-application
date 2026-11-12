---
type: Plan
title: "Bolt 39g4b 終了報告 - 最後の区間の航海の到着予定で最新の到着見込みを動かす"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-12T07:30:00Z }
---

# Bolt 39g4b 終了報告 - 最後の区間の航海の到着予定で最新の到着見込みを動かす

## 承認の議題（T-36。先に確かめてほしいこと）

追跡は DE-12（航海の採用値）を受け、その航海を最後の区間に持つ追跡記録を追跡番号の順に行のロックで押さえて読み直し、航海の到着予定を受けさせるようにしました（`VoyageArrivalEventHandler`）。追跡記録は自分の予定で最後の揚地への到着を結び（`VoyageArrivalRule`。出発のある積地がちょうど 1 つ、その後ろの到着のある最初の揚地）、保存した公開の順より厳密に新しい知らせだけを受け、最新の到着見込み（実績の遅れの見込みとの遅い方。当初より早めない）と最終取得時刻（受けた知らせの取得時刻を含める）だけを導き直します。計画の確認ポイント 1〜9 は承認済み（2026-11-12）のとおりに実装しました。スキーマの変更は承認済みのマイグレーション 1 つ（`tracking_record` の 4 列とコメント、CHECK `ck_tracking_record_voyage_arrival`、`scheduled_leg` の索引 `ix_scheduled_leg_voyage`）だけです。認可・外部連携・ArchUnit の例外・新しいコンテキスト間の依存は足していません。

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **規律の違反が 3 種類あった**（標準入力・一時ファイルの置き場所）: (a) テストのファイルを `sed` で直すコマンドの末尾に、意味のない `python3 -` とヒアドキュメントを 1 回付けた（Python が対話のモードで止まり、120 秒で裏に回ったので止めた。`sed` の直しは効いていて、ほかの影響はない）。(b) 意味のないコマンド `cat > /dev/null < /dev/null; true` を 1 回打った（39g4 の議題 5 と同じ形。影響はない）。(c) Red のコンパイルの記録を 1 回リポジトリの外の `IdeaProjects/redlog.txt` に書き（すぐ消した）、別のコマンドに紛れた `cp` で `/c/PC2024~1` への写しを 1 回試みた（失敗して何も作られていないことを確かめた）。どれもスクラッチパッドを使うべきだった | T-247（標準入力と `cat >` を拒むフック）を W7 の締めで諮るときに、`python3 -`・ヒアドキュメント・スクラッチパッドの外への書き込みも拒む対象に足す。2 Bolt 続けて同じ形が出たので、フックで止める方が確実 | 下の「ふりかえり」 |
| 2 | **計画の外の小さな形の判断**: (a) 受入シナリオのステップ定義が DE-12 の型に依存しないよう、受入テストのポート `VoyageAdoptionArrival`（テストのファイル。前例 `ExternalMilestoneArrival`）を足した（計画 CP-9 の新しいファイルの一覧にない）。(b) マッパーの UPDATE の 4 列の引数を、マッパーの入れ子のレコード `TrackingRecordMapper.VoyageArrivalColumns` にまとめた（引数が 10 を超えるため）。(c) 行 6 のテストを、計画の 3 つの既存のクラスでなく `TrackingRecordVoyageArrivalTest` の入れ子にまとめた（前提の航海の到着予定を受けた追跡記録を 1 か所で作るため） | この形で承認する | 下の「計画から変えたこと」 |
| 3 | **テストで直接確かめていない分岐**: 行 13 (c) の内容の欠けのうち「出典の参照が空」「取得時刻が null」「寄港の並びが null」は、共有カーネルの `Source` と DE-12 の型（`List.copyOf`）が作れないので listener の単体では流せない。寄港の並びの null は値の側（`VoyageArrival.portCallsOf`）で確かめ、参照の空と取得時刻の null の分岐（`VoyageArrival.orderOf`）はテストがない。また行 7 (f)（下書きの取得時刻だけ）は開始の状態だけを見ている（下書きは 39g3 の既存のテストと行 6 (c) が確かめる）。変異 M15 のメモリの実装の側（最後の区間で絞る）も縛っていない（計画は「メモリの実装の行 4 も同じ組で縛る」） | 39g4c の最初に、`VoyageArrival.orderOf` の 2 つの分岐とメモリの実装の行 4 (c) を単体で足す（数分。本体は変えない） | `VoyageArrivalEventHandlerTest`、`InMemoryTrackingRecordRepository` |
| 4 | **ステップ 1 のコミットがステップ 2 の後になった**: 設計の文書の直し（ステップ 1）をサブエージェントに任せ、並行してステップ 2 のリファクタリングを進めたので、コミットの順が `4ddea8706`（ステップ 2）→ `4c25053d1`（ステップ 1）になった。中身は計画のとおり | 記録として承認する | 下の「成果」 |
| 5 | **計画の文書の食い違い**（ステップ 1 で見つけた。計画は直していない）: 計画の ER 図（:327・:339）は `last_acquired_at` を「結べた航海の取得時刻を含める」、索引を `ix_scheduled_leg_voyage_number` と書いているが、決定 (6)・(8) は「受けた（結べなくても）」と `ix_scheduled_leg_voyage`。実装と設計文書は決定の文に合わせた。ほかに release_plan の W7 の週の表（:1197）が「Bolt 39g4 の終了報告…（承認待ち）」のままだったので、この報告の同期で「承認済み」に直した | 計画の図は直さず（承認済みの記録）、この報告で決定の文が正と記録する | 計画 :327・:339、release_plan :1197 |
| 6 | **既存のデータ**（決定 (6)）: 39g4b の前に開始した追跡記録は、次の DE-12 を受けるまで 4 列が NULL で、見込みは採用の実績だけで決まる（今と同じ）。補正のマイグレーションは作っていない | 計画のとおり | 下の「既知の課題」 |
| 7 | **前の Bolt の同時実行テスト（`H2TrackingRecordLockTest` の行37_行38）**: この Bolt の実行では落ちなかった（守る既存のテストの 992 件、`check` の 5,653 件、SonarQube の 2 回の `test`、変異の M17 の `MyBatisTrackingRecordLockIntegrationTest`）。再現の条件は取れていない | 何もしない（落ちたときに再現の条件を取る） | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39g4b 回（W7 の 33 本目） |
| 期間 | 2026-11-12（計画の承認 `5ed171fda` から終了報告まで） |
| 対象 | 追跡: `PublicationOrder`・`VoyagePortCall`・`VoyageArrival`・`VoyageArrivalRule`・`VoyageArrivalEventHandler`（新規）、`MilestoneCandidateRule`（公開の順を使う）、`LatestEstimateRule`（3 引数の形）、`TrackingRecord`（`receiveVoyageArrival`・`voyageArrival`・導出と組み立て）、`TrackingRecordRepository`（名前の変更 `findByTrackingNumberForUpdate`、照会 `findTrackingNumbersByFinalLegVoyage`）、`MyBatisTrackingRecordRepository`・`TrackingRecordRow`・`TrackingRecordMapper`（Java と XML）、`TrackingConfiguration`、`TrackingRecordCommandService`・`ExternalMilestoneEventHandler`（名前の追従）、`CustomerTrackingView`・`package-info`・S-11 のテンプレートのコメント、外部データの `VoyageAdoptedValueUpdated` の Javadoc（型は変えない）。マイグレーション `V20261114100000__add_tracking_record_voyage_arrival.sql`。マニュアル 9 章・5 章・12 章・用語集 |
| GitHub | なし（US を数えない技術の Bolt） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 39g4b 計画](bolt_39g4b_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model（用語の 4 行を直し、5 行（公開の順・航海の寄港・航海の到着予定・航海の到着予定の規則・航海の到着予定のイベントハンドラー）を足した。集約の図、T-INV-10、規則の表、DE-12 の購読者、リポジトリ、「Bolt 39g4b で決めたこと」、39g2〜39g4 の節の 39g4c の注）、data_model（論理モデル・注・Bolt 39g4b で足す列と索引の節・索引の表）、ui_design（S-11・S-12・C-10・:1689）、non_functional（PERF-06 の置き場所、FRS-03）、ADR-004（Bolt 39g4b の項）、release_plan（39g4b の行、D-105 の注、承認待ちの直し）、development_strategy（W7 の行）、test_strategy（T-INV-08〜10）。変えた 2 つの図は `plantuml -charset UTF-8 -tsvg` で描画を確かめた | `4c25053d1` |
| 2. 守る既存のテストとリファクタリング | Red を書く前に守る既存のテスト（計画の一覧の Java のテスト・業務ルール層の受入シナリオ・横断の検査）を流し、992 件・失敗 0 を確かめた（T-217）。DE-12 の購読の数を前提にするテストは、拒否の経路で 0 件を数えるものだけで、購読の数に依存していなかった。公開の順を `PublicationOrder` に出して `MilestoneCandidateRule` が使い（`MilestoneCandidateRuleTest` は直さずに通った）、出した比べ方を縛る `PublicationOrderTest`（行 1）を書いた。押さえて読む操作を `findByTrackingNumberForUpdate` に名前を変え、呼び手 9 ファイルは名前の追従だけにした。出した後の追跡のテスト・受入シナリオ 906 件が通った | `4ddea8706` |
| 3. Red | 受入シナリオ（行 16）と状態の組み合わせの単体・統合（行 2〜15）を同じ段で書き、テストのコンパイルが新しい型とメソッドがない理由で落ちることを確かめた（javac の表示で 126 件。シンボルがない 118、`estimate` の引数が合わない 6、上書きのない `@Override` 2） | `28a8deecc`（Red と Green を同じコミット。T-254） |
| 4〜7. 規則・集約・保存と照会・listener | 値と規則、集約の受け取りと導出、マイグレーションと INSERT・UPDATE・組み立て・新しい照会・メモリの実装、listener と組み立て、配信と直列の統合テスト。テストの誤り 4 件を直した（既存の 2 つのテストのダブルと引数の数、`Source` が作れない欠けの組、列の型が `Timestamp`） | `28a8deecc` |
| 8. 画面の層のシナリオと動画 | 行 17（`voyage_arrival_estimate_ui.feature`、`@demo-bolt-39g4b/voyage-arrival-estimate`）。1 回目で通った | `f32392ed9`、`e44d1a59c` |
| 9. マニュアル | 9 章（S-11 の ④⑤、S-12 の ②、「最新の到着見込みと最終取得時刻」の節に航海の到着予定の決まり 3 行と最終取得時刻）、5 章（C-10 の ②）、12 章（航海の採用値は追跡の見込みにも使われる）、用語集（最新の到着見込み・最終取得時刻を直し、航海の到着予定を足した）。画面の項目は変えていないのでキャプチャは撮り直していない | `c4136fa34` |
| 10. 品質ゲート | 下の「品質ゲート」。SonarQube の直し 2 件（テストのコード） | `31856c70f` |
| 11. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | 1 回目の `check`（`--continue`）: `test` 5,653 件、失敗 0（Bolt 39g4 の 5,573 件から +80）。`documentationTest` 5 件、失敗 0（`LivingGlossaryConsistencyTest` は新しい値 3 つの用語の行と Javadoc の最初の語を確かめた）。`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck`・`spotbugsMain` を含めて成功。最後の直し（SonarQube の 2 件。テストのコードだけ）の後に SonarQube の `test`（全件）と `spotlessJavaCheck`・`checkstyleMain`・`checkstyleTest`・`spotbugsMain` を流し直した（すべて成功。test 5,653 件・失敗 0） |
| `uiTest` | 1 回目: 224 シナリオ、失敗 0（JUnit の XML では 448 件、うち 224 件はシナリオの外枠で skipped と数えられる。39g4 の 223 から +1）。最後の直しの後の 2 回目も 224 シナリオ、失敗 0 |
| `manualScreenshots` | 成功。画面の項目を変えていないので、日付だけ変わったキャプチャは HEAD に戻した（下の `demoVideo` の撮り直しとあわせて 147 件） |
| `demoVideo` | 成功。`bolt-39g4b/voyage-arrival-estimate.webm` だけを残した |
| 変異 | 計画の 22 個（M18 を SQL とメモリの実装の 2 つに分けて 23 個）をスクラッチパッドの使い捨てのスクリプトで 1 つずつ当て、決めたテストを流した。落ちたかどうかは JUnit の XML の落ちたテストの名前で決め、名前のない exit 1 は「起動の失敗」と分けた（39g2 の Try）。23 個すべて落ち、起動の失敗はなかった。1 回目は複数行の 5 個（M1・M2・M7・M14・M19）が、spotless が書いた Java の改行が CRLF なので当てられず、改行を合わせて流し直した |
| SonarQube | 1 回目は Quality Gate が FAIL（新しいコードの違反 2 件。どちらも `MyBatisTrackingRecordRepositoryIntegrationTest`: `containsEntry` を使う、例外を投げうる呼び出しが複数のラムダ）。直した後の 2 回目は PASS（新しいコードのカバレッジ 96.1%、重複 0.01761%、違反 0。全体のカバレッジ 95.9%） |
| ArchUnit | `LayerArchitectureTest`・`DddAnnotationArchitectureTest`・`MapperSchemaArchitectureTest`・`PublicApiArchitectureTest` を含む `test` が通った。規則の例外は足していない。Modulith の検証も通った（追跡の `allowedDependencies` は変えていない） |
| マイグレーション | H2（受入テストの組み立てと画面の層・`manualScreenshots`・`demoVideo`）と PostgreSQL（Testcontainers の `MyBatisTrackingRecordRepositoryIntegrationTest`・`SchemaCommentIntegrationTest`・配信と直列の統合テスト）で適用された。CHECK は PostgreSQL の統合テスト（行 12）で、索引は `pg_indexes` で確かめた |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| CI | **流していない**。指示により push していないので、CI は走っていない |
| PERF-06（行 20） | **W7 の締めへ送った**（人の側の CI の手動のジョブ `performance`。push していないので流せない。決定 (1)・(7) の打ち切りの順のとおり） |

### 変異

| # | 変異 | 落ちたテスト（行） |
| :--- | :--- | :--- |
| M1 | 見込みに航海の到着予定を使わない | `LatestEstimateRuleTest` 行 3 (b)・(e) ほか、受入シナリオ（行 16 の遅い方） |
| M2 | 航海の到着予定を常に優先する | `LatestEstimateRuleTest` 行 3 (c)・(d) ほか |
| M3 | 到着の実績より航海の到着予定を優先する | `LatestEstimateRuleTest` 行 3 (f)、受入シナリオ（到着の実績の後） |
| M4 | 当初の到着予定で抑えない | `LatestEstimateRuleTest` 行 3 (c) |
| M5 | 積地の後ろでなく揚地の最初の寄港を使う | `VoyageArrivalRuleTest` 行 2 (d) |
| M6 | 積地が 2 つでも最初の積地で結ぶ | `VoyageArrivalRuleTest` 行 2 (e) |
| M19 | 到着のない揚地の寄港でも揚地にする | `VoyageArrivalRuleTest` 行 2 (h) |
| M20 | 出発のない寄港も積地にする | `VoyageArrivalRuleTest` 行 2 (i) |
| M7 | 公開の順を公開した時刻だけで比べる | `PublicationOrderTest` 行 1 (b)・(c)、`TrackingRecordVoyageArrivalTest` 行 5 (b) |
| M8 | 同じ順（再配信）でも受ける | `TrackingRecordVoyageArrivalTest` 行 5 (c) |
| M9 | 古い順でも受ける | `TrackingRecordVoyageArrivalTest` 行 5 (c)・(d)、行 7 (g) |
| M10 | 結べない新しい知らせで前の到着予定を残す | `TrackingRecordVoyageArrivalTest` 行 5 (e) |
| M11 | 完了後でも受ける | `TrackingRecordVoyageArrivalTest` 行 5 (f1)・(f2) |
| M21 | 最後の区間でない航海の知らせも受ける | `TrackingRecordVoyageArrivalTest` 行 5 (i) |
| M12 | 実績が変わったとき保存した航海の到着予定を規則に渡さない | `TrackingRecordVoyageArrivalTest` 行 6 (a)・(d) |
| M13 | 最終取得時刻に航海の取得時刻を含めない | `TrackingRecordVoyageArrivalTest` 行 7 (a)・(c)・(d)・(e) ほか 6 本 |
| M22 | 最終取得時刻に結べた知らせの取得時刻だけを含める | `TrackingRecordVoyageArrivalTest` 行 7 (d)・(e) |
| M14 | 航海の到着予定で次の予定の実績を導き直す | `TrackingRecordVoyageArrivalTest` 行 8 |
| M15 | 照会が最後の区間でなくすべての区間の航海で引く（SQL） | `MyBatisTrackingRecordRepositoryIntegrationTest` 行 4 (c)・(f) ほか 3 本 |
| M16 | UPDATE が航海の到着予定の列を書かない | `MyBatisTrackingRecordRepositoryIntegrationTest` 行 10・11 |
| M17 | listener が行を押さえずに読む | `VoyageArrivalEventHandlerTest` 行 13 (a)・(d) ほか 3 本 |
| M18a | 照会が追跡番号の順に並べない（SQL） | `MyBatisTrackingRecordRepositoryIntegrationTest` 行 4 (d) |
| M18b | 照会が追跡番号の順に並べない（メモリの実装） | `VoyageArrivalEventHandlerTest` 行 13 (a)（逆の順に作った 2 件） |

### 文書の突き合わせ（T-197・T-251）

画面の項目と文は変えていない（S-11・S-12・C-10 の `dt`・列の名前は 39g3 のまま）。マニュアルの 9 章の航海の到着予定の決まり（遅い方、当初より早めない、到着の実績のまま、中間の区間では動かない、結べない並び）と最終取得時刻の文は、計画の決定 (3)・(4)・(8) の文から写した。用語集の「航海の到着予定」は domain_model の用語の行の定義を利用者の言葉に直した。

## 計画から変えたこと

- 受入テストのポート `VoyageAdoptionArrival` を足し、ステップ定義は `MilestoneSteps` に足した（議題 2 (a)。新しいステップの定義のファイルは作っていない）
- マッパーの UPDATE の 4 列をレコード `TrackingRecordMapper.VoyageArrivalColumns` で渡した（議題 2 (b)）
- 行 6 のテストを `TrackingRecordVoyageArrivalTest` に置いた（議題 2 (c)）
- 内容の欠けの例外は `VoyageArrival.InvalidEventValueException` に置き、値の作り方（`voyageNumberOf`・`portCallsOf`・`orderOf`、`VoyagePortCall.of`）で投げる（計画の「欠けの例外は値の側」のとおり。置いた型は計画に書いていなかった）
- 行 13 (c) のうち作れない欠けの組は listener の単体から外した（議題 3）
- 行 15 の統合テストは、最後の区間の航海をテストごとに乱数で作った（共有の DB にほかのテストがコミットした V200 の追跡記録があり、押さえる行が増えるため）
- 受入シナリオ（行 16）は背景で区間 1 の出発の 6 時間遅れを登録し、4 つのシナリオ（遅い方が航海・遅い方が実績・到着の実績の後・中間の区間）にした
- 守る既存のテストのうち画面の層のシナリオは Red の前に個別には流していない（`uiTest` はシナリオを選んで流せない）。Green の後の `uiTest` で全件流した（39g3・39g4 と同じ）
- 動画は送らずに済んだ。PERF-06 の参考の値は W7 の締めへ送った（push しないため）

## デモ項目

- 動画: [`@demo-bolt-39g4b/voyage-arrival-estimate`](../../assets/demo/bolt-39g4b/voyage-arrival-estimate.webm){:target="_blank"}（データ責任者が確定した経路の直行の航海を到着の 1 日遅れた 1 行で取り込み、追跡管理者の S-12・S-11 と荷主の C-10 の最新の到着見込みが 1 日後になり、当初の到着予定は変わらない）
- マニュアル: 9 章・5 章・12 章・用語集を直した（キャプチャは画面が変わらないので撮り直していない）
- デモ環境の画面は開いて確かめていない（push していないので配備もしていない）

## 判断と学び

- 公開の順を値に出すリファクタリングを Red の前に分けたので、航海の到着予定の冪等は `isNewerThan` の 1 行で済み、候補の規則のテストを直さずに比べ方が 1 つになった
- 集約が自分の予定で結ぶ形（開始準備の検証の指摘）にしたので、最後の区間でない航海・完了後・取消済みの判定が listener に漏れず、listener は「引いて、押さえて、受けさせて、保存する」だけになった
- 共有の DB を使う統合テストでは、照会の鍵（航海番号）をテストごとに作らないと、ほかのテストがコミットした行が照会や行のロックに混ざる
- 変異のスクリプトで複数行の文を当てるときは、対象のファイルの改行（spotless の書いた CRLF）に合わせる

### ふりかえり（KPT）

- Keep: 守る既存のテストを Red の前に流した。リファクタリングを Red の前に分けた。変異の表の「効く層」を計画で 1 行ずつ確かめていたので、23 個すべてが見込みのテストで落ちた。設計の文書の直しをサブエージェントに並行して任せ、文書と実装の名前を先に渡した（用語の行と Javadoc の最初の語が 1 回で合った）
- Problem: 禁止の形のコマンドを 2 回（ヒアドキュメントつきの `python3 -`、`cat > /dev/null`）打ち、スクラッチパッドの外に 1 回書いた（議題 1）。39g4 の Try（コマンドを打つ前に見る）を守れなかった。テストで作れない欠けの分岐を残した（議題 3）
- Try: コマンドは 1 つの目的だけを書き、末尾に別のコマンドを足さない。標準入力と書き込み先を拒むフック（T-247）を入れる。計画の状態の組み合わせの表に、その入力が型で作れるかを開始準備の検証の観点に足す

### 既知の課題（後に持ち込む）

- **配備の前**: 39g4b の前に開始した追跡記録の 4 列は NULL（決定 (6)。補正しない。次の DE-12 で埋まる）。39g3・39g4 の件数の確かめと一緒に行うが、4 列は今と同じ振る舞いなので数えなくてよい
- 39g4c: 見込みの根拠の表示（【業務の判断】(aw)）。最初に議題 3 のテストを足す
- W7 の締め: PERF-06 の参考の値（DE-12 の購読が 3 つになった後の配信の完了）、T-247（議題 1）
- W11: 予定を差し替える経路を作るときに、航海の到着予定と順の鍵を空にする
- 共有カーネル: 公開の順が経路設計（`Voyage`）と追跡（`PublicationOrder`）の 2 か所にある（技術的負債）
- Release 1.0 のパイロットの前: 業務の判断 (au)・(av) を業務責任者とデータ責任者に、(aw) を業務責任者と追跡責任者に確かめる（台帳 D-137〜D-139）

## 次の Bolt

39g4c（見込みの根拠の表示）。release_plan の順序のとおり 39h の前。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-12 | 初版作成 | anthropic/claude-opus-5-5 |
