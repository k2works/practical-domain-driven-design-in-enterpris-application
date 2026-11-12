---
type: Plan
title: "Bolt 39h4 終了報告 - US-14 を締める（G-06 の欠落・差異、受入条件の照合の表、W7 のデモの動画）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T04:40:00Z }
---

# Bolt 39h4 終了報告 - US-14 を締める（G-06 の欠落・差異、受入条件の照合の表、W7 のデモの動画）

## 承認の議題（T-36。先に確かめてほしいこと）

本番のコード・スキーマ・認可・ArchUnit の例外は変えていない（テスト・テスト資源・設計文書だけ）。承認の後に #18 を閉じる（GitHub への反映。この Bolt では変えていない）。

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **規律の違反の可能性**: `demoVideo` を流すとき、出力をスクラッチパッドのログに書く形（`> demo.log 2>&1`）を 1 回使った。指示の「`> ファイル`」にあたるかもしれない（標準入力を読む形ではなく、リポジトリの外のスクラッチパッドへ出力を書いただけ）。`<<`・`<<<`・`-` で標準入力を読む形・`cat >`・`python -`・引数なしの `cmd` は使っていない。Python は `.py` のファイル（変異のスクリプト）と、引数つきの `python -c`（件数の集計。標準入力は読まない）だけ | 記録のみ | 議題の内容 |
| 2 | **G-06b の入力に航海を 1 本残した（計画は「航海が 1 本もない」）**: 計画の行 3 は「経路設計の航海に直行も積替えも 1 本もない」。入力を空にすると変異 M4・M6 が意味を持たないので、積替えの 2 本目（V-301）の外部 ID にだけ採用値がなく、1 本目 V-401（JPTYO → SGSIN。取得は判定時刻の 2 時間前）だけが入る形にした。候補は 0 件のまま、除外理由の「情報不足」も付かない。欠落の定義（採用値のない外部 ID の航海は経路設計にない）は変えていない | 承認 | `golden/v0/G-06b.json` |
| 3 | **変異 M4 の当て先を変えた**: 計画は `InfoFreshnessPolicy` に当てる。候補が 0 件では方針の評価が呼ばれず、G-06b を落とせない。探索（`RouteCandidateFinder.extend`）が目的地に着かない列も候補に加える変異に変えた。G-06b は落ちた（下の変異） | 承認 | 変異の表 |
| 4 | **ステップ 3 の Red は一部だけ**: 設計文書（ステップ 1）に Release 1.0 の照合の表を先に置いたので、「表がない状態で落とす」Red は作れなかった（本物の表のテストは書いた時点で緑）。代わりに、見出しを引数にした書き方で前方一致のまま流し、見出しの前方一致・行 0 件の 2 本が落ちることを確かめた（10 件中 2 件失敗）。その後に完全一致と空振りの失敗を実装した | 記録のみ | `AcceptanceCriteriaCoverageTest` |
| 5 | **G-06c は計画どおり最初から通る見張り**。ステップ 4 の Red は「ガードのテスト（案件の一覧とファイルの不一致）」の 1 件だけ（302 件中 1 件失敗）。G-06b の期待も最初から緑だった（案件のデータと例の表の行を先に書き、`V0_CASES` を足す前は集約のテストにそもそも入らないため） | 記録のみ | `RoutingCaseGoldenDatasetTest` |
| 6 | **動画は目で再生して確かめていない**: `bolt-39h4/confirm-rejected-same-info-version.webm`（1.48 MB）が撮れたことと、ほかの既存の動画を HEAD に戻したことだけ確かめた。内容の確認は人にお願いしたい | 人が再生して確認 | `docs/assets/demo/bolt-39h4/` |
| 7 | **行 7 の性能テストは Windows で 1 回流れた（判定ではない）**: 採用 8,000・差異 2,000、`ingest=73.6s`・`total=74.0s`（上限 300 s は PERF-06 の判定に使わない）、接続の最大 2、`commits=10002`（`ROWS + 2` のまま。外部版の数と差異の件数に依らない。`measure()` の式は変えていない）、監査の配信 18.3 s（参考）。新しいテストは書いた時点で緑で、Red は作れなかった（行 7 の確かめは「組み立てて流れること」）。**CI（PERF-06）は流していない**（push していない）。参考の値で 39h 以降の取込の変更の影響を測るのは W7 の締めの CI | 計画のとおり | `ExternalDataIngestionPerformanceTest` |
| 8 | **行 8 の警告の文の根拠**: 追加の 1 行は要らなかった。次の既存のテストが実在し、縛っている（名前を確かめた）: `TrackingRecordCandidateAdoptionTest` の `行10_V2の後にV1を公開すると公開した時刻の新しいV1が候補`・`行6_古いDE13が後から届いても候補は公開した時刻の新しい方で届いた順によらない`、`PublicationOrderTest` の `行1_公開の順は公開した時刻から取得時刻と受信記録IDの順に比べる`、`ReceivedRecordDecisionServiceTest` の `行1_主要実績の内容の違う原本を採用すると判断と採用値を追記しDE29とDE13をこの順に出して時刻はClockから1回取る`、`adopted_milestone_reflected_ui.feature`（「後から公開された原本に置き換えられました」）と `TrackingRecordControllerTest`。S-12 の実画面はパイロットの前の確認（計画のとおり） | 計画のとおり | 追跡の側のテスト |
| 9 | **#18 のクローズと SP 8**: 完了条件のうち CI（PERF-06）以外は満たした。クローズは承認の後（親が `gh issue comment 18`・`gh issue close 18`・Project の Status を Done に）。コメントに PERF-06 が 5 分を超えたら開き直すことを書く。release_plan の進捗表の 8 SP は承認の後に数える | 承認 | 下の「#18 のクローズの確認」 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39h4 回（W7 の 38 本目） |
| 期間 | 2026-11-13（計画の承認 `df1fd2cc1` から終了報告まで） |
| 対象 | テスト・テスト資源・設計文書だけ。`AcceptanceCriteriaCoverageTest`、`GoldenDatasetCase`・`RoutingCaseGoldenDatasetTest`・`golden/v0/G-06b.json`・`G-06c.json`・`route_candidates_golden_v0.feature`、`adopted_voyage_reflected_ui.feature`（タグだけ）、`ExternalDataIngestionPerformanceTest`、設計文書 |
| GitHub | [#18](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/18)（US-14。クローズは承認の後） |
| SP | 8（US-14。承認の後に数える） |
| ブランチ | develop |
| 計画 | [Bolt 39h4 計画](bolt_39h4_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計文書 | test_strategy（案件の書式 `G-nn[a-z].json`、G-06・G-06b・G-06c の区分、例の表 6 行、新しい節「Release 1.0 の受入条件の照合の表」（8 行）、「前の対応」の見出しを「Release 1.0 の照合の表の前の対応」に改名して残した、US-14 の締めの行）、user_story（Bolt 39h4 の決定）、system_usecase（UC-06 3a の注）、development_strategy（W7 のデモ）、release_plan（W7 のデモ項目の一覧、W8 の申し送り、Release 1.0 の締めの持ち主、確認ポイント 18）。実装より前にコミットした | `2f5ec878b` |
| 2. 守る既存のテスト | `RoutingCaseGoldenDatasetTest`・`InfoFreshnessPolicyTest`・`TrackingRecordCandidateAdoptionTest`・`PublicationOrderTest`・`ReceivedRecordDecisionServiceTest`・`ExternalMilestoneEventHandlerTest`・`IngestionServiceTest` が緑。行 8 は議題 8。性能の `measure()` の判定式は議題 7。タグの実数の数え直しは、照合の表のテスト（8 行すべてに `@wip` でないシナリオがある）が通ったことで代えた | — |
| 3. 照合 | 見出しを引数にし、完全一致で探す。Release 0.1 と Release 1.0 の表を `@ParameterizedTest` で読む。書式の外れ（見出しを添えた例外）、見出しなし・行 0 件の失敗、見出しの節で止まること、前方一致する別の見出しの表を混ぜないこと、2 つ目の表の失敗。Red は議題 4 | `2dc74e9b4` |
| 4. Golden | G-06b・G-06c、例の表 6 行、ガードのテスト（`V0_CASES`・`golden/v0` のファイル・例の表の案件の一致）。`@golden` は 6 行が流れた（JUnit の XML で `Example #1.1`〜`#1.6`） | `83c0a916d` |
| 5. 動画 | `@demo @demo-bolt-39h4/confirm-rejected-same-info-version` を付けて `demoVideo` を流し（12 分 34 秒）、新しい 1 本だけ残して既存の動画を HEAD に戻した | `60ccdf5d7` |
| 6. 性能の組 | `Bolt39h4_行7_外部ID2000種と外部版5の航海1万件は…`。外部版ごとにまとめて並べ、外部版 1〜4 は取得時刻が昇順、外部版 5 は外部版 1 より前。`measure()` に採用と差異の件数を渡す形を足した。議題 7 | `fd08fb6b5` |
| 7. 品質ゲート・変異 | 下の「品質ゲート」と「変異」。整形（spotless）の直し | `a3f7d6a43` |
| 8. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check documentationTest uiTest --continue`: `test` 5,913 件（Bolt 39h3 の 5,908 件から +5）、失敗 0。`documentationTest` 11 件（+6）、失敗 0。`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck`・`spotbugsMain`・`spotbugsTest` を含めて成功。1 回目の `check` は `spotlessJavaCheck` が `AcceptanceCriteriaCoverageTest` の整形で落ち（`spotlessApply` で直し、別コミット）、流し直して緑 |
| `uiTest` | 229 シナリオ、失敗 0（JUnit の XML では 458 件、うち 229 件は外枠で skipped。Bolt 39h3 と同じ数） |
| `demoVideo` | 成功。`bolt-39h4/confirm-rejected-same-info-version.webm` だけを残した |
| 変異 | 計画の M1〜M8（M3b・M4 の当て先の変更は議題 3）を、スクラッチパッドの使い捨てのスクリプト（`.py`。起動は M1 の 1 つで先に確かめた）で 1 つずつ当て、落ちたテストを JUnit の XML の名前で確かめた。**すべて落ちた** |
| SonarQube | 新しいコードの Quality Gate は **PASS**（新しいコードのカバレッジ 96.2%、重複 0.01696%、違反 0）。1 回目で通った |
| ArchUnit | `LayerArchitectureTest` ほかを含む `test` が通った。規則の例外は足していない |
| マイグレーション | なし |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| CI・PERF-06 | **流していない**（push していない。議題 7） |

### 変異

| # | 変異 | 落ちたテスト |
| :--- | :--- | :--- |
| M1 | 照合のテストの Release 1.0 の見出しの綴りを変える（表が読めない） | `AcceptanceCriteriaCoverageTest` の見出しのパラメーター（Release 1.0）1 件 |
| M2 | 照合が `@wip` のシナリオも数える | `ルールと例のタグを継承しwipの例とシナリオは数えない` |
| M3 | 照合の表の `@US-14-AC5` を `@US-14-AC9` にする | 照合の表のパラメーター（Release 1.0）1 件 |
| M3b | 見出しの探し方を前方一致に戻す | `見出しは完全一致で探し前方一致する別の見出しの表を混ぜない` |
| M4 | 探索が目的地に着かない列も候補に加える（議題 3） | 集約の G-01・G-02・G-06・**G-06b** ほか、受入の例 `#1.5`（G-06b）など計 40 件。G-06c の行（`#1.6`）は落ちない |
| M5 | 候補 0 件の経路版を拒む | 集約の G-06b、受入の例 `#1.5` |
| M6 | 許容時間ちょうどを情報不足にする | 集約の G-06、受入の例 `#1.4`（G-06）ほか。G-06b・G-06c は緑のまま |
| M7 | 例の表の G-06b の適合候補を「1」にする | 受入の例 `#1.5` |
| M8 | `V0_CASES` に G-06b を足さない | `案件の一覧とgoldenのファイルと例の表の案件が一致する` |

G-06c を落とす変異はない（最初から通る見張り。計画のとおり）。M4 の 40 件には G-06c を含まない。

## 計画から変えたこと

- G-06b の入力に航海 V-401 を 1 本残した（議題 2）
- 変異 M4 の当て先を探索に変えた（議題 3）
- 性能の `measure()` に採用と差異の件数を渡すオーバーロードを足した（既存の呼び出しは件数を `ROWS`・0 にして同じ）
- ステップ 3 の Red が一部だけになった（議題 4）
- 照合の表の節は、計画どおり見出しを完全一致にしたうえで、行が 1 つもない表を失敗にした（計画の行 1 (b)）

## デモ項目

- 動画（W7 のデモ項目の一覧）: 取込と分類（`bolt-39c/ingest`・`bolt-39h/discrepancy`）、隔離・差異の判断（`bolt-39d/list`・`bolt-39d2/reject`・`bolt-39d3/compare`・`bolt-39d3b/adopt`・`bolt-39h2/reject-discrepancy`）、古い外部版の警告（`bolt-39h3/old-version-warning`）、採用した航海が候補と確定に効く流れ（`bolt-39f2/stale-candidate`・**本 Bolt の新しい 1 本** [`bolt-39h4/confirm-rejected-same-info-version`](../../assets/demo/bolt-39h4/confirm-rejected-same-info-version.webm){:target="_blank"}）、再設計要になる流れ（`bolt-39f/redesign-by-adoption`）、取り込んだ実績が S-12 に届く流れ（`bolt-39g/external-milestone-draft`・`bolt-39gb/adopt-candidate`）、順序逆転の確認中（`bolt-39gc/under-review`）
- 一覧に入れなかった動画（`bolt-39c2/too-large`・`39c3/notes`・`39c4/review`・`39d2b/reject-keyless`・`39d3c/compare-milestone`・`39d3b2/adopt-milestone`・`39d4/resend`・`39g2`〜`39g4c`）は、US-14 の主流れの細部または追跡の別の機能なので入れない
- マニュアルは変えていない（画面も操作も変わらない。`docs/manual` に「Golden」「G-06」「照合の表」の記述がないことを検索で確かめた）。デモ環境の画面は開いていない（push していないので配備もしていない）

## US-14 の受入条件ごとの照合の表

[Bolt 39h4 の照合の表（test_strategy「Release 1.0 の受入条件の照合の表」）](../../design/cargo-tracker/test_strategy.md)の US-14 の AC1〜AC5 と US-12 の AC3〜AC5 を、`AcceptanceCriteriaCoverageTest`（`documentationTest`）が機械で照合し、緑。詳細の対応（Bolt 列と確かめるテスト）は同じ文書の「Release 1.0 の照合の表の前の対応」。

含む Bolt: 39・39a2・39a3・39b〜39b4・39c〜39c4・39d〜39d4・39h・39h2・39h3・39h4（US-14）。

## #18 のクローズの確認（承認の後に親が行う）

- 受入条件 5 つの照合が緑、`check`・`uiTest` 緑、`documentationTest` 緑、SonarQube PASS
- US-14 に残る未実装の受入条件はない。残る分の送り先: US-12 AC4 の有人例外案件への引き継ぎと UC-06 3a の理由の表示・引き継ぎは W8（US-20）。US-14 AC5 の採用は Release 1.0 では作らない（D-144）
- コメントに書くこと: 照合の表へのリンクと Bolt の一覧、**PERF-06 が 5 分を超えたら #18 を開き直す**（W7 の締めの CI の手動のジョブ `performance`）
- Project の Status を Done にし、release_plan の進捗表に 8 SP を数える

## 判断と学び

- 最初から通る見張り（G-06c）と、規則を縛るもの（G-06b の候補 0 件、G-06 の境界）を変異の表で分けて書き、見張りに「落とす変異がない」ことを確かめた。見張りの回帰は取込側のテスト（39h）が縛る
- 案件の一覧を 3 か所（`V0_CASES`・ファイル・例の表）に持つ形は、足し忘れが黙って抜ける（受入シナリオは JSON を直接読む）。ガードのテストで一致を縛った
- 入力が空の案件は変異に弱い。「一部だけ欠けた入力」にすると、欠落の意味（候補が作られず、情報不足にもならない）を縛れる

### ふりかえり（KPT）

- Keep: 設計文書をステップ 1 で先にコミットした（39h3 の議題 2 の Try）。変異を 1 つ先に流して起動を確かめてから全部を流した。落ちたかどうかは JUnit の XML の名前で確かめた
- Problem: 出力をログのファイルに書く形を 1 回使った（議題 1）。`check` が整形で 1 回落ちた（`spotlessApply` を最後に流していなかった）。動画の内容を再生して確かめていない（議題 6）
- Try: 画面の層以外のテストを書いたら、`check` の前に `spotlessApply` を流す。動画は撮った Bolt の終了報告の承認の場で再生して確認する

### 既知の課題（後に持ち込む）

- **W7 の締め**: PERF-06 の測り（既存のテストと新しい行 7。CI の手動のジョブ。5 分を超えたら #18 を開き直す）と判定、絞らない一覧のキャプチャを足すか、T-247 の手順化、`H2TrackingRecordLockTest` のばらつき、W7 のデモ項目の一覧の点検
- **Release 1.0 の締め**: すでに閉じたストーリー（US-06・US-07・US-16・US-18 ほか）の Release 1.0 の照合の表
- **Release 1.0 のパイロットの前**: 業務の判断 (bh)〜(bk)（台帳 D-150〜D-153）と D-141〜D-149 を業務責任者とデータ責任者に確かめる。主要実績の警告の文は S-12 の実画面で確かめる
- **W8（US-20）**: 候補なし・外部データの欠落のときの理由の表示と有人例外案件への引き継ぎ（UC-06 3a）
- **TST-02**: 業務責任者のデータが届いたら Golden dataset v0（G-06・G-06b・G-06c を含む）を差し替える
- CI と PERF-06 は push の後に確かめる

## 次の Bolt

W7 の締め（PERF-06 の測りと判定、T-247、`H2TrackingRecordLockTest`、マニュアルの点検）。release_plan の順序のとおり。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成（承認待ち） | anthropic/claude-sonnet-5-5 |
| 2026-11-13 | 推奨のとおり承認として記録した（議題 1〜9。計画との違い 1・2 を含む。#18 のクローズは承認の後に実施。動画の再生確認・SP の進捗表への計上・PERF-06 の判定は W7 の締めへ。human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
