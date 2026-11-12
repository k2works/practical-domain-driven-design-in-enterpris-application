---
type: Plan
title: "Bolt 39g4 終了報告 - 次の予定の実績が届かない追跡を鮮度超過として示す"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-12T04:30:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-12T04:55:15Z }
---

# Bolt 39g4 終了報告 - 次の予定の実績が届かない追跡を鮮度超過として示す

## 承認の議題（T-36。先に確かめてほしいこと）

追跡記録は、追跡の開始と採用の実績が変わったときに「次の予定の実績の予定時刻」（予定の時点のうち、採用の実績を結んだ最も後の時点の次）を導いて `tracking_record.next_expected_at` に保存するようにしました（規則 `NextExpectedMilestoneRule`。完了後は空。【業務の判断】(aq)）。S-11・S-12 を開くと、照会サービスが時計から得た判定の時刻と保存した値で鮮度を判定し（`FreshnessPolicy`。設定 `cargotracker.tracking.freshness-tolerance=PT36H`。ちょうど 36 時間は超過）、S-11 の現在状態のセルに「鮮度超過」の印、S-12 に次の予定の実績の予定時刻と警告の文を示します。現在状態は変えず（確認中にしない。(ar)）、C-10 は変えていません（(as)）。計画の確認ポイント 1〜8 は承認済み（2026-11-12）のとおりに実装しました。スキーマの変更は承認済みのマイグレーション 1 つ（列 1 つとコメント）だけです。認可・外部連携・ArchUnit の例外は足していません。

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **計画の行 2 の期待値が計画の規則と食い違っていたので、規則（今のコードの振る舞い）に合わせた**: 計画の行 2 は「A1 = D2 の予定で積替・KRPUS（6 時間遅れ）と区間 2 の出発・KRPUS（2 時間遅れ）を採用 → 同じなら発生時刻の遅い方（区間 2 の出発。当初 + 2 時間）」。同じ予定時刻のとき発生時刻の遅い方は積替（+6 時間）なので、39g3 の規則のとおりなら見込みは当初 + 6 時間になる。リファクタリングは振る舞いを変えないので、テストは当初 + 6 時間で書き、元のコードと出した後のコードの両方で緑を確かめた。この組で、比べ方を時点の列の位置に変える変異（M15。結果は当初 + 2 時間）が落ちる | この形で承認する（計画の文の誤り） | `LatestEstimateRuleTest.Bolt39g4_行2_…` |
| 2 | **S-12 の判定の出どころを `detailWithFreshness` に分けた**: 計画の決定 (9) は「S-12 は詳細と判定の組（新しい型）で返す」。新しい型は `TrackingRecordDetail`（追跡記録・鮮度超過か・許容範囲）で、照会サービスに `detailWithFreshness` を足した。S-13・採用の POST が使う `detail` はそのまま残した（S-13 には判定が要らない）。S-12 の GET だけが新しいメソッドを呼ぶ | この形で承認する | `TrackingRecordQueryService`、`TrackingRecordController.detail` |
| 3 | **S-11 の判定を `RecentTrackingRecords` の鮮度超過の追跡番号の集合で持たせた**: 計画の「`RecentTrackingRecords`（判定を持つ）」の形。C-10 の照会は判定しない形（`of(found, limit)`）のままなので、荷主の一覧には何も足さない（行 19 と M13 で確かめた） | この形で承認する | `RecentTrackingRecords` |
| 4 | **変異の見込みと実際の違い**（下の「変異」）: すべての変異（16 個。M12 を S-11 と S-12 の 2 つに分けた）が落ちた。計画の見込みと違ったのは、M10（UPDATE が書かない）が行 14・15 の統合テストで落ち受入シナリオでは落ちない（計画の見込みどおり）、M11（INSERT が書かない）が既存の要約の並びのテストでも落ちた（追加の検出）、M8 が行 12 に加えて行 10・11 でも落ちた（保持のみの経路も同じ導出を通るため）こと | 記録として承認する | 下の「変異」 |
| 5 | **規律の違反が 1 回あった**（禁止の形 `cat > f`）: 設計の文書を直している途中で、意味のないコマンド `cat > /dev/null < /dev/null; true` を 1 回打った（標準入力を /dev/null につなぐので待ちにはならず、作業ツリーへの影響もない）。ほかに、捨てるファイルの一覧を 1 回 `/tmp` に書いた（スクラッチパッドに書き直して使った） | T-247（拒むフック）の議題に `cat >` を足し、W7 の締めで諮る | 下の「ふりかえり」 |
| 6 | **既存のデータ**（決定 (6)）: 39g4 の前に開始した追跡記録は、次に採用の実績が変わるまで `next_expected_at` が NULL で、鮮度超過と判定されない（S-12 は「なし」）。配備の前にステージング・本番の件数を確かめる | 計画のとおり | 下の「既知の課題」 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39g4 回（W7 の 32 本目） |
| 期間 | 2026-11-12（計画の承認 `b9a54c4d8` から終了報告まで） |
| 対象 | 追跡: `SchedulePoint`・`NextExpectedMilestoneRule`・`FreshnessPolicy`・`TrackingRecordDetail`（新規）、`Schedule`（予定の時点の列と結び方）、`LatestEstimateRule`（予定の時点を使う）、`TrackingRecord`、`TrackingRecordSummary`、`RecentTrackingRecords`、`TrackingRecordQueryService`、`TrackingConfiguration`、`TrackingRecordRepository` の Javadoc、`MyBatisTrackingRecordRepository`・`TrackingRecordRow`・`TrackingRecordSummaryRow`・`TrackingRecordMapper`（Java と XML）、`TrackingRecordViews`・`TrackingRecordController`、テンプレート（S-11・S-12）、`UnderReviewReason`・`TrackingStatus` の Javadoc、application.properties、マイグレーション `V20261113100000__add_tracking_record_next_expected_at.sql`。マニュアル 9 章・用語集 |
| GitHub | なし（US を数えない技術の Bolt） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 39g4 計画](bolt_39g4_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model（用語の 4 行を直し、5 行（鮮度超過・予定の時点・次の予定の実績・次の予定の実績の規則・鮮度の判定）を足した。集約の図に次の予定の実績の予定時刻を足し、現在状態の「鮮度」を消して注に、規則の表、リポジトリ、:1370 の注、「Bolt 39g4 で決めたこと」、39g3 の節の 39g4b の注と `derivedWith` の直し）、data_model（論理モデル・注・Bolt 39g4 で足す列の節・索引の表）、ui_design（メタファー・S-11・S-12・画面イメージ・:1689）、non_functional（FRS-02 を書き換え元の文を注に、:128 の注、FRS-03、OBS-07）、requirements_definition（状態モデルの注）、operation（:52）、test_strategy（:332・:418）、release_plan（39g4b の行・チェックの行・D-130 の注の承認）、development_strategy（W7 の行）。変えた図は `plantuml -charset UTF-8 -tsvg` で描画を確かめた | `14ea27705` |
| 2. 守る既存のテストとリファクタリング | Red を書く前に守る既存のテスト（計画の一覧の Java のテスト・業務ルール層の受入シナリオ・横断の検査と `documentationTest`）を流し、724 件・失敗 0 を確かめた（T-217）。同じ予定時刻の組（行 2）を足して元のコードで緑を確かめ、予定の時点（`SchedulePoint`、`Schedule.points`・`pointOf`）を出して `LatestEstimateRule` が使うようにした。`LatestEstimateRuleTest` の既存は直さずに通った。行 1 のテストを書いた | `78640d32b` |
| 3. Red | 受入シナリオ（行 20・21）と状態の組み合わせの単体・統合（行 3〜19）を同じ段で書き、テストのコンパイルが新しい型とメソッドがない理由で落ちる（javac のエラー 150 件。シンボルがない 128、要約・照会の結果・組み立ての引数が合わない 20 ほか）ことを確かめた | `8c9937059`（Red と Green を同じコミット。T-254） |
| 4〜7. 規則・集約・保存と照会・画面 | `NextExpectedMilestoneRule`・`FreshnessPolicy` と設定、集約の導出（開始と `derivedWith`。完了後は空）、マイグレーションと INSERT・UPDATE・組み立て・2 つの要約、照会サービスの時計と判定、S-11 の印、S-12 の行と警告の文。テストの誤り 1 件（コントローラーの単体の既定の応答を `given(…)` で差し替えると、既定の応答が `detail` を呼んでスタブの先がずれた。`willReturn(…).given(…)` の形に直した）を直した | `8c9937059` |
| 8. 画面の層のシナリオと動画 | 行 21（`freshness_overdue_ui.feature`、`@demo-bolt-39g4/freshness-overdue`）。1 回目で通った | `ca419b401`、`995f7c6c9` |
| 9. マニュアル | 9 章（S-11 の ② に鮮度超過の印、S-12 の ② に次の予定の実績の予定時刻、「鮮度超過の追跡を確かめる」の節）、用語集（次の予定の実績・鮮度超過）。S-11・S-12 のキャプチャ 6 件を撮り直した | `9c5f632d0`、`995f7c6c9` |
| 10. 品質ゲート | 下の「品質ゲート」。SonarQube の直し 1 件 | `961878b11` |
| 11. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | 1 回目の `check`（`--continue`）: `test` 5,573 件、失敗 0（Bolt 39g3 の 5,503 件から +70）。`documentationTest` 5 件、失敗 0。`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck`・`spotbugsMain` を含めて成功。最後の直し（`961878b11`）の後の 2 回目の `check` も成功した（test 5,573 件・documentationTest 5 件、失敗 0。`spotbugsMain`・`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck` も流し直した） |
| `uiTest` | 1 回目: 223 シナリオ、失敗 0（JUnit の XML では 446 件、うち 223 件はシナリオの外枠で skipped と数えられる。39g3 の 222 から +1）。最後の直し（`961878b11`）の後の 2 回目も 223 シナリオ、失敗 0 |
| `manualScreenshots` | 成功。S-11・S-12 の 6 件（`09-tracking-list`・`09-tracking-detail`・`-registered`・`-draft`・`-candidate`・`-under-review`）を残し、日付だけ変わったほかのキャプチャと撮り直した動画（計 140 件）は HEAD に戻した |
| `demoVideo` | 成功。`bolt-39g4/freshness-overdue.webm` だけを残した |
| 変異 | 計画の 15 個（M12 を S-11 と S-12 に分けて 16 個）をスクラッチパッドの使い捨てのスクリプトで 1 つずつ当て、決めたテストを流した。落ちたかどうかは JUnit の XML の落ちたテストの名前で決め、名前のない exit 1 は「起動の失敗」と分けた（39g2 の Try）。16 個すべて落ち、起動の失敗はなかった |
| SonarQube | 1 回目は Quality Gate が FAIL（新しいコードの違反 1 件。`detailWithFreshness` が同じクラスの `@Transactional` のメソッド `detail` を `this` で呼ぶ）。直した後の 2 回目は PASS（新しいコードのカバレッジ 96.1%、重複 0.01787%、違反 0） |
| ArchUnit | `LayerArchitectureTest`・`DddAnnotationArchitectureTest`・`MapperSchemaArchitectureTest`・`PublicApiArchitectureTest` を含む `test` が通った。規則の例外は足していない |
| マイグレーション | H2（dev のプロファイルの `TrackingFreshnessSettingIntegrationTest` と画面の層）と PostgreSQL（Testcontainers の `MyBatisTrackingRecordRepositoryIntegrationTest`・`SchemaCommentIntegrationTest`）で適用された |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| CI | **流していない**。指示により push していないので、CI は走っていない |

### 変異

| # | 変異 | 落ちたテスト（行） |
| :--- | :--- | :--- |
| M1 | ちょうど 36 時間を超えないにする | `FreshnessPolicyTest` 行 7 (c) |
| M2 | 許容範囲を足さずに予定時刻を過ぎたら鮮度超過 | `FreshnessPolicyTest` 行 7 (b)、`TrackingRecordQueryServiceTest` 行 17 (b)・行 18 の 2 本 |
| M3 | 次の予定の実績に確認中・下書き・保持のみも使う | `NextExpectedMilestoneRuleTest` 行 4 の 3 本、受入シナリオ（行 20 の確認中） |
| M4 | 最も後でなく最初に結べた時点の次 | `NextExpectedMilestoneRuleTest` 行 3 (c)・(d)・(e) |
| M5 | 発生時刻の最も新しい実績の時点の次 | `NextExpectedMilestoneRuleTest` 行 3 (h) |
| M6 | 最後の区間の到着の後も次を返す | `NextExpectedMilestoneRuleTest` 行 3 (e)、`TrackingRecordQueryServiceTest` 行 17 (c) |
| M7 | 完了後も次の予定の実績を返す | `TrackingRecordTest` 行 6 (a)・(b)、`TrackingRecordMilestoneTest` 行 10（保持のみ） |
| M8 | DE-13 の下書きの受け取りでも導出し直す（保存した値のまま持つ経路で導く） | `TrackingRecordExternalMilestoneTest` 行 12 の 2 本、`TrackingRecordMilestoneTest` 行 10（保持のみ）、`TrackingRecordCandidateAdoptionTest` 行 11 |
| M9 | 開始で次の予定の実績を空にする | `TrackingRecordTest` 行 9、`TrackingRecordMilestoneTest` 行 10（拒否）、受入シナリオ（行 20 の 2 本） |
| M10 | UPDATE が `next_expected_at` を書かない | `MyBatisTrackingRecordRepositoryIntegrationTest` 行 14 の 2 本・行 15 の 2 本 |
| M11 | INSERT が `next_expected_at` を書かない | `MyBatisTrackingRecordRepositoryIntegrationTest` 行 14、既存の要約の並びのテスト |
| M12a | S-11 の判定に判定の時刻でなく開始時刻を使う | `TrackingRecordQueryServiceTest` 行 17 (a)・(d)、受入シナリオ（行 20 の 3 本） |
| M12b | S-12 の判定に判定の時刻でなく開始時刻を使う | `TrackingRecordQueryServiceTest` 行 18 の 2 本 |
| M13 | C-10 にも鮮度超過の印を出す | `CustomerTrackingControllerTest` 行 19 |
| M14 | S-12 の文の時間を直書きにする | `TrackingRecordControllerTest` 行 18（許容範囲 48 時間） |
| M15 | 見込みの比べ方を時点の列の位置に変える | `LatestEstimateRuleTest` 行 2（同じ予定時刻の組） |

### 文書の突き合わせ（T-197・T-251）

マニュアルの「鮮度超過」「次の予定の実績の予定時刻」の語と、なしのときの「なし」、警告の文「次の予定の実績が予定時刻から 36 時間を過ぎても届いていません。」が、テンプレートの `dt`・印と `TrackingRecordViews` の文と一字ずつ一致することを `grep` で確かめた。9 章の鮮度超過の決まりの文は、計画の決定 (2)〜(4) の文から写した。

## 計画から変えたこと

- 行 2 の期待値を規則に合わせた（議題 1）
- S-12 は `detail` を残して `detailWithFreshness` を足した（議題 2）。コントローラーの単体テストは、既定で `detail` の追跡記録と鮮度超過でない組を返す応答を `@BeforeEach` で置き、既存のスタブを直さずに済ませた
- `Schedule.pointOf` は実績（エンティティ）でなく種類と場所を受ける（計画の図は `pointOf(Milestone)`）。値オブジェクトがエンティティに依存しないため
- 組み立ての `reconstitute` に次の予定の実績の予定時刻を受ける形を足し、受けない形（Bolt 39g4 の前の形。空にする）を残した（前例 39gc・39g3 の形）。`TrackingRecordSummary` には受けない形を作らず、組み立てる箇所をすべて直した
- 変異の M12 を S-11 と S-12 に分けた
- 守る既存のテストのうち画面の層のシナリオは Red の前に個別には流していない（`uiTest` はシナリオを選んで流せない）。Green の後の `uiTest` で全件流した（39g3 と同じ）
- 動画・行 17 (d) のコントローラーの単体は送らずに済んだ（見積りの打ち切りは使わなかった）

## デモ項目

- 動画: [`@demo-bolt-39g4/freshness-overdue`](../../assets/demo/bolt-39g4/freshness-overdue.webm){:target="_blank"}（S-11 の鮮度超過の印から S-12 を開いて警告の文と次の予定の実績の予定時刻を見て、S-13 で遅れていた出発を登録すると S-12 の警告と S-11 の印が消える）
- マニュアル: 9 章・用語集を直し、S-11・S-12 のキャプチャを撮り直した
- デモ環境の画面は開いて確かめていない（push していないので配備もしていない）

## 判断と学び

- 結び方を予定（値オブジェクト）に出してから次の予定の実績の規則を足したので、見込みと鮮度で対応づけが分かれず、新しい規則は「位置の最大の次」だけの数行になった
- 計画の状態の組み合わせの表の期待値も、規則の文と食い違うことがある。Red の前に今のコードで緑を確かめる手順（行 2）が、計画の文の誤りを拾った
- Mockito の `given(mock.m(...))` は、既定の応答が別のスタブのメソッドを呼ぶと、スタブの先がずれる。既定の応答を置いたメソッドは `willReturn(...).given(mock).m(...)` で差し替える
- SonarQube は `@Transactional` のメソッドから同じクラスの `@Transactional` のメソッドを `this` で呼ぶ形を違反にする。照会の部品（担当範囲の判定）を直接呼ぶ

### ふりかえり（KPT）

- Keep: 守る既存のテストを Red の前に流した。リファクタリングを Red の前に分け、元のコードと出した後のコードの両方で同じテストを流した。変異の表の「効く層」を計画で 1 行ずつ確かめていたので、16 個すべてが見込みのテストで落ちた
- Problem: 意味のないコマンドで禁止の形（`cat >`）を 1 回打った（議題 5）。計画の表の期待値の誤りを開始準備の検証で拾えなかった（議題 1）
- Try: コマンドを打つ前に、標準入力・`cat >`・`/tmp` を使っていないかを見る（T-247 のフックで拒む）。状態の組み合わせの表で、期待値が規則の文から導けるかを開始準備の検証の観点に足す

### 既知の課題（後に持ち込む）

- **配備の前**: ステージング・本番で、完了後でなく `next_expected_at` が NULL の追跡記録の数を確かめる（決定 (6)。補正のマイグレーションは作っていない）。39g3 の `last_acquired_at` の件数と一緒に行う。照会は運用手順書のタスクで行う（独自のスクリプトを作らない）
- 39g4b: 航海の採用値で動く見込み（【業務の判断】(at)）と見込みの根拠の表示
- W9: FRS-03 の「最新性を保証しない旨」・次回更新日時・担当窓口（鮮度超過の判定は `FreshnessPolicy` を使う）
- W10: 鮮度超過だけの一覧・絞り込み（`next_expected_at` の索引）、OBS-07、業務ホームの未処理
- W11: 取消しの写し・予約状態の完了の写しを書くときに `next_expected_at` を空にする
- W7 の締め: T-247 に `cat >` を足す（議題 5）
- Release 1.0 のパイロットの前: 業務の判断 (aq)〜(as) を追跡責任者に、(at) を業務責任者に確かめる（台帳 D-133〜D-136）

## 次の Bolt

39g4b（航海の採用値で動く見込みと見込みの根拠の表示）。release_plan の順序のとおり 39h の前。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-12 | 初版作成 | anthropic/claude-sonnet-5-5 |
| 2026-11-12 | 推奨のとおり承認として記録した（議題 1〜6。以降は推奨で進める指示による。配備の前の件数の確認と T-247（`cat >` を拒否対象に足す）は W7 の締めへ。human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |

## 関連ドキュメント

- [Bolt 39g4 計画](bolt_39g4_plan.md)、[Bolt 39g3 終了報告](bolt_39g3_report.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[データモデル](../../design/cargo-tracker/data_model.md)、[ユーザーマニュアル 9 章](../../manual/09-追跡管理.md)
