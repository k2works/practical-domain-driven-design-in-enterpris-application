---
type: Plan
title: "Bolt 39a2 終了報告 - 外部原本の保存の DB の実装（原本の表、独立したトランザクション、層別の許可リスト）"
tags: [plan]
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-04T00:44:17Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-02T13:43:30Z }
---

# Bolt 39a2 終了報告 - 外部原本の保存の DB の実装（原本の表、独立したトランザクション、層別の許可リスト）

## 承認の議題（T-36。先に確かめてほしいこと）

外部原本の保存の DB の実装を、外側のトランザクションが巻き戻っても原本が残る形で作りました。ステップ 1〜3 は各ステップで承認を受けています。開発レビュー（3 視点）の直しは、どれも決定を変えないものでした。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **共通の `BYTEA`**: PostgreSQL と H2（`binary varying` になり、0 バイト・0x00・0xFF を読み書きできた）の両方で通ったので、ベンダー別の DDL と ADR-020 は作らなかった（ステップ 2 で承認済み）。H2 の `binary varying` には長さの上限があることを data_model に書いた | data_model の `stored_payload` の行、`V20261102100000__create_external_data.sql` |
| 2 | **独立したトランザクションの記録**: 再取込でもキーが重ならない（取込ごとに新しい UUID）、読み出しは外側の接続で READ COMMITTED の前提、39b の PERF-06 で行ごとのコミットの量と接続の数（外側 1・内側 1）を測る、S3 の接続の扱いは W10 で決める | ADR-004 の改訂の経緯（Bolt 39a2 の項）、`ExternalPayloadStorage`・`MyBatisExternalPayloadStorage` の Javadoc |
| 3 | **infrastructure の Spring の許可**: 開発レビューで、infrastructure に許す Spring を実際に使う `dao`・`transaction`・`stereotype` だけに絞った（規則 (b) の名前も変えた）。infrastructure 以外の (a) の `org.springframework..` は今のまま | `ModularityTest` |
| 4 | **ADR-004 :77 の指し先**: Bolt 39 の項の「39a2 の開始準備で決める」の末尾には、すでに「→ 39a2 で独立したトランザクションに決めた（下の Bolt 39a2 の項）」がある。リンクにはしていない（推奨: このまま） | ADR-004 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39a2 回（W7 の 3 本目。開始準備で 39a2 と 39a3 に縦に分けた 1 つ目） |
| 期間 | 2026-11-02（計画の承認 `bdd44c5fc` から終了報告まで） |
| 対象 | `external_data` のスキーマと原本の表、`MyBatisExternalPayloadStorage`、層別の許可リスト、スキーマの一覧の 4 か所、設計の文書 |
| GitHub | [#18](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/18)（US-14。クローズは 39h） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 39a2 計画](bolt_39a2_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | data_model（原本の表、原本は S3 と書いた箇所、追記専用の一覧）、ADR-004 の改訂の経緯、ポートの Javadoc、architecture_backend（層別の許可リスト、独立したトランザクション）、domain_model（`ReceivedRecord`、指し先を 39a3 に）、Bolt 39 の終了報告の指し先、release_plan（39a2・39a3 の行、更新履歴）、開発戦略 | `376e1ceb4` |
| 2. スキーマと原本の表 | common の `V20261102100000__create_external_data.sql`（`external_data` のスキーマと `stored_payload`。`BYTEA`、追記専用、日本語のコメント）、afterMigrate・SchemaSpy・2 つのテストの一覧。共通の `BYTEA` を PostgreSQL と H2 で確かめた | `21130e41d` |
| 3. 層別の許可リストと DB の実装 | `MyBatisExternalPayloadStorage`（`@Repository`。`REQUIRES_NEW` で INSERT、主キーの違反は `execute` の外で `IllegalStateException`、読めないキーは `NoSuchElementException`、防御の写し、Clock）、`StoredPayloadMapper` と XML、契約を継ぐ統合テストと行 2〜5、許可リストを層別の 2 本に | `52c257d60` |
| 4. 品質ゲートと開発レビュー | 下の「品質ゲート」「開発レビューの指摘と扱い」 | `0e8ad69a9`・`c30405491`・`548f0596d` |
| 5. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` の `test` 3634 件、失敗 0（Bolt 39 の 3619 件から +15） |
| 変異 | V1〜V5 のすべてが狙ったテストで落ちた（V1 は行 2 と行 4。行 4 の実際の落ち方は、一意制約の違反で PostgreSQL が外側のトランザクションを中断し外側の次の SQL が失敗すること。V2 は契約の上書きの拒否と行 4、V3 は規則 (a) だけ、V4 は追記専用の 2 件、V5 は行 5 だけ）。開発レビューで足したテストも、Clock なし・1 MB で切り詰め・例外を変えない・infrastructure から spring-jdbc の各変異で落ちた |
| SonarQube | Quality Gate は PASS（新しいコードのカバレッジ 96.0%、重複 0.028%、違反 0）。1 回目はテストのラムダの中の 2 つの呼び出し（MAJOR）、2 回目は catch の変数の名前（MINOR）で FAIL し、直して PASS した |
| `okf:check` | ERROR 0 |
| CI | `548f0596d` で `check`・`ui`・`deploy-demo` が成功した（デモ環境の PostgreSQL に `external_data` のスキーマと原本の表ができた）（2026-11-04 訂正: デモ環境は H2 のインメモリで、PostgreSQL ではない。マイグレーションは H2 で通った） |

### 開発レビューの指摘と扱い

3 視点（テスター（プログラマーを兼ねる）・アーキテクト・テクニカルライター。画面のない永続化の Bolt なのでユーザー代表は省いた）。高 2、中 7、低 7。

| # | 指摘 | 扱い |
| :--- | :--- | :--- |
| 1 | `stored_at` に Clock の時刻が入ることを確かめるテストがない（テスターの高） | 直した（単体テスト `MyBatisExternalPayloadStorageTest`。マッパーをスタブにして渡した時刻を捕まえる） |
| 2 | 外側が巻き戻った後の再取込で同じキーの主キーの違反になるかもしれない（アーキテクトの高。推測） | 起きない（キーは取込ごとに新しい UUID）。ADR-004 とポートの Javadoc に書いた |
| 3 | 行 2 に陽性の対照がない、5 MB の payload、同じキーへの同時の保存（テスターの中） | 直した（同時の保存は Hikari の使用中の接続の数が戻ることも確かめ、3 回流し直して揺らがなかった） |
| 4 | infrastructure に `org.springframework..` を丸ごと許している（アーキテクト・テスターの中） | 直した（`dao`・`transaction`・`stereotype` だけ） |
| 5 | 接続の数を 39b で測る項目に入れる（アーキテクトの中） | ADR-004 に足した |
| 6 | data_model の `receipt` に改名の注がない、アーキテクチャの決定の表のセルが詰まっている（テクニカルライターの中） | 直した |
| 7 | ADR-004 :77 の指し先（テクニカルライターの中） | 既に指し先があるので変えない（議題 4） |
| 8 | キーの長さ 500・501（テスターの中） | キーは `PayloadObjectKey` の形の検査で作り、長さは 500 を大きく下回る（最も長い `externaldata/ingestions/{UUID}` でも 60 文字）ので、501 の場合はポートの外の入力にならない。テストは足さない |
| — | 低: `rowsOf` の死んだ枝、`ClassFileImporter` の重複、用語「外部原本の保存」の区別、例外の文の書式、読み出しの前提の Javadoc | 直した |
| — | 低: 原本 1 件の大きさの上限を data_model に、数えるテストの規約（自分のキーで絞る） | 39b（PERF-06 と照合を作るとき）に送る |

## 計画から変えたこと

- 共通の `BYTEA` が通ったので、ベンダー別の DDL と ADR-020 は作らず、スキーマと表を 1 つの版にした（ステップ 2。承認済み）
- マイクロ秒の切り捨ては `ClockConfiguration` の `Clock.tick` に任せた（ステップ 3。承認済み）
- 変異の表の V1 の行 4 の落ち方と V4 の副次の落ち方を事実に直した（ステップ 3。承認済み）
- 開発レビューで、infrastructure の Spring の許可を絞り、規則 (b) の名前を変えた（議題 3）

## デモ項目

- 動画は撮らない（見える振る舞いを変えない）
- マニュアルは変えない

## 判断と学び

- 未検証の推測（H2 が `BYTEA` を受けるか）は、ADR の例外を書く前に先に確かめると、例外そのものが要らなくなることがある。不確かな所を Bolt の最初に通す縦の割り方が効いた
- 独立したトランザクションの前例（採番）を根拠に引くときは、前例が実際にその経路を通しているかを読む。採番の「16 本の同時でも止まらない」は REQUIRES_NEW を通さない対策の結果だった
- 統合テストのクラスの `@Transactional` は、外側のトランザクションを確かめるテストでは使えない（外側がテストのトランザクションに合流する）

### ふりかえり（KPT）

- Keep: ステップごとの承認ゲートで、型の確かめの結果（共通の `BYTEA`）と変異の落ち方の違いを、その場で人に見せて決めた。2 つの検証で、`@Transactional` の落とし穴と 1 次キャッシュの防御の写しを実装の前に見つけた
- Problem: 計画の変異の落ち方の見込み（`UnexpectedRollbackException`）が PostgreSQL の振る舞いと違った。開発レビューで、Clock と同時の保存のテストの抜けが見つかった
- Try: T-229: 永続化の Bolt の状態の組み合わせの表には、時刻の出どころ（Clock）と同時の書き込みの行を最初から置く

### 既知の課題（後に持ち込む）

- 39a3: data_source・ingestion・external_record・received_record・received_record_decision・adopted_value、`receipt` の改名、domain_model の要素表と用語集の分類 4 つ（「重複」の行を含む）
- 39b: 行ごとのコミットの量と接続の数（PERF-06）、足りなければ一括の保存、原本 1 件の大きさの上限、`stored_payload` を数えるテストは自分のキーで絞る規約、開発データの情報源と DATA_STEWARD の開発利用者
- W10: S3 の実装の接続の扱い（ADR-010 との違い）

## 次の Bolt

Bolt 39a3（残りの 6 表と改名）。表を作るので、ステップごとに承認ゲートで止める。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-04 | デモ環境のデータベースの誤り（PostgreSQL → H2 のインメモリ）に訂正の注を足した（Bolt 39b3b の整合性検証で見つけた） | anthropic/claude-opus-5-5 |
| 2026-11-02 | human:kakimomokuri が承認した（議題 1〜4 を報告のとおり） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-11-02 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 39a2 計画](bolt_39a2_plan.md)
- [Bolt 39 終了報告](bolt_39_report.md)
- [ADR-004](../../adr/cargo-tracker/004-external-data-ingestion.md)
- [データモデル](../../design/cargo-tracker/data_model.md)
