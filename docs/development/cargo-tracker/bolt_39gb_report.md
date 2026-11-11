---
type: Plan
title: "Bolt 39gb 終了報告 - 下書きの候補を判定して示し、追跡管理者が候補を採用する"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-11T03:00:00Z }
---

# Bolt 39gb 終了報告 - 下書きの候補を判定して示し、追跡管理者が候補を採用する

## 承認の議題（T-36。先に確かめてほしいこと）

データ責任者が値を直して送り直した主要実績について、追跡管理者の S-12 が最後に公開された下書きだけを候補として示し、古い下書きに「後から公開された原本に置き換えられました」と示すようにしました。追跡管理者は候補を採用して現在状態に効かせられます。計画の確認ポイント 1〜8 は推奨のとおり承認済み（2026-11-11）として実装しました。この終了報告は**まだ承認されていません**。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **計画にない型を 2 つ足した**: ドメインの採用の結果 `MilestoneAdoption`（`aggregates`。採用した・採用済みと同じ値・拒否）と、コマンドサービスの結果 `MilestoneAdoptionOutcome`（`commandservices`）。計画の依存図は `adoptCandidate()` の戻りの型を名指ししていなかった。前例（`ExternalMilestoneReceipt`・`MilestoneRegistrationOutcome`）と同じ形で、注釈を付けないので用語集の検査の対象外。名前と置き場所でよいか | `TrackingRecord.adoptCandidate`、`TrackingRecordCommandService.adoptCandidate` |
| 2 | **画面の採用のボタンは POST と同じドメインの操作で決める**: S-12 は下書きごとに `adoptCandidate(実績番号, 閲覧者, 今)` を試し（純粋で保存しない）、採用できればボタン、できなければ理由の文を出す。ボタンと POST の判定がずれない代わりに、表示のたびに実績の数の 2 乗の比べが走る（1 件の追跡で数十件の想定） | `TrackingRecordViews.milestone` |
| 3 | **採用の版の照合は判定より先**: 画面を開いた後に新しい DE-13 が届いたとき（行 57）だけでなく、同じ画面からの二重送信も「ほかの更新が先に…もう一度採用してください」になる（登録の二重送信は既存の実績を返すのと違う）。候補が変わったかもしれないので読み直してもらう方を取った | S-12、マニュアル 9.2 |
| 4 | **P-6 の読み方**: 「出典の重複だけ冪等として扱い」を、出典の一意制約 `uk_milestone_source` の違反だけを今までどおり競合（画面は読み直しで既存の実績を見る）にし、ほかの一意制約の違反（実績番号の重なり）は `IllegalStateException` で上げる、と読んだ（前例 `MyBatisBookingRepository` の制約の名前で分ける形）。実績番号の重なりは版の条件の後には単一の流れで起こせないので、マッパーを差し替えた単体テストで確かめた | `MyBatisTrackingRecordRepository.update`、`MyBatisTrackingRecordRepositoryUniqueKeyTest` |
| 5 | **P-5**: 保存の最後に根拠の実績番号の実績が表にあるかを数え、なければ競合にする（遅延の外部キーの違反がコミットのときに 500 になる前に拾う）。保存のたびに 1 本の SELECT が増える | 同上 |
| 6 | **規律の違反が 2 回あった**: 標準入力に関わるコマンドを 2 回使った（`python -` の heredoc を 1 回。中身は空で、止めた。ファイルへの影響はない。`git commit -F -` の heredoc を 1 回。コミット `f8e42f3f7` の内容は正しい）。T-247（拒むフック）の議題に足してほしい | 下の「ふりかえり」 |
| 7 | **コミットの末尾の行が 2 種類ある**: 指示の「Co-Authored-By: Claude Sonnet 5.5」で 2 つ（`c9524df47`・`a2fc8f8a5`）、途中で受けたシステムの帰属の指示（Claude Opus 5.5 と Claude-Session の行）でそれ以後をコミットした。どちらにそろえるかを決めてほしい（履歴は書き換えていない） | `git log` |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39gb 回（W7 の 28 本目） |
| 期間 | 2026-11-11（計画の承認 `5462b0b9b` から終了報告まで） |
| 対象 | `MilestoneGroup`・`MilestoneCandidateRule`・`AdoptionRejectionReason`・`MilestoneAdoption`、`TrackingRecord.groupsOf`・`isSupersededDraft`・`adoptCandidate`、`Milestone` の採用者・採用時刻、`TrackingRecordCommandService.adoptCandidate`、S-12 の採用の欄と POST、`tracking.milestone` の 2 列と CHECK、P-5・P-6、マニュアル 9 章・12 章・用語集 |
| GitHub | #19（US-12。クローズは 39gc）、#50（P-5・P-6） |
| SP | 0（2 SP は 39gc） |
| ブランチ | develop（push していない） |
| 計画 | [Bolt 39gb 計画](bolt_39gb_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model（用語 3 行、「Bolt 39gb で決めたこと」、T-INV-06・13、集約の図）、data_model（2 列と CHECK、権限の表）、ui_design（S-12 の採用の欄と POST の行）、user_story、test_strategy、ADR-004、release_plan（39gb の行に決めたこと、台帳 D-120〜D-122（仮））、log.md。development_strategy の W7 の行はすでに 39gb を含み、直す箇所がなかった | `c9524df47` |
| 2〜3. Red と候補の判定 | 新しい型がない理由でコンパイルが落ちることを確かめてから、組・候補の規則・採用の判定を作った（行 5〜12・15・50〜56 の単体） | `a2fc8f8a5` |
| 4〜7. 表示・スキーマ・採用の操作・P-5・P-6 | S-12 の置き換えの文・採用のボタン・理由（行 43・51〜55）、`V20261111100000`（行 59）、POST とコマンドサービス（行 50・57）、認可の回帰（行 58）、P-5・P-6（行 60・61）、業務ルール層の受入シナリオ（行 63）。統合テストが「別の採用が先に表の行を採用にしていても黙って成功する」欠陥を見つけ、採用者と採用時刻まで表と比べる形に直した | `f8e42f3f7` |
| 8. 画面の層のシナリオ・動画・マニュアル・キャプチャ | 行 62 のシナリオと動画、マニュアル 9 章（採用の手順・置き換えの文・採用できない理由・競合）・12 章・用語集、キャプチャ `09-tracking-detail-candidate`（新規）と `09-tracking-detail-draft`（採用のボタンが写るので撮り直し） | `fa8272e05`、`a5cbe225b` |
| 9. 品質ゲート | 下の「品質ゲート」。`checkstyleMain`（循環的複雑度）・`spotlessJavaCheck`（書式）・SonarQube（違反 3 件）の失敗を直した | `a0e24a629`、`bf567ba56` |
| 10. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check`（`--continue`）が成功（`a0e24a629`）。`test` 5,274 件、失敗 0（Bolt 39g の 5,193 件から +81）。SonarQube の違反を直した後（`bf567ba56`）は `sonar-local:check` の中の `test` 全体と `checkstyleMain` が成功した。`spotbugsMain` はこの最後の直しの後には流し直していない |
| `uiTest` | 1 回目は新しいシナリオだけが落ちた（219 シナリオ中 1。採用のボタンの押下の後、送る前と戻った後の URL が同じなので URL の待ちが効かず、遷移の途中で axe が動いた）。リダイレクトの後の GET の応答を待つ形に直した 2 回目（`a0e24a629`）は 219 シナリオ、失敗 0（JUnit の XML では 438）。最後の直し（`bf567ba56`。振る舞いを変えない Refactor）の後には流し直していない |
| `manualScreenshots` | 成功。変わった 2 枚だけを残し、日付だけ変わったほかのキャプチャは戻した（前例と同じ） |
| `demoVideo` | 成功。`bolt-39gb/adopt-candidate.webm` だけを残し、撮り直しで変わったほかの動画は戻した |
| 変異 | 計画の 10 個（順の向き、組の情報源、候補でない下書きの採用、値違いの拒否、期待版の比較、置き換えの文、取得時刻、受信記録 ID を先に比べる、実績番号の最大、保持のみを組に入れる）。すべて落ちた。計画の「組に手入力を入れる」は手入力の実績が外部の識別を持たず組のキーを作れないので、「保持のみを組に入れる」に置き換えた |
| `checkstyleMain`・`spotlessJavaCheck` | 1 回目に落ちた（上）。直した |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| SonarQube | 1 回目は Quality Gate が FAIL（新しいコードの違反 3 件: 採用の結果の switch をレコードパターンに、の 2 件と、`Optional` を確かめずに `get` する Bug の 1 件）。直した 2 回目は PASS（新しいコードのカバレッジ 96.0%、重複 0.0188%、違反 0）。コミット `bf567ba56` のメッセージに書いた規則の番号（java:S6878）は確かめていない |
| CI | **流していない**。指示により push していないので、CI は走っていない |

Markdown Lint は動かす既存のタスクが見当たらず、流していない。

### 文書の突き合わせ（T-197・T-251）

マニュアル 9 章・12 章・用語集の文と、実装の文（`TrackingRecordViews.SUPERSEDED`・`SAME_AS_ADOPTED`・`adoptionRejection`、`TrackingRecordController` の結果と競合の文、ボタンの名前「実績 N を採用する」）を一字ずつ突き合わせた。キャプチャ `09-tracking-detail-candidate` に置き換えの文とボタンが写ることを目で確かめた。

## 計画から変えたこと

- 計画の依存図にない結果の型 `MilestoneAdoption`・`MilestoneAdoptionOutcome` を足した（議題 1）
- 行 61（P-6）は統合テストでなく、マッパーを差し替えた単体テスト `MyBatisTrackingRecordRepositoryUniqueKeyTest` で確かめた。出典の重複の違反の競合は既存の PostgreSQL の統合テストが確かめている
- 行 57 の「PostgreSQL での同時の採用と DE-13 の到着」は、ラッチで重ねる統合テストを作っていない。版の照合（コマンドサービスの単体）、表の行がもう下書きでないときの競合（PostgreSQL の統合）、行のロックの再利用（39g の行 28 の統合）で確かめた（決定 (1) の打ち切りの順の最初の項目）
- ステップ 4 と 6 を 1 つのコミットにした（画面の採用のボタンが POST と同じドメインの操作を使うため）
- 監査は足していない（主要実績の登録も監査を書かない前例。決定 (5a)・確認ポイント 7）

## デモ項目

- 動画: [`@demo-bolt-39gb/adopt-candidate`](../../assets/demo/bolt-39gb/adopt-candidate.webm){:target="_blank"}（値を直して送り直した主要実績の古い下書きが置き換えと示され、追跡管理者が候補を採用して現在状態が「輸送中」になる）
- マニュアル: 9 章・12 章・用語集を直し、キャプチャ `09-tracking-detail-candidate` を足した
- デモ環境の画面は開いて確かめていない（push していないので配備もしていない）

## 判断と学び

- 画面のボタンと POST が同じ純粋なドメインの操作を呼ぶと、「出したボタンが押すと拒否される」ずれが構造で起きなくなる
- 状態だけを比べて書き換えを決めると、別の人が同じ遷移を先に書いた場合を見落とす。統合テストで表を直接書き換えて初めて分かった

### ふりかえり（KPT）

- Keep: 統合テストで「表の行がもう採用」を直接作る行 57 のテストが、黙って成功する欠陥を見つけた
- Problem: 標準入力に関わるコマンドを 2 回使った（`python -` の heredoc、`git commit -F -` の heredoc）。指示の最初に書かれていたのに守れなかった。画面の層の待ちを URL で書き、PRG で同じ URL に戻る形で外した
- Try: T-247（拒むフック）に `git commit -F -` と heredoc を足す。PRG で同じ画面に戻る操作の待ちは、URL でなく応答で書く

### 既知の課題（後に持ち込む）

- 39gc: 採用のときの時系列の確認中（ここで作った採用の操作に判定を足す）
- 39g2: 採用の経路にも DE-09・DE-10 が掛かる
- W12（US-13）: 採用済みと値の違う候補を二者確認で直す
- 配備の前: ステージング・本番に外部原本の採用済みの実績がないこと（39g の申し送りと同じく、CHECK の追加が失敗しないこと）
- 行 57 の PostgreSQL でラッチで重ねる同時の統合テスト（上の「計画から変えたこと」）

## 次の Bolt

Bolt 39gc（確認中と DE-18、手入力の規則）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-11 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 39gb 計画](bolt_39gb_plan.md)、[Bolt 39g 終了報告](bolt_39g_report.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[データモデル](../../design/cargo-tracker/data_model.md)、[ユーザーマニュアル 9 章](../../manual/09-追跡管理.md)
