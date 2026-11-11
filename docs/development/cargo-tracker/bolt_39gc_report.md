---
type: Plan
title: "Bolt 39gc 終了報告 - 時系列に反する実績を確認中にし、DE-18 を出し、手入力の取消済み・完了後を決める"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-11T07:30:50Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-11T07:50:14Z }
---

# Bolt 39gc 終了報告 - 時系列に反する実績を確認中にし、DE-18 を出し、手入力の取消済み・完了後を決める

## 承認の議題（T-36。先に確かめてほしいこと）

追跡管理者が手入力で登録した実績や候補を採用した実績が、採用済みの実績と時系列に反するとき、実績は状態「確認中」で残り、追跡の現在状態は「確認中」になって S-12 に理由が示され、S-11 に状態バッジが出るようにしました。確認中に入るときだけ DE-18 を発行します。取消済みの予約への手入力は拒否し、完了した追跡への手入力は保持のみで残します。計画の確認ポイント CP-1〜CP-9 は承認済み（2026-11-11）のとおりに実装しました。US-12 の受入条件 AC1〜AC5 はすべて受入シナリオで通ります。

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **計画に名指しのない型の形を変えた**: ドメインの `MilestoneAdoption.Rejected` に値の違う判断済みの実績（`Optional<Milestone>`）を、コマンドサービスの `MilestoneAdoptionOutcome.Rejected` に判断済みの実績の状態（`Optional<MilestoneState>`）を足した（S-12 と POST が「確認中の実績と値が違います」を出し分けるため。理由の値は足していない。決定 (7)）。`MilestoneRegistrationOutcome.Registered`・`MilestoneAdoptionOutcome.Adopted` に実績の状態を足した。`MilestoneAdoption.Adopted`・`MilestoneRegistration` に DE-18（`Optional`）を載せた（決定 (4) の Refactor で「`Adopted` に実績の状態で表す」を選んだ）。`TrackingRecord` に `statusFromAdopted()`（公開）と、確認中の理由を受ける `reconstitute` の 14 引数の形を足し、13 引数の形は理由なしとして残した | この形で承認する | `MilestoneAdoption`、`MilestoneAdoptionOutcome`、`TrackingRecord` |
| 2 | **DE-18 の集約の版は保存した後の版**（読み込んだ版 + 1）にした。DE-22 は INSERT の版 0 をそのまま載せており、「発行元の集約の版」を保存の後の版と読んだ | この読み方で承認する | `TrackingRecord.placedUnderReview`、`TrackingPlacedUnderReview` の Javadoc |
| 3 | **S-12 の採用の POST で「判断済みと同じ値」の結果の文は今のまま**: 判断済みの実績が確認中でも「実績 N は採用済みの実績 M と同じ値です。採用は要りません。」と出る（S-12 の欄は「確認中の実績と同じ値です」）。結果の型に状態がないため。計画の行 14 は「POST は何もしない」とだけ縛っている | 次に S-12 を触る Bolt で文をそろえる（既知の課題に置いた） | `TrackingRecordController.adoptMilestone` |
| 4 | **S-13 の取消済みの拒否は発生時刻の項目の誤りとして示す**（エラー要約は「発生時刻: 取消済みの予約には主要実績を登録できません。」）。決定 (8) の「`rejection` の腕を例外から文に直す」をそのまま行った。項目でなく画面全体の誤りにするとエラー要約のテンプレートの直しが要る | このまま承認し、画面全体の誤りにするかは UI のレビューに送る | `TrackingRecordController.rejection`、ui_design の S-13 |
| 5 | **規律の違反が 3 回あった**（標準入力につなぐコマンド）: `cat > ファイル`（相手がディレクトリで即座に失敗）1 回、`python -` 1 回（入力待ちで止まり、止めた）、`cat > ファイル <<'EOF'` の heredoc 1 回（空のファイルをスクラッチパッドに作っただけ）。どれもリポジトリへの影響はない。指示の冒頭に書かれていたのに守れなかった | T-247（拒むフック）の議題に足し、W7 の締めで諮る | 下の「ふりかえり」 |
| 6 | **受入シナリオ（業務ルール層）の Red を流していない**: 単体の Red（コンパイルの失敗 40 件）は確かめたが、`@US-12-AC3`・`AC4`・`AC5` の手入力の分のシナリオはドメインの Green の後に書いた。変異 M7（手入力の取消済みの判定を外す）でこのシナリオが落ちることは確かめた。画面の層のシナリオは 1 回目の `uiTest` で落ち（下）、直して通った | 記録として承認する（T-254 は守った: Red のコミットはない） | 下の「計画から変えたこと」 |
| 7 | **#19 のクローズと US-12 の 2 SP**: AC1〜AC5 がすべて受入シナリオで通った。承認の後に #19 をクローズする（指示により、この Bolt ではクローズしていない） | 承認の後にクローズし、2 SP を数える | `register_milestone.feature`、test_strategy の照合の表 |
| 8 | **確認中から出る道がない**（決定 (3)。US-13・W12）。Release 1.0 の間に確認中になった追跡は確認中のまま残る | 計画のとおり W7 の締めで諮る（台帳 D-124 の注） | release_plan の台帳 D-124 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39gc 回（W7 の 29 本目） |
| 期間 | 2026-11-11（計画の承認 `86f19bb0b` から終了報告まで） |
| 対象 | `MilestoneSequenceRule`・`UnderReviewReason`・`TrackingPlacedUnderReview`（新規）、`CurrentStatusDeriver`・`DerivedStatus`・`TrackingRecord`・`Milestone`・`MilestoneGroup`・結果の型、`TrackingRecordCommandService`、S-11・S-12・S-13、`tracking_record.under_review_reason`（`V20261112100000`）、マニュアル 9 章・用語集 |
| GitHub | #19（US-12。クローズは終了報告の承認の後） |
| SP | 2（US-12 の残り） |
| ブランチ | develop |
| 計画 | [Bolt 39gc 計画](bolt_39gc_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model（用語 2 行と既存の 4 行、「Bolt 39gc で決めたこと」、状態遷移図、T-INV-03・05、`CurrentStatusDeriver`・`MilestoneSequenceRule` の行、DE-18、W7 CP7 の境界を外部データの節と追跡の節に）、data_model（型と CHECK、「Bolt 39gc で変える列」）、ui_design（S-11・S-12・S-13）、user_story、system_usecase（UC-10 2b）、test_strategy（AC3 の行と AC4・AC5 の手入力の行）、ADR-004、release_plan（39gc の行、W7 CP7・8・10・23 の済み、台帳 D-123〜D-127（仮）と D-117 の注）、log.md。development_strategy の W7 の行はすでに 39gc を含み、直す箇所がなかった | `40a85ebb3` |
| 2〜8. Red・規則・採用・手入力・スキーマ・DE-18・表示 | 新しい型がない理由でテストのコンパイルが 40 件落ちることを確かめてから実装した。状態の組み合わせの行 1〜20・22〜27・29〜31 のテスト。マイグレーションの `ALTER COLUMN … SET DATA TYPE VARCHAR(30)` は H2（dev のプロファイルのテストと `uiTest`）と PostgreSQL（Testcontainers の統合テスト）の両方で通ったので、型を変えた（計画の既定どおり） | `9d895fe2a` |
| 9. PostgreSQL の同時の採用 | 行 21。同じ候補の採用 2 本をラッチで重ね、先が確認中に採用、後は `Conflict`、DE-18 は 1 件 | `4a2de6f7e` |
| 8・10. 画面の層のシナリオ・動画・マニュアル | 行 28 の `under_review_milestone_ui.feature`（`@demo-bolt-39gc/under-review`）、マニュアル 9 章（確認中の追跡を確かめる、採用の注記、登録の結果の文、取消済みの拒否、保持のみ）・用語集（確認中を足し、採用・保持のみを直した）、キャプチャ `09-tracking-detail-under-review`（新規） | `5fcb85d6e`、`f72d54b81` |
| 11. 品質ゲート | 下の「品質ゲート」。直しは要らなかった | — |
| 12. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check`（`--continue`）が成功。`test` 5,349 件、失敗 0（Bolt 39gb の 5,274 件から +75）。`documentationTest`（用語の検査・受入条件の照合）、`checkstyleMain`・`checkstyleTest`、`spotlessJavaCheck`、`spotbugsMain` を含む。最後のコードの直し（`5fcb85d6e` の画面のシナリオの直し）の後に流した |
| `uiTest` | 1 回目は新しいシナリオだけが落ちた（220 シナリオ中 1。JUnit の XML では 440 中 1）。既存のステップ「主要実績の一覧に … が示される」が一覧の先頭の項目だけを見ており、発生時刻の順で先頭に来ない実績 2 を見つけられなかった。実績番号で項目を引くステップに直した。最後の直しの後の 2 回目（`check`・SonarQube の後）は 220 シナリオ、失敗 0（JUnit の XML では 440） |
| `manualScreenshots` | 成功。新しい `09-tracking-detail-under-review` だけを残し、日付だけ変わったほかのキャプチャは HEAD に戻した。確認中の撮影はほかの章の撮影の後に置いた（追跡が確認中になり、5 章の照会の結果の画面が変わるため） |
| `demoVideo` | 成功。`bolt-39gc/under-review.webm` だけを残し、撮り直しで変わったほかの動画（88 件）は HEAD に戻した |
| 変異 | 計画の 11 個をスクラッチパッドの使い捨てのスクリプトで 1 つずつ当て、追跡のテストと業務ルール層の受入シナリオを流した。すべて落ちた（M1 段の向きを逆 → 規則・コマンドサービス・画面・統合、M2 同じ時刻も反する → `MilestoneSequenceRuleTest`、M3 出発と積替を別の段 → 同、M4 下書き・保持のみとも比べる → 同、M5 確認中があっても採用済みから導く → 導出・コマンドサービス・画面・統合、M6 根拠を最も新しい確認中 → 導出・集約、M7 手入力の取消済みの判定を外す → 集約・受入シナリオ、M8 完了後を現在状態で判定 → 集約 2 クラス、M9 組から確認中を外す → 集約・画面、M10 すでに確認中でも DE-18 → 集約・コマンドサービス、M11 保存で理由を書かない → 統合 2 クラス（CHECK が拒否する）） |
| SonarQube | Quality Gate が PASS（新しいコードのカバレッジ 96.0%、重複 0.01856%、違反 0）。1 回目で通った |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| CI | **流していない**。指示により push していないので、CI は走っていない |

Markdown Lint は動かす既存のタスクが見当たらず、流していない。

### 文書の突き合わせ（T-197・T-251）

マニュアル 9 章・用語集の 8 つの文（確認中の理由の文、採用の注記、採用・登録の確認中の結果、保持のみの結果、取消済みの拒否、確認中の実績と値が違う・同じ値）が、`TrackingRecordViews`・`TrackingRecordController` の文と一字ずつ一致することをスクリプトで確かめた（N・M は実装では値の連結なので、固定の部分を比べた）。キャプチャ `09-tracking-detail-under-review` に、状態バッジ・理由の文・状態「確認中」・採用の注記が写ることを目で確かめた。

## 計画から変えたこと

- 業務ルール層の受入シナリオは単体の Green の後に書いた（議題 6）
- 行 25（C-10）は `TrackingRecordCustomerViewTest` でなく `TrackingRecordCandidateAdoptionTest` の `Bolt39gc_採用の確認中` に置いた（確認中の追跡記録を採用の経路で作るため）
- 行 20 の H2 の確かめは専用の統合テストを作らず、dev のプロファイル（H2）で動く `uiTest`・`manualScreenshots`・`demoVideo` で確認中の保存（2 つの CHECK を通る UPDATE）が通ることで代えた。PostgreSQL は統合テストで行 18〜21 を確かめた
- ステップ 2〜8 のコードを 1 つのコミットにした（結果の型の形の変更がドメイン・コマンドサービス・画面にまたがり、途中で分けると develop にコンパイルの通らない状態が積まれるため）
- 新しいテストのクラスは計画の決定 (13) のとおり `MilestoneSequenceRuleTest` だけにし、ほかは既存のクラスの `Bolt39gc_…` に置いた

## デモ項目

- 動画: [`@demo-bolt-39gc/under-review`](../../assets/demo/bolt-39gc/under-review.webm){:target="_blank"}（到着の後に、到着より後の発生時刻の集荷を登録すると、S-12 が確認中のバッジと理由を示し、S-11 にもバッジが出る）
- マニュアル: 9 章・用語集を直し、キャプチャ `09-tracking-detail-under-review` を足した
- デモ環境の画面は開いて確かめていない（push していないので配備もしていない）

## 判断と学び

- 完了後の判定を「採用済みの実績から導いた状態」で見ると、確認中の現在状態に隠れずに T-INV-05 が効く（行 11。変異 M8 が 2 クラスで落ちた）
- 現在状態と確認中の理由の対を `DerivedStatus` の不変条件にしたので、表の CHECK（`ck_tracking_record_under_review`）と集約の規則が同じ形になり、保存で理由を書かない変異（M11）は CHECK が拒否して落ちた
- 既存の画面のステップが「一覧の先頭」を前提にしていると、発生時刻の順の並びで新しい実績を見落とす。実績番号の項目で引く方が壊れにくい

### ふりかえり（KPT）

- Keep: 状態の組み合わせの表の行をテストの名前に付け、変異 11 個がすべてどこかの行で落ちた
- Problem: 標準入力につなぐコマンドを 3 回使った（議題 5）。指示と memory にあるのに、確かめのつもりの短いコマンドで繰り返した。業務ルール層の受入シナリオの Red を流さなかった
- Try: Bash でファイルを作る・確かめるときは Write と Read だけを使い、`cat >`・`<<`・`python -` を打たない（T-247 のフックで拒む）。受入シナリオは単体の Red と同じ段で書いて Red を流す

### 既知の課題（後に持ち込む）

- W7 の締め: 確認中から出る道が Release 1.0 のパイロットの前に要るか（台帳 D-124 の注）。T-247 に標準入力のコマンドを足す
- 次に S-12 を触る Bolt: 判断済みの実績が確認中のときの、採用の POST の「同じ値」の結果の文（議題 3）
- 39g2: DE-09・DE-10 は確認中になった集荷・引渡しでは出さない（採用済みでないため）。予約状態 `COMPLETED` の書き手を作るとき、完了後の判定（採用済みから導いた状態）が通ること
- 39h: 外部データの差異と追跡の確認中の境界（domain_model の 2 つの節と ADR-004 に書いた）
- W9: DE-18 の受け手（通知）と T-184、`tracking.domain.events` の名前付きインターフェース、C-10 の確認中の説明（US-09 AC2）
- 配備の前: ステージング・本番に `current_status = 'UNDER_REVIEW'` の行と `under_review_reason` に値のある行がないこと（CHECK の追加が失敗しないこと）
- Release 1.0 のパイロットの前: 業務の判断 (ag)〜(ak) を業務責任者に確かめる（台帳 D-123〜D-127）

## 次の Bolt

W7 の残り（39g2・39h など。release_plan の順序）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-11 | 推奨のとおり承認として記録した（議題 1〜8。議題 5 は T-247 の議題に足し、議題 8 は W7 の締めで諮る。#19 をクローズした。human:kakimomokuri） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-11-11 | 初版作成 | anthropic/claude-sonnet-5-5 |

## 関連ドキュメント

- [Bolt 39gc 計画](bolt_39gc_plan.md)、[Bolt 39gb 終了報告](bolt_39gb_report.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[データモデル](../../design/cargo-tracker/data_model.md)、[ユーザーマニュアル 9 章](../../manual/09-追跡管理.md)
