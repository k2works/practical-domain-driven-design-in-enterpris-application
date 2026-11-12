---
type: Plan
title: "Bolt 39h2 終了報告 - S-17 で差異を一覧に出して却下する"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-12T14:40:00Z }
---

# Bolt 39h2 終了報告 - S-17 で差異を一覧に出して却下する

## 承認の議題（T-36。先に確かめてほしいこと）

S-17 の判断を待つ一覧と件数に差異を出し、判断の画面「差異の判断」で差異の理由の全文と受け取った値を読んで、理由を書いて却下できるようにしました（US-14 AC5、UC-15 5）。差異は判断できるが採用できず（採用のファクトリーが拒み、採用できる隔離の定めは分類を最初に見て `DISCREPANCY` を返す）、判断の画面に採用の欄は出ません。S-16 のリンクは「この取込の隔離・差異をすべて確認する」になり、差異だけの取込にも出ます。名前の見直し（`UndecidedQuarantine` → `UndecidedRecord` ほか）は振る舞いを変えない準備のリファクタリングとして先にコミットしました。計画の確認ポイント 1〜9 は承認済み（2026-11-12）のとおりに実装しました。スキーマ・認可・外部連携・ArchUnit の例外・DE-29 の契約・新しいコンテキスト間の依存は変えていません。

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **規律の違反 2 件**: 作業の途中で標準入力につながる形のコマンドを 2 回流した。(a) 確かめのつもりで `cat > /dev/null` に here-string（`<<<`）をつなぐ形を 1 回、(b) 置換のつもりで `python3 -I -`（標準入力から読む形）と `cat > /dev/null` を 1 回（2 分の時間切れで背景に回り、`TaskStop` で止めた）。どちらも何も書かず、リポジトリの外やホーム直下には書いていない。ファイルの直しは以降すべて Write・Edit と `.py` のファイル・`sed` で行った | 記録として承認する。Bolt 39h の Try（確かめのつもりのコマンドも、流す前に標準入力につながる形でないかを見る）を 2 回続けて守れなかったので、**コマンドを流す前のチェックを手順にする案**を Try に入れた | — |
| 2 | **Red と Green を同じコミットにした**（前例 Bolt 39h。T-254）。Red は「テストのコンパイルが落ちる（`UndecidedRecord` の分類・結果の型の分類・`DISCREPANCY` がない）」ことで確かめた。ただし 1 回目のコンパイルの失敗は、名前の見直しの後の置換スクリプトが main の `MyBatisReceivedRecordRepository` にも仮の引数を差したことによる main 側の失敗が先に出て、テスト側の失敗の件数は数えていない | 記録として承認する | コミット `2cd418e9d` |
| 3 | **差異の採用の POST は画面の操作では起きない**: 差異の判断の画面に採用の欄がないので、利用者は採用の POST に入れない。URL を直接叩いた場合は採用せず判断の画面に戻ってエラー要約に差異の文が出る（単体と画面の層で確かめた）。マニュアルには書いていない | 画面の操作で起きない経路なのでマニュアルには書かない | `UndecidedRecordControllerTest` 行 9 (c)(d) |
| 4 | **範囲の外のマニュアル 1 行を直した**: `00-はじめに.md` :28 の「判断を待つ隔離の一覧の確認」を「判断を待つ隔離・差異の一覧の確認」にした（サブエージェントが報告し、実装の語に合わせた） | この形で承認する | `docs/manual/00-はじめに.md` |
| 5 | **差異のまとめての却下は作っていない**（決定 (8)、D-149）。1 つの取込で差異が数千件になりうる件の頻度は、Release 1.0 のパイロットの前にデータ責任者に確かめる | 計画のとおり | 台帳 D-149 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39h2 回（W7 の 36 本目） |
| 期間 | 2026-11-12（計画の承認 `9b36da5e1` から終了報告まで） |
| 対象 | 外部データ: `UndecidedRecord`（旧 `UndecidedQuarantine`。分類の項目と不変条件）、`ReceivedRecordDecision`（`decidable`・採用のファクトリー）、`DecisionOutcome`（`Rejected`・`AlreadyDecided` の分類、`NotAdoptableReason.DISCREPANCY`）、`ReceivedRecordDecisionService`、`ReceivedRecordRepository` と MyBatis（`ReceivedRecordMapper` の Java と XML）、`UndecidedRecordPage.Item`、`UndecidedRecordQueryService`、`UndecidedRecordController`、テンプレート 4 つ（`index.html`・`decision.html`・`keyless-rejection.html`・`result.html`）。設計文書 8 本、マニュアル 12 章・付録 B・用語集・`00-はじめに.md` とキャプチャ 5 枚 |
| GitHub | [#18](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/18)（US-14。クローズは 39h4） |
| SP | 0（US-14 の 8 SP は 39h4 で数える） |
| ブランチ | develop |
| 計画 | [Bolt 39h2 計画](bolt_39h2_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計文書 | domain_model（用語集の 3 行・状態遷移図・Bolt 39h2 で決めたこと）、data_model、ui_design（S-16・S-17・差異の判断の画面のワイヤーフレーム）、test_strategy、ADR-004、system_usecase、user_story、release_plan。サブエージェントに任せ、名前と文を先に渡した。変えた PlantUML 8 図は描画を確かめた | `134da9cb6` |
| 2. 守る既存のテスト | Red の前に `test` を流し 5,818 件・失敗 0（7 分 11 秒）。期待が変わる既存のテストの数は計画の数のとおり（「この取込の隔離をすべて確認する」46 か所・「判断を待つ隔離」120 か所・結果の型の生成と比較 21 か所ほか）。画面の層は `uiTest` がシナリオを選べないので個別には流していない（39g3〜39h と同じ） | — |
| 3. 準備のリファクタリング | 決定 (2) の名前の見直し（型・ファイル・メソッド・SQL の id と断片・`quarantinedOf`）。`check` は成功（7 分 16 秒）、用語集の英語名と Javadoc の最初の語は `LivingGlossaryConsistencyTest` で確かめた | `0c012f623` |
| 4〜8. Red・Green・画面の層 | 行 1〜13 のテストと受入シナリオ（`reject_discrepancy_ui.feature`）を書き、実装した。`check` と `uiTest` が成功 | `2cd418e9d` |
| 9. マニュアルとキャプチャと動画 | 12 章・付録 B・用語集をサブエージェントに任せた（決定の文を先に渡した）。キャプチャは変わる 4 枚を撮り直し、差異の判断の画面 `12-undecided-record-discrepancy` を足した。動画 `@demo-bolt-39h2/reject-discrepancy` を撮った | `a8a6b863b` |
| 10. 品質ゲート | 下の「品質ゲート」。SonarQube の違反 2 件を直した | `642399528` |
| 11. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check`（`--continue`）: `test` 5,864 件、失敗 0（Bolt 39h の 5,818 件から +46）。`documentationTest` 5 件、失敗 0。`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck`・`spotbugsMain` を含めて成功。最後の直しの後に `spotbugsMain`・`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck`・`uiTest` を流し直した |
| `uiTest` | 227 シナリオ、失敗 0（JUnit の XML では 454 件、うち 227 件はシナリオの外枠で skipped。Bolt 39h の 226 から +1） |
| `manualScreenshots` | 成功。`12-ingestion-result`・`12-ingestion-review`・`12-undecided-records`・`12-undecided-records-keyless-rejection` を撮り直して残し、新しい `12-undecided-record-discrepancy` を足した。日付だけ変わったキャプチャ（`12-undecided-record-decision` を含む）は HEAD に戻した。差異の判断の画面は目で確かめた（見出し「差異の判断」、種類「差異」、差異の理由、「受け取った値」の領域だけ、却下の欄の後に採用できない理由の文） |
| `demoVideo` | 成功（12 分 16 秒）。`bolt-39h2/reject-discrepancy.webm` だけを残し、撮り直された既存の動画は HEAD に戻した |
| 変異 | 計画の 14 個を、スクラッチパッドの使い捨てのスクリプトで 1 つずつ当て、決めたテストを流した。落ちたかどうかは JUnit の XML の落ちたテストの名前で決めた。本番の前に M14 だけを流して起動を確かめた（Bolt 39h の Try）。M5・M8 は 1 回目に当てる文字列が 2 件に一致して当てられなかったので、前後の文を足して流し直した。14 個すべて落ちた |
| SonarQube | 1 回目は Quality Gate が FAIL（新しいコードの違反 2 件。テストの `record` という名前が restricted identifier）。直した後の 2 回目は PASS（新しいコードのカバレッジ 96.2%、重複 0.01713%、違反 0。全体のカバレッジ 96.0%） |
| ArchUnit | `LayerArchitectureTest`・`DddAnnotationArchitectureTest`・`MapperSchemaArchitectureTest`・`PublicApiArchitectureTest` を含む `test` が通った。規則の例外は足していない。Modulith の検証も通った |
| マイグレーション | なし（スキーマを変えない。決定 (7)）。差異の判断の行は H2 と PostgreSQL（Testcontainers）の契約のテストで書けた |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| CI・PERF-06 | **流していない**。指示により push していないので、CI と Linux の `performanceTest` は走っていない（照会の形が変わらないので PERF-06 は計画でも流さない） |

### 変異

| # | 変異 | 落ちたテスト（行） |
| :--- | :--- | :--- |
| M1 | 判断できる受信記録を隔離だけに戻す | `ReceivedRecordDecisionTest` 行 3 と行 1 (d)、`ReceivedRecordDecisionServiceTest` 行 3 (a)(b)(d)・行 2 など 11 件 |
| M2 | 採用のファクトリーが差異を受ける | `ReceivedRecordDecisionTest` 行 1 (d)（ファクトリーの層）ほか 2 件 |
| M3 | `DISCREPANCY` を情報源の確かめの後に確かめる | `ReceivedRecordDecisionServiceTest` 行 2（情報源が停止された差異） |
| M4 | 差異で `NotAdoptable` を返さない | `ReceivedRecordDecisionServiceTest` 行 2 の 3 つと `Bolt39d4` の理由ごとの組 |
| M5 | 一覧の SQL を隔離だけに戻す | H2・PostgreSQL の契約の行 4 の 4 本ずつ 8 件 |
| M6 | メモリの実装の一覧を隔離だけに戻す | `InMemoryExternalDataRecordRepositoriesTest` 行 4 の 4 件 |
| M7 | 件数の SQL だけ隔離に戻す | H2・PostgreSQL の契約の行 4 の 3 本ずつ 6 件 |
| M8 | 行の型が分類を読まない（いつも隔離） | H2・PostgreSQL の契約の行 4 の 3 本ずつ 6 件 |
| M9 | S-16 のリンクの条件を隔離だけに戻す | `ExternalDataIngestionControllerTest` 行 10 (b) と Bolt 39h の行 8 (c) |
| M10 | 却下の知らせが分類を見ない | `UndecidedRecordControllerTest` 行 9 (a)(b) |
| M11 | 判断済みの知らせが分類を見ない | 同 行 9 (a)(b)・(f) |
| M12 | 差異の判断の画面に「この外部版の採用値」の領域を出す | 同 行 8 (a)(c) |
| M13 | `quarantineValues` が差異に値を返さない | `UndecidedRecordQueryServiceTest` 行 8 |
| M14 | 一覧の行の不変条件で差異の外部 ID なしを許す | `UndecidedRecordTest` 行 6 (b) |

### 文書の突き合わせ（T-197・T-251）

決定 (5) の文（件数・0 件・caption・列の見出し・ページ送りの名前・採用できない理由の `DISCREPANCY` の文・「差異を却下しました。」「この差異は判断済みです。」・S-16 のリンク・「内容の違う原本と差異は含みません。…」）は、計画から写して `UndecidedRecordController`・テンプレート・`UndecidedRecordControllerTest`・`ExternalDataIngestionControllerTest`・画面の層のステップ・feature・ui_design・マニュアル 12 章・付録 B・用語集で同じ文にした。採用できない理由の文は、controller・コントローラーのテスト・画面の層のステップの 3 か所に同じ文字列がある。

## 計画から変えたこと

- `UndecidedRecordPage.Item` は `keyless` の項目を `classification` に置き換え、`keyless()` を外部 ID の有無から導く方法にした（項目の重複を避ける。呼び手のコンストラクターの変更は 24 か所のテストに及んだので、書き換えのスクリプトを使った）
- `UndecidedRecord` の項目の順は計画のクラス図の順にした（`receivedRecordId`、`classification`、…）
- `adoptionCheckOf` の最初に分類を見る処理を足した（計画のとおり）。`refusalOf(ReceivedRecord)` の判断済みも分類を渡した（`AlreadyDecided` を作る 4 か所すべて）
- GET の着地は `decisions.refusalOf(id).orElseThrow()` で読み直す形にし、既存の 1 本のコントローラーのテストは「読み直す側が判断済みを返す」形に直した（行の期待の変更。計画の「期待が変わる既存のテスト」の範囲）
- 受け取った値の領域の出し分けは、controller が差異のときに `adoptedValues` を渡さず、テンプレートが `th:if="${adoptedValues}"` で領域を出さない形にした。照会のサービスも差異では採用値の照会を引かない（行 8 (a) の「採用値は空」を呼び出しの記録で縛る）
- 監査の配信の確かめ（行 11）は `Bolt39h_差異の監査の配信` の入れ子に足した
- 新しいテスト: `UndecidedRecordTest`（新規）、契約の行 4・5（5 本）、判断のサービスの差異の組（5 本）、コントローラーの差異の組（9 本）、照会の差異の組（2 本）、`ReceivedRecordDecisionTest` の差異の組（2 本）、S-16 のリンク（1 本）、監査の配信（1 本）、受入シナリオ 1 本（`reject_discrepancy_ui.feature`）、マニュアルのキャプチャのシナリオ 1 本
- 守る既存のテストのうち画面の層のシナリオは Red の前に個別には流していない（`uiTest` はシナリオを選んで流せない）

## デモ項目

- 動画: [`@demo-bolt-39h2/reject-discrepancy`](../../assets/demo/bolt-39h2/reject-discrepancy.webm){:target="_blank"}（データ責任者が東京からロッテルダムへの航海を後に取得した外部版 2 で取り込んだ後に、前に取得した外部版 1 を取り込んで差異を作り、取込の結果の「この取込の隔離・差異をすべて確認する」から一覧を開き、差異の判断の画面で採用の欄がないことを確かめて、理由を書いて却下する）
- マニュアル: 12 章・付録 B・用語集を直し、第 12 章のキャプチャ 4 枚を撮り直して 1 枚を足した（`12-undecided-record-discrepancy`）
- デモ環境の画面は開いて確かめていない（push していないので配備もしていない）

## 判断と学び

- 結果の型に分類を持たせたので、却下・判断済みの知らせを分類のすべてで選ぶ `switch`（`default` を置かない）にできた。DE-29 を変えずに差異の却下を区別できた（差異かどうかは同じ受信記録の DE-31 の監査で読む）
- 差異の外部原本は採用値を持たない（Bolt 39h の決定 (6)）ので、判断の画面は「受け取った値」の領域だけにした。比べる相手のない「この外部版の採用値はありません。」を出さずに済んだ。この外部 ID の採用値を並べるのは 39h3
- 差異の採用を判断のサービスの定めだけでなく、採用のファクトリーでも拒む二重の守りにしたので、変異 M2 をファクトリーの層で落とせた
- テストの識別子に `record` を使うと SonarQube の restricted identifier になる

### ふりかえり（KPT）

- Keep: ステップ 1 とマニュアルの文書の直しをサブエージェントに任せ、名前と文を先に渡した（用語の食い違いは範囲の外の 1 行だけ）。変異のスクリプトを落ちたテストの名前で判定し、本番の前に 1 つだけ流して起動を確かめた。置換のスクリプトで呼び手 24 か所の機械的な直しを短時間で済ませた
- Problem: 標準入力につながるコマンドを 2 回流した（議題 1）。名前の見直しの sed が日本語の語の隣で語の境界を取りこぼし、コメントの旧名 1 件を後から直した。テスト名に全角の中黒を含めてしまい、コンパイルより先に spotless が落ちた。置換のスクリプトが main にも仮の引数を差した
- Try: **コマンドを流す前に、引数に `-`・`<<`・`> ファイル` が含まれないかを見るチェックを手順として声に出して確かめる**（Bolt 39h の Try の繰り返し）。メソッド名・テスト名に使う語を sed で置換するときは、識別子に使えない文字（`・` など）を `void` の行で拾う。置換のスクリプトは対象のディレクトリを main と test で分けて流す

### 既知の課題（後に持ち込む）

- **39h3**: 判断の画面に「この外部 ID の採用値」を示し、差異の判断の画面にも同じ領域を出すかを決める。古い外部版の隔離の採用の警告。S-17 の絞らない一覧から取込で絞る入口（分類で絞る操作が要るかも）。`QuarantineValues`・`QuarantineValueViews`・フラグメントの名前
- **39h4**: G-06、受入条件の照合の表（US-14 AC5 を 39h2 の却下だけで照合する）、W7 のデモの動画、#18 のクローズと 8 SP。性能テストの複数の外部版の組
- **Release 1.0 のパイロットの前**: 業務の判断 (be)〜(bg) と差異のまとめての却下の要否（D-147〜D-149）
- PERF-06 と CI は push の後に確かめる

## 次の Bolt

39h3（判断の画面にこの外部 ID の採用値を示し、古い外部版の隔離の採用を警告する。S-17 の取込で絞る入口）。release_plan の順序のとおり。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-12 | 初版作成 | anthropic/claude-sonnet-5-5 |
| 2026-11-12 | 推奨のとおり承認として記録した（議題 1〜5。以降は推奨で進める指示による。T-247 の手順化・配備前の件数の確認は W7 の締めへ。human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
