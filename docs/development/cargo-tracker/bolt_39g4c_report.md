---
type: Plan
title: "Bolt 39g4c 終了報告 - 最新の到着見込みの根拠を S-12 に 1 行で示す"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-12T08:20:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-12T09:47:19Z }
---

# Bolt 39g4c 終了報告 - 最新の到着見込みの根拠を S-12 に 1 行で示す

## 承認の議題（T-36。先に確かめてほしいこと）

最新の到着見込みの規則が見込みと見込みの根拠を 1 回の評価で返し（`LatestEstimateRule.estimateWithBasis`、値 `LatestEstimate`・`LatestEstimateBasis`）、追跡記録が見込みと根拠を 1 つの項目 `latestEstimate` で持って保存するようにしました。見込みと根拠が合うこと（当初・航海の到着予定・到着の実績は見込みと等しく、出発・積替の実績は当初以上で予定の時点に結ぶ採用の実績）は private のコンストラクターで確かめます。S-12 の定義リストの最新の到着見込みの次に「見込みの根拠」の 1 行を示し、S-11 と C-10 には出しません。最初に Bolt 39g4b の終了報告の議題 3 を返済しました（届かない 2 つの分岐を消し、残りをテストで縛った）。計画の確認ポイント CP-1〜CP-8 は承認済み（2026-11-12）のとおりに実装しました。スキーマの変更は承認済みの 2 つのマイグレーション（共通の `V20261115100000`: 2 列・CHECK `ck_tracking_record_latest_eta_basis`・列のコメント、PostgreSQL だけの `V20261115100100`: 外部キー `fk_tracking_record_latest_eta_basis`）だけです。認可・外部連携・ArchUnit の例外・新しいコンテキスト間の依存は足していません。

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **CHECK の書き方を計画の文から 1 か所変えた**（データベース）: 計画の決定 (3) の条件をそのまま OR でつなぐと、種類が NULL で実績番号がある行で比べの結果が NULL になり CHECK を通った（行 10 の組で落ちた）。2 つ目以降の枝を「種類が NOT NULL」で囲んだ。拒む行と通す行は計画の決定 (3) のとおりで、制約の名前・列・意味は変えていない（マイグレーションはコミット前に直したので、適用済みの版の書き換えではない） | この形で承認する | `V20261115100000__add_tracking_record_latest_eta_basis.sql`、下の「判断と学び」 |
| 2 | **根拠の実績番号の先の確かめ（M21）は表の上では起きない**: 集約の不変条件（根拠は採用の実績）と、UPDATE の後の実績の追加があるので、保存の流れでは根拠の実績番号の実績が表にない状態を作れない。計画どおり実装し、マッパーを差し替える既存の `MyBatisTrackingRecordRepositoryUniqueKeyTest` で確かめた（統合テストからは外した）。議題 3（39g4b）と同じ「届かない分岐」に近いが、PostgreSQL の遅延の外部キーの違反を 500 にしない守りとして P-5 と同じ形に保った | 残す（P-5 と同じ守り。消すと現在状態の根拠と見込みの根拠で扱いが分かれる） | `MyBatisTrackingRecordRepository.update`、`MyBatisTrackingRecordRepositoryUniqueKeyTest` |
| 3 | **S-12 のワイヤーフレームの例を計画から変えた**: 計画は「見込みの根拠 \| 区間 1 の出発の実績（実績 2、JPTYO、当初より 6 時間遅れ）」を足すとしたが、ワイヤーフレームの場面は「実績 1（集荷）を登録しました」で、見込みは当初と同じ。場面に合う「当初の到着予定」にした（キャプチャ `09-tracking-detail-registered` と同じ） | この形で承認する | ui_design の S-12 のワイヤーフレーム |
| 4 | **計画の変異の表の M16 の見込みが外れた**: 画面の文は 0 分以下を「当初の到着予定どおり」にするので、行 6 (e) では M16 が落ちない。集約の行 4 に早い出発で見込みの遅れが 0 の組を足して落とした（`fd5cdeb2f`） | 記録として承認する。Try に入れた | 下の「計画から変えたこと」 |
| 5 | **ステップ 1 のコミットがステップ 2 の後になった**: 設計の文書の直しをサブエージェントに並行して任せたので、コミットの順が `a278e58f6`（ステップ 2）→ `c11dedae8`（ステップ 1）になった（39g4b の議題 4 と同じ）。中身は計画のとおり | 記録として承認する | 下の「成果」 |
| 6 | **既存のデータ**（決定 (3)・CP-6）: 39g4c の前に開始した追跡記録は、次に見込みを導き直すまで 2 列が NULL で S-12 は「記録がありません」。補正のマイグレーションは作っていない | 計画のとおり | 下の「既知の課題」 |
| 7 | **規律**: 標準入力につなぐコマンド・ヒアドキュメント・リポジトリの外（スクラッチパッドを除く）への書き込みは使わなかった。一時ファイル（JUnit の集計・変異・行の削除の `.py` とログ）はスクラッチパッドに置いた | — | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39g4c 回（W7 の 34 本目） |
| 期間 | 2026-11-12（計画の承認 `c7ea6ee1e` から終了報告まで） |
| 対象 | 追跡: `LatestEstimate`・`LatestEstimateBasis`（新規）、`LatestEstimateRule`（`estimateWithBasis`）、`TrackingRecord`（項目 `latestEstimate`、`latestEstimateBasis`・`latestEstimateBasisPoint`・`latestEstimateDelay`、不変条件、`reconstitute` の根拠を受ける形）、`VoyageArrival`（届かない分岐を消した）、`MyBatisTrackingRecordRepository`・`TrackingRecordRow`・`TrackingRecordMapper`（Java と XML）、`TrackingRecordViews`・S-12 のテンプレート。マイグレーション 2 つ。マニュアル 9 章・用語集・S-12 のキャプチャ |
| GitHub | なし（US を数えない技術の Bolt） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 39g4c 計画](bolt_39g4c_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model（「見込みの根拠 \| LatestEstimateBasis」の行を足し、最新の到着見込み・最新の到着見込みの規則・集約の図・T-INV-10・規則の表・リポジトリ・「Bolt 39g4c で決めたこと」・39g4b の節の注）、data_model（論理モデル・注・Bolt 39g4c で足す列と制約の節）、ui_design（S-12・ワイヤーフレーム・C-10 と C-11 の表）、test_strategy（T-INV-08〜10）、release_plan（39g4c の行・D-139 の注・更新履歴）。サブエージェントに並行して任せ、名前を先に渡した。変えた 3 つの図は `plantuml -charset UTF-8 -tsvg` で描画を確かめた | `c11dedae8` |
| 2. 守る既存のテストと議題 3 の返済 | Red を書く前に守る既存のテスト（計画の一覧の Java のテスト・業務ルール層の受入シナリオ・横断の検査）を流し、1,007 件・失敗 0 を確かめた（T-217）。行 1 (a)(b)(d)(e) のテストを書いて元のコードで通し、`VoyageArrival.orderOf` の届かない 2 つの分岐を消した後も同じテストを流した（追跡・受入シナリオ 978 件、失敗 0） | `a278e58f6` |
| 3. Red | 受入シナリオ（行 11・12）と状態の組み合わせの単体・統合（行 2〜10）を同じ段で書き、テストのコンパイルが新しい型とメソッドがない理由で落ちることを確かめた（javac の表示で 74 件。新しい型とメソッドのシンボルがない、パッケージがない、マッパーの UPDATE の引数の数が合わない） | `819aec42f`（Red と Green を同じコミット。T-254） |
| 4〜7. 値と規則・集約・保存・S-12 | 値 `LatestEstimate`・`LatestEstimateBasis`、`estimateWithBasis`、集約の項目と不変条件と取り出し口、2 つのマイグレーションと INSERT・UPDATE・組み立て・根拠の実績番号の先の確かめ・メモリの実装、S-12 の文。テストの誤りではなく実装の誤り 1 件を直した（CHECK が種類 NULL の行で比べの結果が NULL になり通っていた。行 10 の組で落ちた）。画面の層のステップの `Pattern.quote` を `PlaywrightText.literal` に直した（`AcceptanceStepArchitectureTest` で落ちた） | `819aec42f` |
| 8. 画面の層のシナリオと動画 | 行 12（`latest_estimate_basis_ui.feature`、`@demo-bolt-39g4c/latest-estimate-basis`）。1 回目で通った | `c47580bd4`、`528bc8c98` |
| 9. マニュアル | 9 章（S-12 の ② に見込みの根拠、「最新の到着見込みと最終取得時刻」の節に 7 つの文の意味の表と S-11・C-10 に出ないこと）、用語集（見込みの根拠を足し、最新の到着見込みの項に 1 行）。S-12 のキャプチャ 5 枚を撮り直した | `7460bff37`、`528bc8c98` |
| 10. 品質ゲート | 下の「品質ゲート」。変異 M16 を落とすテストを 1 本足した | `fd5cdeb2f`、`c60360a1a` |
| 11. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check`（`--continue`）: `test` 5,734 件、失敗 0（Bolt 39g4b の 5,653 件から +81）。`documentationTest` 5 件、失敗 0（`LivingGlossaryConsistencyTest` は新しい値 2 つの用語の行と Javadoc の最初の語を確かめた）。`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck`・`spotbugsMain` を含めて成功。最後の直し（SonarQube の 1 件。`TrackingRecordViews` の書式）の後に SonarQube の `test`（全件 5,734 件・失敗 0）と `spotlessJavaCheck`・`checkstyleMain`・`checkstyleTest`・`spotbugsMain` を流し直した（すべて成功）。直した書式は変異 M14・M15 を当て直して落ちることも確かめた |
| `uiTest` | 225 シナリオ、失敗 0（JUnit の XML では 450 件、うち 225 件はシナリオの外枠で skipped と数えられる。39g4b の 224 から +1）。最後の直しの後の 2 回目も 225 シナリオ、失敗 0 |
| `manualScreenshots` | 成功。S-12 の 5 枚（`09-tracking-detail`・`-registered`・`-draft`・`-candidate`・`-under-review`）だけを残し、日付だけ変わったキャプチャ 51 件は HEAD に戻した |
| `demoVideo` | 成功。`bolt-39g4c/latest-estimate-basis.webm` だけを残し、撮り直された既存の動画は HEAD に戻した |
| 変異 | 計画の 21 個をスクラッチパッドの使い捨てのスクリプトで 1 つずつ当て、決めたテストを流した。落ちたかどうかは JUnit の XML の落ちたテストの名前で決め、名前のない exit 1 は「起動の失敗」と分けた（39g2 の Try）。改行は対象のファイルの CRLF に合わせた（39g4b の学び）。21 個すべて落ち、起動の失敗はなかった。M11 は spotless が行を折り返していたので 1 回目は当てられず、型を合わせて流し直した。M16 は計画の見込み（行 6 (e)）では落ちず、行 4 に早い出発の組を足して落とした（下の「計画から変えたこと」） |
| SonarQube | 1 回目は Quality Gate が FAIL（新しいコードの違反 1 件。`TrackingRecordViews` の入れ子の三項演算子）。直した後の 2 回目は PASS（新しいコードのカバレッジ 96.2%、重複 0.01743%、違反 0。全体のカバレッジ 95.9%、未解決のイシュー 0） |
| ArchUnit | `LayerArchitectureTest`・`DddAnnotationArchitectureTest`・`MapperSchemaArchitectureTest`・`PublicApiArchitectureTest`・`AcceptanceStepArchitectureTest` を含む `test` が通った。規則の例外は足していない。Modulith の検証も通った（追跡の `allowedDependencies` は変えていない） |
| マイグレーション | H2（受入テストの組み立てと画面の層・`manualScreenshots`・`demoVideo`）と PostgreSQL（Testcontainers の `MyBatisTrackingRecordRepositoryIntegrationTest`・`SchemaCommentIntegrationTest`）で適用された。CHECK・外部キー・列の型とコメントは PostgreSQL の統合テスト（行 10）で確かめた。H2 の CHECK は統合テストで直接は確かめていない（集約の不変条件で守る。前例のとおり） |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| CI | **流していない**。指示により push していないので、CI は走っていない |

### 変異

| # | 変異 | 落ちたテスト（行） |
| :--- | :--- | :--- |
| M1 | 遅れのない出発の実績で根拠を当初の到着予定にする | `LatestEstimateRuleTest` 行 3 (c)・(l) |
| M2 | 同じ時刻の航海の到着予定を根拠にする | `LatestEstimateRuleTest` 行 3 (i)・(o) |
| M3 | 見込みに効かない早まった航海の到着予定を根拠にする | `LatestEstimateRuleTest` 行 3 (f)・(g)・(i)・(o)、受入シナリオ 2 本 |
| M4 | 予定の順で最も前の実績を根拠にする | `LatestEstimateRuleTest` 行 3 (l) ほか 4 本、受入シナリオ |
| M5 | 確認中の実績も根拠にする | `LatestEstimateRuleTest` 行 3 (k) ほか 2 本、受入シナリオ 2 本 |
| M6 | 到着の実績より航海の到着予定を根拠にする | `LatestEstimateRuleTest` 行 3 (j)、39g4b の (f)、受入シナリオ |
| M7 | 航海の到着予定を受けたとき根拠を書き直さない | `TrackingRecordLatestEstimateBasisTest` 行 4 (d)、受入シナリオ |
| M8 | 結べない新しい知らせの後も根拠を航海の到着予定のままにする | `TrackingRecordLatestEstimateBasisTest` 行 4 (e) |
| M9 | DE-13 の下書きの受け取り・保持のみで見込みと根拠を導き直す | `TrackingRecordLatestEstimateBasisTest` 行 4 (f)・(g) |
| M20 | 根拠が航海の到着予定でも見込みを実績から書く | `TrackingRecordLatestEstimateBasisTest` 行 4 (d)・(e)（不変条件）、受入シナリオ 3 本 |
| M10 | 追跡の開始で根拠を空にする | `TrackingRecordLatestEstimateBasisTest` 行 4 (a)・(c)、`MyBatisTrackingRecordRepositoryIntegrationTest` 行 8・9・10、受入シナリオ |
| M11 | 組み立てで航海の到着予定のない航海の根拠を通す | `TrackingRecordLatestEstimateBasisTest` 行 5 (d) の 2 本 |
| M12 | UPDATE が 2 列を書かない | `MyBatisTrackingRecordRepositoryIntegrationTest` 23 本（PostgreSQL の CHECK が見込みの変わった行を拒む） |
| M13 | CHECK が実績のときの実績番号の NOT NULL を確かめない | `MyBatisTrackingRecordRepositoryIntegrationTest` 行 10「実績で実績番号が NULL」 |
| M14 | 遅れの文で分を落とす | `TrackingRecordControllerTest` 行 6 (c) |
| M15 | 0 分の見込みの遅れを「当初より 0 分遅れ」にする | `TrackingRecordControllerTest` 行 6 (e)・(f) |
| M16 | `latestEstimateDelay()` を実績の発生時刻 − 予定時刻にする | `TrackingRecordLatestEstimateBasisTest` 行 4 (b) の対（早い出発。ステップ 10 で足した） |
| M17 | C-10 にも根拠の行を出す | `CustomerTrackingControllerTest` 行 7 |
| M18 | メモリの実装の照会がすべての区間の航海で引く | `VoyageArrivalEventHandlerTest` 行 1 (d) の 2 本 |
| M19 | 最終取得時刻に下書きの取得時刻を含める | `TrackingRecordVoyageArrivalTest` 行 1 (e) の 2 本 |
| M21 | UPDATE で根拠の実績番号の先の確かめをしない | `MyBatisTrackingRecordRepositoryUniqueKeyTest` 行 8 |

### 文書の突き合わせ（T-197・T-251）

S-12 の `dt`「見込みの根拠」と 7 つの文（当初の到着予定・区間 n の出発／積替の実績と当初からの見込みの遅れ・当初の到着予定どおり・到着の実績・航海 V の到着予定（取得 日時）・記録がありません）は、計画の決定 (5) と【業務の判断】(ax) の文から写し、`TrackingRecordControllerTest` の行 6 と ui_design の S-12・マニュアル 9 章の表で同じ文にした。キャプチャ 5 枚で 1 行が最新の到着見込みの次に出ることを目で確かめた（どれも根拠は「当初の到着予定」）。

## 計画から変えたこと

- CHECK `ck_tracking_record_latest_eta_basis` の 2 つ目以降の枝を「種類が NOT NULL」で囲んだ（議題 1。拒む行と通す行は計画のとおり）
- 行 8 の根拠の実績番号の先の確かめ（M21）は、集約の不変条件と実績の追加があるので表の上では起こせず、統合テストではなくマッパーを差し替える既存の `MyBatisTrackingRecordRepositoryUniqueKeyTest` に置いた（前例 Bolt 39gb の行 61。議題 2）
- 行 12 の取り込む航海の到着予定は、計画の A2 + 8 時間でなく Bolt 39g4b の既存のステップ（直行の航海の到着の 1 日遅れ）を使った。実績の遅れの見込み（6 時間）より厳密に遅いという計画の条件は同じで、新しいステップを作らずに済んだ
- 変異 M16 は計画の見込み（行 6 (e)）で落ちなかった。画面の文は 0 分以下を「当初の到着予定どおり」にするので、早い出発で負になっても文が変わらない。集約の行 4 に早い出発で `latestEstimateDelay()` が 0 になる組を足した（`fd5cdeb2f`）
- 既存のテスト `TrackingRecordControllerTest` の Bolt 39g3 の行 18（最新の到着見込みの次に最終取得時刻）は、間に見込みの根拠の 1 行が入るので、並びの正規表現に 1 行を足した（振る舞いの変更による直し）
- S-12 のワイヤーフレームの見込みの根拠の例は、場面（実績 1 の集荷だけ）に合わせて「当初の到着予定」にした（議題 3）
- 行 7 の C-10 は、計画の値の組（`CustomerTrackingView`）ではなく、航海の到着予定が根拠の追跡記録から `customerView()` で作った値で確かめた（根拠を持たない値なので出ないことを経路ごと確かめる）
- 守る既存のテストのうち画面の層のシナリオは Red の前に個別には流していない（`uiTest` はシナリオを選んで流せない）。Green の後の `uiTest` で全件流した（39g3〜39g4b と同じ）
- 動画は送らずに済んだ

## デモ項目

- 動画: [`@demo-bolt-39g4c/latest-estimate-basis`](../../assets/demo/bolt-39g4c/latest-estimate-basis.webm){:target="_blank"}（追跡管理者が S-12 で根拠「当初の到着予定」を見て、区間 1 の出発を 6 時間遅れで登録すると「区間 1 の出発の実績（実績 1、JPTYO、当初より 6 時間遅れ）」になり、データ責任者が直行の航海を到着の 1 日遅れで取り込むと「航海 … の到着予定（取得 …）」になる。荷主の照会の結果には出ない）
- マニュアル: 9 章・用語集を直し、S-12 のキャプチャ 5 枚を撮り直した
- デモ環境の画面は開いて確かめていない（push していないので配備もしていない）

## 判断と学び

- 規則が見込みと根拠を 1 つの値で返し、集約も 1 つの項目で持ったので、書き手 3 つ（開始・導出・航海の受け取り）は値を渡すだけになり、片方だけを書く変異（M7・M20）は不変条件で必ず落ちた
- SQL の CHECK を OR でつなぐときは、NULL の列の比べが NULL（通る）になる枝を作らない。種類の列が NULL の行は最初の枝だけで扱い、残りの枝を NOT NULL で囲む。統合テストの「種類が NULL で実績番号がある」の組で見つかった
- 画面の書式が値を丸める（0 以下を同じ文にする）と、その手前の値の変異は画面のテストでは見えない。変異が効く層の値をその層のテストで縛る（39g3 の Try の続き）

### ふりかえり（KPT）

- Keep: 守る既存のテストを Red の前に流し、議題 3 のリファクタリングを Red の前に分けた。設計の文書の直しをサブエージェントに並行して任せ、名前を先に渡した（用語の行と Javadoc の最初の語が 1 回で合った）。変異のスクリプトを CRLF に合わせ、落ちたテストの名前で判定した
- Problem: 計画の変異の表の M16 が、画面の書式の丸めで見込みのテストでは落ちない組だった。計画の CHECK の文（決定 (3)）をそのまま SQL にすると NULL で通る枝ができた
- Try: 計画の変異の表で、落ちる見込みのテストが書式や丸めの後ろにあるときは、その手前の層の値も縛る組を入れる。CHECK を書くときは NULL の列ごとに真理値を確かめる組を状態の組み合わせに入れる

### 既知の課題（後に持ち込む）

- **配備の前**: 39g4c の前に開始した追跡記録の根拠は NULL（「記録がありません」。決定 (3)。補正しない。次に見込みを導き直すと埋まる）。39g3〜39g4b の件数の確かめと一緒に数える
- 技術的負債: `TrackingRecord.reconstitute` の形が 5 つ、テストだけが呼ぶ `LatestEstimateRule.estimate` の 2 つの形（計画の申し送りのとおり。W7 の締めで返済の候補として諮る）
- W9: C-10 の見込みの根拠と FRS-03 の「最新性を保証しない旨」
- W11: 予定を差し替える経路を作るときに、航海の到着予定と順の鍵を空にし、見込みと根拠を導き直す
- Release 1.0 のパイロットの前: 業務の判断 (aw)・(ax) を業務責任者と追跡責任者に確かめる（台帳 D-139・D-140）

## 次の Bolt

39h（US-14 AC5 の差異）。release_plan の順序のとおり。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-12 | 初版作成 | anthropic/claude-opus-5-5 |
| 2026-11-12 | 推奨のとおり承認として記録した（議題 1〜5。以降は推奨で進める指示による。T-247・配備前の件数の確認は W7 の締めへ。human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
