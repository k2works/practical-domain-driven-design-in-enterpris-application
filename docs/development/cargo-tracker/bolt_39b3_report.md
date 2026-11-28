---
type: Plan
title: "Bolt 39b3 終了報告 - 採用値と DE-12・DE-13・DE-14 の発行"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-03T14:27:28Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-03T14:27:29Z }
---

# Bolt 39b3 終了報告 - 採用値と DE-12・DE-13・DE-14 の発行

## 承認の議題（T-36。先に確かめてほしいこと）

分類が採用の受信記録に採用値を追記し、航海の採用値（DE-12）・主要実績の採用値（DE-13）・外部原本の隔離（DE-14）を取込のトランザクションの中で発行するようにしました。人に個別に諮って決めたのは、イベントの型・採否の監査の範囲・寄港の上限・許可リストの 4 つです。ほかのゲートは推奨のとおり承認として記録しています（Recommend 承認の指示による）。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **イベントの形（公表された言語の契約）**: DE-12・DE-13 は `externaldata.domain.events` の中に文字列と共有カーネルの型で値の写しを持ち（寄港は入れ子の `AdoptedPortCall`）、出典は `Source(EXTERNAL_RECORD, 受信記録 ID, 取得時刻)`、操作者と取込・情報源の ID を持つ。DE-14 は内容の違い（`CONTENT_MISMATCH`）とキーのない隔離（`FormatDefect` の名前）の両方に出す。重複には出さない。直列化の契約（`DomainEventSerializationContractTest`）で形を固めた（human:kakimomokuri の決定 1） | `domain.events` の 3 つのイベント |
| 2 | **規則の変更**: 組み立てのクラスに `org.springframework.context.ApplicationEventPublisher` の 1 クラスだけを許した（パッケージ全体は許さない。human:kakimomokuri） | `ModularityTest` |
| 3 | **後の Bolt に送る重い論点**: (a) H2 の `event_publication.serialized_event` は VARCHAR(4000) で、寄港およそ 35 を超える DE-12 は入らない。DE-12 を最初に購読するのは 39b3b の監査なので、H2 では発行記録の INSERT で取込全体が巻き戻りうる（39b3b の開始準備）。(b) 監査記録の形と冪等の取り方（`event_id` の列を足すかを含む。39b3b の開始準備で諮る）。(c) 情報版は文字列で順が付かないので、非同期に届く DE-12 の新旧の比べ方を 39e の開始準備で選ぶ | 下の「既知の課題」 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39b3 回（W7 の 8 本目。開始準備で監査の listener と規則の汎用化を 39b3b に縦に分けた） |
| 期間 | 2026-11-03（計画の承認 `144a2edf5` から終了報告まで） |
| 対象 | 名前付きの例外（`DuplicateExternalRecordException`・`DuplicateAdoptedValueException`）、採用値 `AdoptedValue` と `AdoptedValueRepository`、寄港の上限 60、3 つのイベントと名前付きインターフェース `events`、`AdoptionFactory` |
| GitHub | [#18](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/18)（US-14。クローズは 39h） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 39b3 計画](bolt_39b3_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | DE-12〜14 の値の写しと出典、自動の採用も監査する前の決定の変更、寄港の上限 60（前の決定の変更）、用語集、ADR-004 の改訂の経緯 | `924d7890e` |
| 2. 外部原本のポートの約束 | 冪等性キーの重複を `DuplicateExternalRecordException` で伝える（元の例外を原因に持つ） | `7cf8423cd` |
| 3. 採用値 | 採用の受信記録ごとに採用値を追記（追記専用のポート、MyBatis・メモリ・契約）、値の要約、寄港の上限 60 と `TOO_MANY_PORT_CALLS` | `8b552116c` |
| 4. イベント | DE-12・DE-13・DE-14 の発行（1 行ごとに受信記録 → 採用値 → イベント）、`@NamedInterface("events")`、直列化の契約、許可リスト | `a3c1230a1` |
| 5. 統合と受入シナリオ | 採用値とイベントの公開の記録が取込と一緒にコミットされ、巻き戻れば残らない、重複・隔離には作らない（`@US-14-AC1`） | `93edb8d22` |
| 6. 品質ゲートと開発レビュー | 下の「品質ゲート」「開発レビューの指摘と扱い」 | `22b918b1f` |
| 7. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` の `test` 4041 件、失敗 0（Bolt 39b2b の 3992 件から +49）。`documentationTest` 5 件、失敗 0。1 回目の `check` は checkstyle の MissingSwitchDefault 1 件（ステップ 4 で入った分類の switch）で落ち、switch 式にして網羅をコンパイラーに確かめさせた |
| 変異 | V1〜V10 のすべてが落ちた。V5（採用値の追記を REQUIRES_NEW にする）は期待の巻き戻りではなく副作用（外部キーの違反）で落ちた（計画の表に注） |
| SonarQube | 各ステップで流し（T-234）、ステップ 4 の 1 件と直しの中の 1 件を直した。最後は Quality Gate が PASS（新しいコードのカバレッジ 95.7%、重複 0.025%、違反 0） |
| spotbugsMain | 各ステップで指摘なし（T-232） |
| `okf:check` | ERROR 0 |
| CI | `22b918b1f` で `check`・`ui`・`deploy-demo` が成功した（デモ環境に採用値とイベントの発行が入った） |

### 開発レビューの指摘と扱い

1 視点（アーキテクト・テスター・プログラマーを兼ねる）。高 1、中 2、低 2。

| # | 指摘 | 扱い |
| :--- | :--- | :--- |
| 1 | 高: H2 の `serialized_event` VARCHAR(4000) の件の送り先が 39e だが、DE-12 を最初に購読するのは 39b3b の監査 | 送り先を 39b3b の開始準備に直した（議題 3） |
| 2 | 中: 採用値の MyBatis の実装が Spring の `DuplicateKeyException` を漏らし、メモリの実装に一意性がない | 直した（`DuplicateAdoptedValueException`、メモリの一意性、契約の行 15、H2 の契約のテスト） |
| 3 | 中: `RecordValues` の型の分岐が 2 か所に写り、取込のサービスが大きい | 直した（package-private の `AdoptionFactory` に切り出し。39d の判断による採用でも使える） |
| — | 低: 契約の主要実績の採用値に航海の形の要約、例外の文言の重複 | 直した |
| — | 懸念: 情報版の新旧の比べ方、行 8 の統合テストが共有の DB に残す、DE-14 の理由の区分の名前の衝突 | 39e に送る、テストの Javadoc に書く、見張りのテストを足した |

## 計画から変えたこと

- 1 本の計画を 39b3（採用値とイベント）と 39b3b（監査の listener と規則の汎用化）に分けた（開始準備。半日を超えるため）
- 取込のサービスは名前付きの例外を捕まえずに上げ、取込全体を巻き戻す（計画のとおり。指示の誤りを担当が計画に合わせた）
- イベントの操作者の部品名を `operatorUserId`・`operatorCompanyId` にした
- 行 9 の同時の取込は、照会の spy で 2 回目に空を返して模した（追記は本物の一意制約に当たる）
- 採用値の H2 の契約のテストはステップ 6 で足した

## デモ項目

- 動画は撮らない（画面は 39c）
- マニュアルは変えない（見える振る舞いを変えない。イベントの受け手は 39b3b・39e・39g）

## 判断と学び

- イベントは公表された言語の契約なので、属性（出典・操作者）を設計の文書と突き合わせてから直列化の契約で固める。2 つの検証で「出典がない」を実装の前に見つけた
- ステップの判定に checkstyle を入れていなかったので、switch の default 節の欠けがステップ 6 まで残った。spotbugs と SonarQube だけでは checkstyle の規則は見えない
- 共有の DB に追記専用の行（採用値）を残すテストは消せないので、提供元で絞り、残ることを Javadoc に書く

### ふりかえり（KPT）

- Keep: 前の決定や ADR に当たる判断 4 つを、計画の初稿とステップの途中で人に諮った（規則に当たったら置き場所を動かさず止めた）。ステップごとの SonarQube（T-234）で、最後のステップに違反がまとまらなかった。サブエージェントは heredoc を 1 度も使わなかった（39b2 から 4 本続けて）
- Problem: ステップの判定に checkstyle がなかった。指示（取込のサービスで例外を捕まえる）が計画と食い違っていた（担当が計画に合わせた）
- Try: T-237: 本体のクラスを足したステップでは、承認の前に `checkstyleMain` も流す（T-232・T-234 と並べる）。T-238: サブエージェントへの指示は、計画の決定を言い換えず計画の行を指す

### 既知の課題（後に持ち込む）

- 39b3b: identity の監査の listener（DE-12・DE-13・DE-14。イベントから監査を書く最初の listener）、監査記録の形と冪等（`event_id` の列を足すか）を開始準備で諮る、H2 の `serialized_event` VARCHAR(4000) と寄港 60 の DE-12（入るかを確かめるテスト）、毒入りのメッセージ、配信の確認（T-184）、W6 横断レビュー M-2、`PublicApiArchitectureTest` の汎用化、手書きの規則の写し。39d の採用の判断で DE-12・DE-13 と判断のイベントの両方が出て監査が二重になりうる
- 39e: DE-12 の listener（経路設計の航海の書き手）、情報版の新旧の比べ方（選択肢つき）、寄港の上限 60 は経路設計にない片側だけの規則、出典の参照（受信記録 ID）を `source_ref` に
- 39g: DE-13 の listener（追跡の下書き）、出典の参照を `source_ref` に写して重ねない
- 39d・39h: 値を作り直すにはファイル全体の原本のヘッダーが要る（Bolt 39b2b の申し送り）
- 統合テストの行 8 は採用値・取込・発行記録を共有の PostgreSQL に残す（追記専用。提供元で絞る）

## 次の Bolt

Bolt 39b3b（identity の監査の listener と規則の汎用化）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-03 | 承認として記録した（議題 1・2 は人が個別に決めた内容、議題 3 は推奨のとおり。Recommend 承認の指示による。human:kakimomokuri） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-11-03 | 初版作成 | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 39b3 計画](bolt_39b3_plan.md)
- [Bolt 39b2b 終了報告](bolt_39b2b_report.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[データモデル](../../design/cargo-tracker/data_model.md)
- [ADR-004](../../adr/cargo-tracker/004-external-data-ingestion.md)
