---
type: Plan
title: "Bolt 39d2b 計画 - S-17 で 1 つの取込の形式の誤りをまとめて却下する（件数が減ったときの示し方・ページ送り）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-04T22:38:27Z }
---

# Bolt 39d2b 計画 - S-17 で 1 つの取込の形式の誤りをまとめて却下する（件数が減ったときの示し方・ページ送り）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39d2b 回（W7 の 17 本目。release_plan :965 の 39d2b の行。Bolt 39d2 の開始準備で 39d2 から縦に分けた） |
| 状態 | 初稿（承認待ち。2 つの整合性検証はこの後） |
| 予定 | W7（2026-11-16 の週。前倒しで着手できる）。作業は約 3 時間 45 分（上限いっぱい（1 万件）の計測を 39d4 に送った後。送らなければ約 4 時間 05 分。下の「時間の配分と打ち切り」。`check`・`uiTest`・`manualScreenshots`・`demoVideo`・SonarQube・CI とステップごとの承認の待ちを除く） |
| アプローチ | アウトサイドイン（S-17 のまとめての却下から。画面の層の受入シナリオ（`@ui`）の Red から入る。release_plan :965 の 39d2b の行のとおり。1 件ずつの却下のサービス・判断のポート・DE-29・監査は 39d2 で固まっていて（ReceivedRecordDecisionService.java :69-102、MyBatisReceivedRecordDecisionRepository.java :46-60、ExternalRecordAuditEventHandler の `on(ReceivedRecordDecided)`）、まとめての却下の画面と振る舞いが受入条件の中心） |
| 対象 | (1) 1 つの取込の判断を待つキーのない隔離のまとめての却下（【業務の判断】(g)。台帳 D-96）: 取込で絞った一覧の「この取込の形式の誤りをまとめて却下する（N 件）」、まとめての却下の画面 `GET /staff/external-data/undecided-records/keyless-rejection?ingestion={取込 ID}` と `POST` 同じ URL（PRG で取込で絞った一覧に戻る。CSRF。理由は 1 つで必須、1000 文字まで）、受信記録ごとに判断・DE-29・監査を 1 つずつ（今ある 1 件の判断の型とイベントと listener を使う。DE-29 の形は変えない）、(2) 件数と 1 ページの照会の間に件数が減ったときの示し方（39d の申し送り。39d2 の開始準備の決定 (8) の形）、(3) 39d の行 7 を契約のテストに上げる（39d2 の行 16）、(4) S-17 のページ送りが 100 個の「判断する」の後にあるキーボードの負担（39d2 の開発レビューの低）、(5) マニュアル 12.2 に「形式の誤りをまとめて却下する」とキャプチャ、(6) デモの動画（`@demo-bolt-39d2b/reject-keyless`）。上限いっぱい（1 万件）のまとめての却下の時間の計測は 39d4 に送る（下の開始準備の決定 (1)。【前の決定の変更】(1)） |
| トレース | US-14 AC4（隔離しデータ責任者へ示す）、UC-15 の主成功シナリオ 5（データ責任者が差異と採否を確認する）・3b、ED-INV-02、AUD-01・IA-INV-15（domain_model :1452。（操作、受信記録）ごとに 1 つ）、ui_design の S-17（:142・:193-195・:1932 の節）、domain_model の受信記録の判断（:1522）、台帳 D-96（release_plan :1073）、ACC-01（WCAG 2.2 AA） |
| GitHub | [#18](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/18)（US-14。クローズは 39h） |
| SP | 0 |
| 承認ゲート | **ステップごとに止める**（release_plan :965 の「ステップごと（画面）」）。画面（まとめての却下の画面と POST、一覧のリンクとページ送りの位置）、新しいファイル（下の一覧）。スキーマは変えない（判断の表と受信記録の表をそのまま使う）。監査は新しい操作を足さず、書き手も規則の行も変えない（DE-29 と `EXTERNAL_RECORD_DECIDED` をそのまま使う）。認可は変えない（決定 (4)）。注: 人の指示「Recommend 承認で進めて」により、各ステップの終わりで止めて推奨の案を承認として記録する（人が個別に見ていないことが分かるように「推奨のとおり（Recommend 承認の指示による）」と書く）。ステップを飛ばしたり束ねたりはしない |
| 開始準備で決めたこと | 下の (1)〜(10)（初稿。推奨を 1 つに決めて書いた。承認待ち）。【業務の判断】(g) を実装し、台帳に D-96 として足した（release_plan :1073）。【業務の判断】(h) を新しく記録する |

### 開始準備の決定

(1) **範囲と見積り**: release_plan :965 の 39d2b の行（まとめての却下、理由は 1 つ、受信記録ごとに判断・DE-29・監査、上限いっぱい（1 万件）の時間の計測、件数が減ったときの示し方、マニュアルとキャプチャ、デモの動画、39d の行 7 を契約のテストに、ページ送りのキーボードの負担）を引き直すと約 4 時間 05 分で、目安（2〜4 時間）を 5 分超える。39d2 の開始準備の決定 (1) の見積り（約 2 時間 45 分。計画の承認で行 7 の 20 分を足して約 3 時間 05 分）より 1 時間増えるのは、(a) 一覧のボタンから直に却下せず、件数と取込を示すまとめての却下の画面を挟む（決定 (3)。【前の決定の変更】(3)。+20 分）、(b) ページ送りの直し（決定 (7)。39d2 の開発レビューで足した。+10 分）、(c) 状態の組み合わせの表の行が 22 あり、Red・品質ゲート・受入シナリオの例が 39d2 と同じ水準になる（+40 分）ため。行 7 は 39d2 の契約の行 5 がほぼ同じことを確かめているので 20 分から 10 分に減る（決定 (6)。−10 分）。

送る候補は 3 つを比べ、**上限いっぱい（1 万件）のまとめての却下の時間の計測を 39d4 に送る**（【前の決定の変更】(1)。約 20 分。39d2b は約 3 時間 45 分）を推奨する。39d4 には 39d の行 19（判断を待つ隔離 1 万件の取込の一覧の照会の計測。non_functional :57）がすでにあり、同じ `ExternalDataIngestionPerformanceTest` の同じ準備（キーのない隔離 1 万件の取込）の続きで測れるので、準備を 1 回で済ませられる。まとめての却下の形（1 つのトランザクションで受信記録ごとに判断を追記し DE-29 を出す）は、取込が 1 つのトランザクションで 1 万件の受信記録を書き DE-14 を出す形（IngestionService.java :258-271）と同じで、取込は Linux の CI で 1 万件が 17.0〜24.3 秒（[Bolt 39b4 終了報告](bolt_39b4_report.md) :50。上限 300 秒）なので、39d4 まで待つ危うさは小さい。39d4 で 5 分を超えたら、そのステップで止めて分け方（取込の中を区切って複数のトランザクションにする）を諮る。比べた案: (i) 39d2 の前例（5 分の超過を見積りの誤差の範囲として受け入れる）に倣い送らない（目安を超える。人の指示「目安の 2〜4 時間に収める」に合わない）、(ii) 行 7 を契約のテストに上げる分（10 分）を再び送る（39d から 2 回送っている。テストの付け替えを寝かせ続ける）、(iii) ページ送りの直し（10 分）を 39h（差異の行を同じ一覧に足す）に送る（2 つを合わせても 20 分で、目安を超えない分に足りない。キーボードの負担は今のデモ環境の 1 万件の取込で起きている）。

順序は 39d2 → 39d2b → 39d3 → 39d4（release_plan :975 のまま）。39d2b は 39d2 の判断のサービスとイベントと listener を使い、39d3 の採用と縦が違う。39d4 の見積りは計測の約 20 分が増え、39d4 の開始準備で引き直す。

(2) **対象は 1 つの取込の、判断を待つキーのない隔離だけ**（【業務の判断】(g)。台帳 D-96）: 「取込で書いた受信記録で、取込 ID がその取込、分類が隔離、外部原本を持たない（`external_record_id` が NULL）、判断がない」受信記録。形式の誤り・ファイルの誤り・取り込めなかった部分のどれも外部原本を持たない（ReceivedRecord.java :52-57。外部原本を持たないのは隔離だけ）。内容の違う原本（外部原本を持つ隔離）・採用・重複・ほかの取込の受信記録・判断済みは対象に入れない。判断できる受信記録の定め（39d2 の開始準備の決定 (3)。ReceivedRecordDecision.java :48-50 の `decidable`）の中の部分集合で、1 件ずつの判断の規則は変えない。読むのは受信記録のポートに足す 2 つ（`externaldata.domain.model.aggregates.ReceivedRecordRepository`）:

- `undecidedKeylessQuarantinesOf(UUID ingestionId)`: 上の受信記録を行の範囲の始まりの順（範囲のないファイルの誤りを先、同じなら受信記録 ID の順。今の `quarantinedOf` の並び。ReceivedRecordMapper.xml :40-47）に、件数の上限なしで `List<ReceivedRecord>` で返す（1 つの取込のキーのない隔離はデータ行の上限 10,000 件（IngestionFileLimit.java :13）と取り込めなかった部分・ファイルの誤りの数件で、1 万と少しに収まる）
- `countUndecidedKeylessQuarantines(UUID ingestionId)`: 同じ定めの件数

SQL は判断を待つ隔離の断片（ReceivedRecordMapper.xml :66-75 の `undecidedQuarantineSource`）に `AND r.external_record_id IS NULL` を足し、取込 ID を必須にする（断片の `<if>` は通る）。メモリの実装（`InMemoryReceivedRecordRepository`）も同じ定めで、判断のリポジトリを見る。新しい索引は足さない（`ix_received_record_ingestion` で取込の受信記録に絞れる。39d の一覧と同じ）。

(3) **画面: 一覧のリンクとまとめての却下の画面**（【前の決定の変更】(3)）: 取込で絞った一覧（`?ingestion=…`）で、その取込に判断を待つキーのない隔離が 1 件以上あるときだけ、件数の文の後に「この取込の形式の誤りをまとめて却下する（N 件）」（N は 3 桁の区切り）のリンクを出す。絞らない一覧・取込が見つからない一覧・キーのない隔離が 0 件の取込（内容の違う原本だけ）には出さない（押しても行き先のない操作を出さない）。リンクの先は **まとめての却下の画面** `GET /staff/external-data/undecided-records/keyless-rejection?ingestion={取込 ID}`（見出し「形式の誤りのまとめての却下」）で、取り込んだ日時・情報源・「形式の誤り N 件」・「内容の違う原本は含みません。一覧で 1 件ずつ判断してください。」・「形式の誤りは採用できません。直したファイルを取り込み直してください。」（判断の画面と同じ文。decision.html :99）・却下の理由の欄（判断の画面と同じ `textarea`。必須、1000 文字まで、`aria-describedby`。decision.html :104-107）・「却下すると取り消せません。誤って却下したときは、提供元に同じ原本を送り直してもらってください。」（判断の画面と同じ文。decision.html :109。キーのない隔離は冪等性キーを持たず重複に分類されないので、同じファイルを取り込み直すと再び判断を待つ隔離になる。IngestionService.java :258-271 はキーのない行を外部原本の照合なしに隔離する）・「まとめて却下する」・「一覧に戻る」を示す。「まとめて却下する」は `POST /staff/external-data/undecided-records/keyless-rejection?ingestion={取込 ID}` で、却下したら PRG で取込で絞った一覧の 1 ページ目に戻り「形式の誤り N 件を却下しました。」（N は実際に却下した件数、3 桁の区切り）を示す。

- GET の時点で 0 件（ほかの人がまとめて却下した、全部を 1 件ずつ却下した）と、POST の時点で 0 件（二重送信を含む）は、判断を作らず取込で絞った一覧に戻って「この取込にまとめて却下できる形式の誤りはありません。」を示す
- 取込の指定がない・UUID でない・ない取込は 404（判断の画面の前例。一覧の「指定された取込が見つかりません。」は絞らない一覧を示せるから文にしているが、まとめての却下の画面は取込がないと示すものがない）
- 理由が空・空白だけ・1000 文字（コードポイント）を超えるときは、まとめての却下の画面に戻って判断の画面と同じ欄の誤り（UndecidedRecordController.java :69-71 の 2 つの文）とエラー要約を示す
- `keyless-rejection` は判断の画面のパス `/{receivedRecordId}`（UndecidedRecordController.java :132）と重なるが、Spring MVC はパスの変数より文字の一致を先に選ぶので、判断の画面に入らない（controller のテストの行 10 で確かめる）
- 項目は `UndecidedRecordController` に足し（GET と POST。同じ URL の下）、新しい controller を作らない。フォームは判断の画面の `RejectionForm`（RejectionForm.java。理由だけを持つ）をそのまま使う

代わりの案は取らない: (i) 一覧のボタンから直に POST する（理由の欄を一覧に置くことになり、件数と情報源を確かめずに 1 万件を取り消せずに却下しうる。39d2 で一覧の各行に理由の欄を置かなかった理由と同じ）、(ii) まとめての却下の画面に対象の行を並べる（1 万行になる。対象の行は一覧で読める）。

(4) **却下のサービス・CSRF・認可（今の規則の中）**: `ReceivedRecordDecisionService` に `rejectKeyless(AuthenticatedActor, RejectKeylessQuarantinesCommand)` を足す（新しいサービスを作らない。組み立ては ExternalDataConfiguration の今の `@Bean` のまま）。1 つのトランザクションで、役割の確かめ（データ責任者でなければ `NotPermitted`。前例 ReceivedRecordDecisionService.java :71-73）、取込を読む（`IngestionRepository.findById`。なければ `NotFound`。情報源 ID もここから得る）、対象を読む（`undecidedKeylessQuarantinesOf`。0 件なら `NothingToReject`）、判断の時刻を Clock から 1 回取り、受信記録ごとに `ReceivedRecordDecision.reject`（ReceivedRecordDecision.java :57-71。理由の規則と隔離だけの規則はそのまま）で判断を作って追記し DE-29 を出す。読んだ後に 1 件ずつ却下された受信記録の一意制約の違反（`DuplicateReceivedRecordDecisionException`。判断のリポジトリがセーブポイントに戻す。MyBatisReceivedRecordDecisionRepository.java :46-60）は、とばして数えない（1 件ずつの却下の `AlreadyDecided` と同じ扱い。ReceivedRecordDecisionService.java :85-89）。ほかの例外は握りつぶさず上げ、トランザクションごと巻き戻す（判断も DE-29 も残らない）。全部がとばされたら `NothingToReject`。結果は sealed の `KeylessRejectionOutcome`（`RejectedAll(int count)`・`NotPermitted`・`NotFound`・`NothingToReject`。前例 DecisionOutcome。入れ子の `Rejected` は `DecisionOutcome.Rejected`（DecisionOutcome.java :17）と同じ単純名になるので `RejectedAll` にする。`NotPermitted`・`NotFound` を外側の型ごとに持つのは前例どおり（IngestionOutcome.java :110、DecisionOutcome.java :25・:28））。コマンド `RejectKeylessQuarantinesCommand`（取込 ID と理由。`application.internal.commands`）に `@Command` を付ける（DddAnnotationArchitectureTest の規則）。CSRF は Spring Security の既定の同期トークン（SecurityConfiguration.java :73）で、フォームは `th:action` が隠し項目を足す。認可は `/staff/external-data/**` のデータ責任者（SecurityConfiguration.java :224-225）の中で、サービスでも役割を確かめる。どちらも今の規則の中なので、新しいセキュリティの判断はない

(5) **イベントと監査は変えない**: 却下した受信記録ごとに今の DE-29 `ReceivedRecordDecided`（ReceivedRecordDecisionService.java :92-100 と同じ値。判断は `REJECTED`、理由は全件で同じ文、判断の時刻は全件で同じ）を 1 つ出し、identity の `ExternalRecordAuditEventHandler` が受信記録ごとに `EXTERNAL_RECORD_DECIDED` を 1 つ書く（IA-INV-15 の（操作、受信記録）ごとに 1 つ。domain_model :1452 にすでに「まとめての却下（39d2b）も受信記録ごとに判断・DE-29・監査を 1 つずつ」と書いてある）。まとめての却下を示す印は DE-29 にも監査記録にも足さない（DE-29 の v1 の直列化の契約を変えない。まとめたかどうかは同じ判断の時刻・判断者・理由で読める。監査を読む画面は AUD-UI-01（W8））。listener と規則の行（LayerArchitectureTest の「書き手は 1 か所」）は変えない。配信は `ExternalRecordAuditDeliveryIntegrationTest` に 1 本足す（T-184。まとめて 3 件を却下して監査記録が 3 つ）。1 万件の DE-29 の発行記録の大きさは取込の 1 万件の DE-14 と同じ量で、H2 の発行記録の列は Bolt 39b3b で広げてある（db/migration/h2/V20261104103700）

(6) **行 7 を契約のテストに上げる**（39d の申し送り。39d2 の開始準備の決定 (7)・行 16）: 39d2 の契約のテストの行 5（ExternalDataRecordRepositoriesContract.java :641-663。判断のポートで判断を追記すると、取込で絞った一覧と件数から消える。メモリ・H2・PostgreSQL）が、39d の行 7 の取込で絞った分をすでに確かめている。残る差は **絞らない一覧と件数からも消えること**（39d の行 7 の本体の最後の 2 つの確かめ。MyBatisExternalDataRecordRepositoriesIntegrationTest.java :206-209 の、絞らない件数が判断を待つ 1 件だけ増えることと、絞らない一覧の先頭が判断していない隔離であること）だけなので、契約のテストに `Bolt39d2b_行17_判断のポートで判断を追記した隔離は絞らない一覧と件数からも消える` を足し、JDBC で判断の行を入れていた 39d の行 7（MyBatisExternalDataRecordRepositoriesIntegrationTest.java :152-163 と本体 :165-210、その本体だけが使う `keylessQuarantine`（:213 以降）、H2ExternalDataRecordRepositoriesTest.java :210-218）を消す。MyBatis の 1 次キャッシュを避けるために件数を先に数えていた書き方（MyBatisExternalDataRecordRepositoriesIntegrationTest.java :183-185 の注）は、判断のポートの追記が同じ session の更新としてキャッシュを捨てるので要らなくなる見込みで、移すときに「追記の前と後に絞らない件数を数え、差が 1」で確かめる（キャッシュが残って落ちるなら、理由を書いて今の順に戻す）

(7) **ページ送りを表の前に移す**（39d2 の開発レビューの低。【前の決定の変更】(2)）: 今のページ送り（index.html :50-55 の `nav`）は表（:21-49）の後にあり、1 ページ 100 個の「判断する」を Tab で越えないと届かない（39d2 で UI のステップを Shift+Tab で画面の終わりから戻る書き方に直した。UndecidedRecordUiSteps.java :144-160）。**ページ送りの `nav` を件数の文とまとめての却下のリンクの後、表の前に移す**（1 つのまま。読み上げの名前「判断を待つ隔離のページ」も変えない）を推奨する。件数の文「N 件（M〜L 件目を示しています）」の直後に前後のページがあると、どこを見ているかと移り方が並んで読める。比べた案: (i) 表の前と後の両方に置く（同じ名前の `nav` が 2 つになり、axe の landmark-unique に当たる。名前を分けると読み上げの一覧に似たものが 2 つ並ぶ）、(ii) 表の前に「ページ送りへ移る」のリンクを置く（リンクが 1 つ増えるだけで、移った先はやはり表の後）、(iii) 1 ページの件数を減らす（39d の開始準備の決定 (3) の 100 件（S-16 の 100 件にそろえた）を変え、1 万件の取込が 100 ページより増える）。UI のステップは Tab で先頭から進める書き方に戻し、Shift+Tab の上限の定数（:28-29）を消す。ui_design の画面イメージ（:1951）とマニュアル 12.2 の画面の説明（12-外部データ.md :214 の ⑤、:222）とキャプチャ `12-undecided-records.png` を直す

(8) **件数と 1 ページの照会の間に件数が減ったとき**（39d の申し送り。39d2 の開始準備の決定 (8) の形のまま）: 照会のサービス（UndecidedRecordQueryService.java :48-71）は件数を数えてから（:53）ページを読む（:59-60）。読んだページが空で件数が 0 より大きいとき（最後のページの隔離がその間にすべて判断された。まとめての却下で起きやすくなる）は、件数を数え直して最後のページに寄せ、1 回だけ読み直す。数え直して 0 件、または読み直しても空なら 0 件の文を示す（ページの記録の件数を 0 にする）。示す範囲（M〜L 件目）は読んだ件数から作る（UndecidedRecordPage.java :28-35。今のまま）。まとめての却下のリンクの件数（決定 (3)）はページと別に数え、ずれても却下はその時点の対象を読み直すので害がない（決定 (4)）。代わりの案は 39d2 の決定 (8) で退けたまま（REPEATABLE READ、「一覧が変わりました」の文）

(9) **却下の理由と取り消し**（【業務の判断】(d)・(e)。39d2 の開始準備の決定 (10)・(11)）: 1 件ずつの却下と同じ（必須、前後の空白を除いて 1〜1000 文字（コードポイント）、取り消さない）。まとめての却下では 1 つの理由を全件に付ける（【業務の判断】(g)）。誤ってまとめて却下したときは、同じファイルを取り込み直せば形式の誤りが再び判断を待つ隔離になる（決定 (3) のコードの事実）。マニュアルにはこの Bolt でも判断の画面と同じ文（提供元に同じ原本を送り直してもらう）を書き、「同じファイルを取り込み直すと再び判断を待つ隔離になる」の受入シナリオは、送り直しの扱いを確かめる 39d4 に送る（39d2 の終了報告の議題 2 と同じ Bolt で確かめる）

(10) **新しい型の名前**（T-246。`apps/cargo-tracker/src` で `Bulk`・`KeylessRejection`・`RejectKeyless`・`rejectKeyless`・`undecidedKeylessQuarantinesOf`・`countUndecidedKeylessQuarantines`・`RejectedAll` を検索して、ないことを確かめた。`KeylessQuarantine` はテストの private のメソッド名（`assertKeylessQuarantine`・`onlyKeylessQuarantine`）と取込のサービスの private の `addKeylessQuarantine` だけで、型にはない）: 本体 `RejectKeylessQuarantinesCommand`（`application.internal.commands`）、`KeylessRejectionOutcome`（`application.internal.commandservices`。入れ子に `RejectedAll`・`NotPermitted`・`NotFound`・`NothingToReject`）、`KeylessRejectionTarget`（`application.internal.queryservices`。まとめての却下の画面の中身: 取込 ID・取り込んだ日時・提供元・原本種別・件数。前例 UndecidedRecordPage）、テンプレート `templates/externaldata/undecided-records/keyless-rejection.html`。フォームは `RejectionForm` を使い、新しいフォームを作らない

【前の決定の変更】(1): release_plan :965 の 39d2b の行の「上限いっぱい（1 万件）の時間の計測」を 39d4 に送る（39d4 の行に「まとめての却下の上限いっぱい（1 万件）の時間の計測」を足す。39d の行 19 の一覧の計測と同じ準備で測る。non_functional の PERF-06 の行（:62）に、まとめての却下の時間をログの「PERF-06 keyless-rejection」の行に出して参考の値として記録し、5 分を超えたら止めて分け方を諮ることを書く。ステップ 1）（決定 (1)）。

【前の決定の変更】(2): Bolt 39d の開始準備の決定 (3) の S-17 の一覧の画面の、ページ送りを表の後に置く配置（ui_design :1951 の画面イメージ）を、表の前（件数の文とまとめての却下のリンクの後）に移す。39d2 で Shift+Tab に直した UI のステップを Tab に戻す（決定 (7)）。

【前の決定の変更】(3): release_plan :965 と ui_design :193 の「一覧を取込で絞ったときの『この取込の形式の誤りをまとめて却下する』」を、一覧のリンクからまとめての却下の画面を挟む形にする（一覧 → まとめての却下の画面 → 一覧。39d2 の【前の決定の変更】(2) の判断の画面と同じ考え方）（決定 (3)）。

新しいファイル（39d2b）: 本体は決定 (10) の 3 個（.java）とテンプレート `keyless-rejection.html`。テスト: 受入シナリオ `features/ui/reject_keyless_quarantines_ui.feature` と Steps `RejectKeylessQuarantinesUiSteps.java`（新しいテストのクラスはこれだけ。ほかは既存のテストのクラスに `@Nested class Bolt39d2b_…` で足す）。キャプチャ `docs/manual/assets/12-undecided-records-keyless-rejection.png`（`manualScreenshots` が作る）、デモの動画 `docs/assets/demo/bolt-39d2b/reject-keyless.webm`（`demoVideo` が作る）。直すもの: `ReceivedRecordRepository`・`MyBatisReceivedRecordRepository`・`ReceivedRecordMapper`（.java・.xml）・`InMemoryReceivedRecordRepository`（決定 (2)）、`ReceivedRecordDecisionService`（決定 (4)）、`UndecidedRecordQueryService`（まとめての却下の画面の中身、一覧のキーのない隔離の件数、件数が減ったときの読み直し）・`UndecidedRecordPage`（キーのない隔離の件数）、`UndecidedRecordController` とテンプレート `index.html`（リンク・知らせ・ページ送りの位置）、テスト `ExternalDataRecordRepositoriesContract`・`H2ExternalDataRecordRepositoriesTest`・`MyBatisExternalDataRecordRepositoriesIntegrationTest`（行 7 を移す）、`ReceivedRecordDecisionServiceTest`・`UndecidedRecordQueryServiceTest`・`UndecidedRecordControllerTest`・`ExternalDataIngestionSecurityIntegrationTest`・`ExternalRecordAuditDeliveryIntegrationTest`、`UndecidedRecordUiSteps`（Tab に戻す）、`manual_screenshots.feature`、マニュアル 12 章・付録 B（「この取込にまとめて却下できる形式の誤りはありません。」の 1 行）。マニュアルは新しいファイルを作らない（12.2 に節を足す）。

## Bolt ゴール

データ責任者が S-17 の一覧を取込で絞ると、その取込に形式の誤りがあれば「この取込の形式の誤りをまとめて却下する（N 件）」が出る。開くと、まとめての却下の画面で取込・情報源・件数を確かめ、理由を 1 つ書いて「まとめて却下する」と、その取込の判断を待つ形式の誤りがすべて、同じ判断者・時刻・理由で 1 件ずつ却下として記録され、取込で絞った一覧に戻って「形式の誤り N 件を却下しました。」が出る。内容の違う原本は一覧に残り、1 件ずつ判断する。却下した受信記録ごとに DE-29 が出て、監査記録 `EXTERNAL_RECORD_DECIDED` が 1 つずつ残る。理由が空・1000 文字を超えると欄の誤りを示す。ほかの人が先に却下していれば、残っている分だけを却下し、残っていなければ「この取込にまとめて却下できる形式の誤りはありません。」を示す。最後のページの隔離が読み込みの間に判断されても「0〜0 件目」を出さず、最後のページか 0 件の文を示す。ページ送りは表の前にあり、キーボードで 100 個の「判断する」を越えずに届く。マニュアル 12.2 にまとめての却下の手順があり、キャプチャとデモの動画がある。上限いっぱい（1 万件）の時間は 39d4 で測る。

### 開始準備で確かめたこと

- **release_plan の 39d2b の行**（:965）: 「1 つの取込のキーのない隔離をまとめて却下する縦（【業務の判断】(g)）: 一覧を取込で絞ったときの『この取込の形式の誤りをまとめて却下する』、理由は 1 つ、受信記録ごとに判断・DE-29・監査を 1 つずつ、上限いっぱい（1 万件）の時間の計測、件数と 1 ページの照会の間に件数が減ったときの示し方、マニュアルとキャプチャ、デモの動画、39d の行 7 を契約のテストに上げる、S-17 のページ送りが 100 個の『判断する』の後にあるキーボードの負担」、アプローチはアウトサイドイン（S-17 のまとめての却下から）、承認ゲートは「ステップごと（画面）」
- **前の Bolt の申し送り**: Bolt 39d2（[計画](bolt_39d2_plan.md) の開始準備の決定 (1)・(7)・(8)・(9) と行 15・16、[終了報告](bolt_39d2_report.md) :19・:20・:71・:77・:98-99・:103）、Bolt 39d（[終了報告](bolt_39d_report.md) の開発レビューの行 7 の件数を先に数える書き方と件数が減ったときの「N 件（0〜0 件目）」）
- **1 件ずつの却下の今の形**: サービスは役割 → `findById` → 隔離か → `existsFor` → 追記（一意制約の違反は `AlreadyDecided`）→ 取込を読んで DE-29（ReceivedRecordDecisionService.java :69-102）。判断のリポジトリの追記はセーブポイント（`NESTED`）の中（MyBatisReceivedRecordDecisionRepository.java :46-60）。判断の不変条件は理由の必須と 1000 文字（ReceivedRecordDecision.java :31・:57-71）。判断の画面の行き先は `refusalOf`（ReceivedRecordDecisionService.java :55-61）
- **キーのない隔離**: 外部原本 ID と取得時刻が NULL の受信記録は隔離だけ（ReceivedRecord.java :52-57）。照会では外部原本を持たない隔離を「形式の誤り」と示す（UndecidedQuarantine.java :44-46、UndecidedRecordController.java :56・:234）。取込はキーのない行を外部原本の照合なしに隔離し DE-14 を出す（IngestionService.java :258-271）ので、同じファイルを取り込み直すと重複にならず再び隔離になる
- **一覧の今の形**: 判断を待つ隔離の断片（ReceivedRecordMapper.xml :66-75）、件数（:87-90）、ページ（:79-85）。照会のサービスは件数を数えてからページを読む（UndecidedRecordQueryService.java :53・:59-60）。ページ送りは表の後（index.html :50-55）。UI のステップは Shift+Tab で画面の終わりから戻る（UndecidedRecordUiSteps.java :144-160）
- **行 7**: JDBC で判断の行を入れる 39d の行 7（MyBatisExternalDataRecordRepositoriesIntegrationTest.java :152-210、H2ExternalDataRecordRepositoriesTest.java :210-218）。39d2 の契約の行 5（ExternalDataRecordRepositoriesContract.java :641-663）が判断のポートで取込で絞った一覧と件数から消えることを確かめている
- **認可と CSRF**: `/staff/external-data/**` はデータ責任者だけ（SecurityConfiguration.java :224-225）、CSRF は既定の同期トークン（同 :73）
- **計測**: PERF-06 は 1 万件のファイルを 5 分以内（non_functional :62）。取込の 1 万件は Linux の CI で 17.0〜24.3 秒（[Bolt 39b4 終了報告](bolt_39b4_report.md) :50）。判断を待つ隔離の一覧の上限いっぱいの計測は 39d4（non_functional :57）
- **マニュアル**: 12.2 の「隔離を却下する」（docs/manual/12-外部データ.md :253-296）と「注意・補足」（:297）。一覧の画面の説明の ⑤ がページ送り（:214・:222）
- **台帳**: D-95（判断の取り消しを作らない。release_plan :1072）の後に D-96（【業務の判断】(g)）を仮の決定として足した（:1073）

## 設計の決定

| 項目 | 決定 | 根拠 |
| :--- | :--- | :--- |
| 範囲と分け方 | 開始準備の決定 (1)。上限いっぱい（1 万件）の計測を 39d4 に送り約 3 時間 45 分（【前の決定の変更】(1)） | 下の「時間の配分と打ち切り」、[Bolt 39b4 終了報告](bolt_39b4_report.md) :50 |
| 対象 | 決定 (2)。1 つの取込の判断を待つキーのない隔離だけ。受信記録のポートに 2 つの照会。【業務の判断】(g)（D-96） | ReceivedRecord.java :52-57、ReceivedRecordMapper.xml :66-75 |
| 画面 | 決定 (3)。取込で絞った一覧のリンク → まとめての却下の画面 → PRG で一覧（【前の決定の変更】(3)）。0 件は知らせ、取込がないと 404 | UndecidedRecordController.java :132・:69-71、decision.html :99・:104-109 |
| 却下のサービス | 決定 (4)。今のサービスに `rejectKeyless`。1 つのトランザクション、判断の時刻は 1 回、一意制約の違反はとばす、ほかの例外は巻き戻す。sealed の結果。コマンドに `@Command` | ReceivedRecordDecisionService.java :69-102、MyBatisReceivedRecordDecisionRepository.java :46-60 |
| イベントと監査 | 決定 (5)。DE-29 と `EXTERNAL_RECORD_DECIDED` を受信記録ごとに。形と書き手と規則の行は変えない | domain_model :1452 |
| 行 7 | 決定 (6)。契約に絞らない件数の行を足し、JDBC の行 7 を消す | ExternalDataRecordRepositoriesContract.java :641-663 |
| ページ送り | 決定 (7)。表の前に移す（1 つのまま）（【前の決定の変更】(2)） | index.html :50-55、UndecidedRecordUiSteps.java :144-160 |
| 件数が減ったとき | 決定 (8)。空のページなら数え直して 1 回読み直す | UndecidedRecordQueryService.java :48-71、UndecidedRecordPage.java :28-35 |
| 理由と取り消し | 決定 (9)。1 件ずつと同じ。【業務の判断】(d)・(e) | release_plan :1072（D-95） |
| 型の名前 | 決定 (10)（T-246 で検索した） | — |

## ふりかえりと前の Bolt からの引き継ぎ

| 出典 | 内容 | この Bolt での扱い |
| :--- | :--- | :--- |
| W6 の Try T-204・T-212、Bolt 38h の T-227、Bolt 39b の T-233、Bolt 39d・39d2 の Problem | サブエージェントへの指示の最初に heredoc と標準入力を待つコマンド（`python -`、引数のない `cat >` など）を使わないと書く | 任せるときの指示の最初に書く（39c4 から 4 本続けて流れた。[39d2 終了報告](bolt_39d2_report.md) :98）。私が計画を直すときもファイルは Write・Edit で書く |
| Bolt 39d の T-247、Bolt 39d2 の Try | 決まりを文章の指示ではなく仕組みで守らせる（heredoc と `python -` を拒むフック。触らないファイルもフックで守る案を含める） | W7 の締めで最初に諮る（39d2 の終了報告のとおり）。39d2b では任せる範囲を、ファイルを Write・Edit で書く作業と読むだけの検索に絞り、Gradle と git を人の側の 1 か所に残す。任せる指示に触らないもの（`.claude/agent-memory`・`.run/`・`apps/cargo-tracker/.claude/`、ほかの作業のテスト）を名前で書く（39d2 でマニュアルの担当が `.claude/agent-memory` に書いた） |
| Bolt 39d の T-248 | `git add` は `git status --short` の出力を使わず、ディレクトリか `git add -u` で指定する | 各ステップのコミットで、本体・テストはパッケージのディレクトリ、文書は `docs/` の下のディレクトリで指定する（日本語のパスのマニュアルは `docs/manual` で指定する） |
| W6 の Try T-217、Bolt 39b2 の Try | 本体を直す前に Red を流し、落ちた理由を記録する。直したテストの一覧を添える | ステップ 2。ページ送りを表の前に移して落ちる既存のテスト（`UndecidedRecordControllerTest` の HTML の順、`review_undecided_records_ui.feature` のページ送りのステップ）と、`UndecidedRecordPage` の項目が増えて直すもの（照会と controller のテストの組み立て）を、ステップ 2 の開始で grep して一覧にする |
| W6 の Try T-191 | 環境の前提に設定ファイルの行を添える | CSRF と認可の行（SecurityConfiguration.java :73・:224-225）、H2 の発行記録の列（db/migration/h2/V20261104103700）を決定 (4)・(5) に添えた |
| W6 の Try T-225・T-213 | push の前に `check` | ステップ 7（`uiTest` も流す） |
| W6 の Try T-182・T-211・T-216 | 状態の組み合わせの表に重複・隔離と出口・誤った入力の行を置く | 下の表（行 1〜22。行 21 は 39d4 に送る）。対象の外（行 5）、同時の却下（行 8・9）、取込がない（行 10）、誤った入力（行 7） |
| W6 の Try T-184 | listener は配信まで確かめる | 行 13（`ExternalRecordAuditDeliveryIntegrationTest` に 1 本） |
| W6 の Try T-219 | 変異の対象に単体・統合・契約のテスト | 下の変異の表 |
| W6 の Try T-215・T-222 | Gradle を流す担当を 1 つに、計画の初稿を先にコミット | Gradle は人の側の 1 か所で流す（`uiTest`・`manualScreenshots`・`demoVideo` を含む）。初稿をコミットしてから実装に入る |
| Bolt 39a2 の T-229 | 永続化の Bolt の状態の組み合わせに時刻の出どころと同時の書き込みの行を置く | 時刻: 判断の時刻は Clock から 1 回取り、全件で同じ（行 4）。同時の書き込み: 読んだ後に 1 件ずつ却下された受信記録（行 8）、まとめての却下の二重送信（行 9）、一覧の件数とページの間の判断（行 15） |
| Bolt 39a3 の T-230 | 入ることを確かめる行のデータはドメインの規則に合う組み合わせにする | 形式の誤りと内容の違う原本が混ざる取込は、寄港 2 つ以上・時刻の順の航海の行と列の数の違う行を同じファイルに置き、同じキー・違う値の行を 2 度取り込んで作る。情報源は提供元の名前に自分の UUID を含める |
| Bolt 39b の T-231 | `@Transactional` は契約のクラスに付ける | 行 5・17 を契約のクラス（`ExternalDataRecordRepositoriesContract`）に置き、トランザクションは契約のクラスの注釈で受ける |
| Bolt 39b の T-232・Bolt 39b2 の T-234・Bolt 39b3 の T-237 | 本体を直したステップは承認の前に `spotbugsMain`・`checkstyleMain`・`sonar-local:check` | ステップ 3〜5 |
| Bolt 39b2b の T-235 | テストの名前の「行 N_」に Bolt の接頭辞を付けるかを W7 の締めで決める | 39d2 の前例のまま。新しいテストのクラスはない（受入シナリオの Steps だけ）。既存のテストのクラスでは `@Nested class Bolt39d2b_…`、契約のテストは名前の頭に `Bolt39d2b_` |
| Bolt 39b2b の T-236 | 「起きないこと」を確かめるテストは呼び出しの記録で確かめる | 0 件・権限なし・ない取込のとき、判断を追記せずイベントを出さない（行 9〜11）、絞らない一覧でキーのない隔離を数えない（行 2）、件数が減らなければ数え直さない（行 15）を、ポートとイベントの発行の呼び出しの記録で確かめる |
| Bolt 39b3 の T-238 | サブエージェントへの指示は計画の決定を言い換えず計画の行を指す | 任せるときは「開始準備の決定」の番号と「状態の組み合わせ」の行番号を指す |
| Bolt 39b3b の T-239 | 人に諮る選択肢・主張の前提はコードで確かめ、出典の行を添える | 開始準備の決定の前提に出典の行を添えた（キーのない隔離の規則、取込がキーのない行を照合しないこと、判断のリポジトリのセーブポイント、判断の画面の文、ページ送りの位置、行 7 と 39d2 の行 5、取込の 1 万件の時間） |
| Bolt 39b3b の T-240 | 表から作る規則には、全部の行が規則に入ることを確かめるテストを添える | 対象（行 5）を分類のすべて（`ADOPTED`・`DUPLICATE`・`QUARANTINED`・`DISCREPANCY`）と外部原本の有無のパラメーターのテストで縛る。`KeylessRejectionOutcome` のすべての結果が画面の行き先を持つことを controller のテストで縛る |
| Bolt 39b4 の T-241・T-242 | 性能の目標を外れたら区切って測る。上限いっぱいの入力の行を入れる | 上限いっぱい（1 万件）は 39d4 に送る（行 21。決定 (1)）。39d4 で 5 分を超えたら区切って測り、分け方を諮る |
| Bolt 39c の T-243 | 状態の数を 0 件にする受入シナリオは計画の時点で controller のテストに下ろす | まとめての却下で形式の誤りが 0 件になり、リンクが消える（行 22）は UI で作れる（形式の誤り 2 件と内容の違う原本 1 件の取込を作る）。POST の時点で 0 件（行 9）は controller とサービスのテストに下ろす |
| Bolt 39c2 の T-244 | パラメーターのテストは入力と期待する出力の組み合わせまで縛る | 行 6（件数 1・1,000・10,001 と文）、行 7（理由の空・空白だけ・1000・1001 文字と欄の誤りの文） |
| Bolt 39c3 の T-245 | 並びの規則を足すテストは書く順を逆にしたデータを置く | 行 5 の対象の並び（範囲のないファイルの誤りを先、行の範囲の順）を、書く順を逆にしたデータで確かめる |
| Bolt 39c4 の T-246 | 新しい型の名前は同じ単純名がないかを検索してから決める | 開始準備の決定 (10)（入れ子の `Rejected` を `RejectedAll` にした） |
| 画面の Bolt の前例（memory の manual-in-bolt-done-criteria、Bolt 37f2 の T-197） | 振る舞いが変わる Bolt はマニュアルの更新を完了条件に入れる。マニュアルの文を実装と突き合わせる | 完了条件に 12.2 のまとめての却下・ページ送りの位置・キャプチャ・付録 B を入れる。ステップ 6 で知らせ・欄の誤り・0 件の文を controller の定数とテンプレートに突き合わせる |
| Bolt 39d2 の終了報告の既知の課題と開発レビューの低 | 39d2b: まとめての却下、件数が減ったときの示し方、39d の行 7、ページ送りのキーボードの負担 | 決定 (2)〜(8) |
| Bolt 39d2 の終了報告の議題 2 | 却下した原本の送り直しが重複になり一覧に戻らないおそれ | 39d4 の開始準備で確かめる（39d2 のとおり）。キーのない隔離はキーを持たないので重複にならない（決定 (3) の出典）が、受入シナリオは 39d4 に送る（決定 (9)） |

## 状態の組み合わせ（行ごとにテストを 1 本。T-182）

対象の照会の DB の通しは契約のテスト（`ExternalDataRecordRepositoriesContract`。メモリ・H2・PostgreSQL）、まとめての却下のサービスは単体（`ReceivedRecordDecisionServiceTest` の `@Nested class Bolt39d2b_…`。ポートとイベントの発行を mock）、照会のサービスは単体（`UndecidedRecordQueryServiceTest`。ポートを mock）、画面は WebMvc のテスト（`UndecidedRecordControllerTest` の `@Nested class Bolt39d2b_…`。サービスと照会は mock）、監査は配信の統合テスト（`ExternalRecordAuditDeliveryIntegrationTest`）、認可・CSRF・DB の通しはセキュリティの統合テスト（`ExternalDataIngestionSecurityIntegrationTest` の `@Nested class Bolt39d2b_…`。PostgreSQL）、画面の通しは `reject_keyless_quarantines_ui.feature`（Feature の `@ui @US-14 @must`、幅 320 px、axe）。

| 行 | 状況 | 期待 | 確かめ方 |
| :--- | :--- | :--- | :--- |
| 1 | 取込で絞った一覧で、その取込に判断を待つ形式の誤りがある | 件数の文の後に「この取込の形式の誤りをまとめて却下する（N 件）」のリンク（`…/keyless-rejection?ingestion=…`） | controller、UI |
| 2 | 絞らない一覧・取込が見つからない一覧・形式の誤りが 0 件の取込（内容の違う原本だけ） | リンクを出さない。絞らない一覧ではキーのない隔離を数えない（照会の呼び出しの記録） | controller、照会のサービスの単体 |
| 3 | まとめての却下の画面の中身 | 見出し「形式の誤りのまとめての却下」、取り込んだ日時・情報源・「形式の誤り N 件」、内容の違う原本は含まないこと、取り込み直しの案内、理由の欄（必須、1000 文字まで）、取り消せないことの文、「まとめて却下する」「一覧に戻る」（取込で絞った一覧） | controller（HTML の文字） |
| 4 | まとめて却下する | その取込の判断を待つ形式の誤りのすべてに判断（`REJECTED`）が 1 つずつ追記され、判断者・時刻・理由は全件で同じ（時刻は Clock から 1 回、マイクロ秒に切り捨て）。取込で絞った一覧の 1 ページ目に戻って「形式の誤り N 件を却下しました。」（3 桁の区切り）。内容の違う原本は一覧に残る | サービスの単体、セキュリティの統合テスト、UI |
| 5 | 対象の外（内容の違う原本・ほかの取込の隔離・判断済み・採用・重複・差異） | 対象の照会と件数に入らず、判断を作らない。分類のすべてと外部原本の有無のパラメーター（T-240）。並びは範囲のないファイルの誤りを先、行の範囲の順（書く順を逆にしても。T-245） | 契約のテスト（`undecidedKeylessQuarantinesOf`・`countUndecidedKeylessQuarantines`） |
| 6 | 件数の示し方 | 1・1,000・10,001 件で、リンク・画面・知らせの数が 3 桁の区切り（T-244） | controller（パラメーター） |
| 7 | 理由が空・空白だけ・1001 文字（コードポイント。サロゲートペアを含む）、1000 文字 | 空・空白だけ・1001 はまとめての却下の画面に戻り、判断の画面と同じ欄の誤り（「却下の理由を書いてください。」「却下の理由は 1000 文字までで書いてください。」）とエラー要約。サービスを呼ばない。1000 は通る | controller（パラメーター。呼び出しの記録） |
| 8 | 読んだ後に 1 件ずつ却下された受信記録がある（同時の却下） | その受信記録の一意制約の違反をとばし、数えない（N は実際に却下した件数）。ほかの受信記録は却下する。とばした受信記録の DE-29 を出さない | サービスの単体（追記の 2 件目が `DuplicateReceivedRecordDecisionException`） |
| 9 | 0 件（GET の時点・POST の時点・二重送信・全部がとばされた） | 判断を作らずイベントを出さず、取込で絞った一覧に戻って「この取込にまとめて却下できる形式の誤りはありません。」 | サービスの単体（呼び出しの記録）、controller |
| 10 | 取込の指定がない・UUID でない・ない取込 | GET と POST のどちらも 404（型変換の 400 にしない）。`keyless-rejection` は判断の画面の `/{受信記録 ID}` に入らない。サービスはない取込で `NotFound` | controller、サービスの単体 |
| 11 | 権限: データ責任者のほかの役割・未ログイン・CSRF のトークンがない POST | ほかの役割は A-04、未ログインは A-01（`/staff/external-data/**` の今の規則）、トークンなしは 403 で判断を作らない。サービスもデータ責任者でなければ `NotPermitted` | セキュリティの統合テスト、サービスの単体 |
| 12 | DE-29 | 却下した受信記録ごとに `ReceivedRecordDecided` が 1 つ（受信記録 ID・取込 ID・情報源 ID・`REJECTED`・理由・判断者・判断者の企業・時刻。1 件ずつの却下と同じ形） | サービスの単体 |
| 13 | 配信（T-184） | まとめて 3 件を却下したコミットの後に listener が受け、受信記録ごとに監査記録 `EXTERNAL_RECORD_DECIDED` が 1 つずつ、計 3 つある | `ExternalRecordAuditDeliveryIntegrationTest` |
| 14 | 途中の失敗 | 2 件目の追記が DB の失敗（一意制約の違反でない）なら例外を上げる（握りつぶさない）。トランザクションごと巻き戻り、判断も DE-29 も残らない | サービスの単体（例外が上がり、後の受信記録を追記しない）、セキュリティの統合テスト（PostgreSQL。判断のリポジトリを spy にして 2 件目で失敗させ、判断の件数が 0） |
| 15 | 件数とページの間に件数が減る（39d の申し送り） | 読んだページが空で件数が 0 より大きいとき、数え直して最後のページに寄せ、1 回だけ読み直す。示す範囲は読んだ件数から作る（「0〜0 件目」を出さない）。ページが空でなければ数え直さない（呼び出しの記録） | 照会のサービスの単体 |
| 16 | 読み直しても空・数え直して 0 件 | 0 件の文（取込で絞っていれば「この取込に判断を待つ隔離はありません。」） | 照会のサービスの単体、controller |
| 17 | 行 7 を契約のテストに（39d の申し送り） | 判断のポートで判断を追記した隔離は、絞らない件数からも消え（追記の前と後の差が 1）、絞らない一覧の先頭にも出ない（未来の時刻の取込で先頭に来るようにする。39d の行 7 の前例）。JDBC の行 7 を消す | 契約のテスト（メモリ・H2・PostgreSQL） |
| 18 | ページ送りの位置 | ページ送りの `nav`（1 つ、名前は「判断を待つ隔離のページ」）が件数の文とまとめての却下のリンクの後、表の前にある。キーボードで先頭から Tab で進めると、100 個の「判断する」より前に届く | controller（HTML の順）、UI（Tab に戻したステップ） |
| 19 | UI の幅と axe | まとめての却下の画面と知らせの出た一覧が幅 320 px で横スクロールを出さず、axe の違反 0 | `reject_keyless_quarantines_ui.feature` |
| 20 | マニュアルのキャプチャ | 新しい `12-undecided-records-keyless-rejection.png`（まとめての却下の画面）。`12-undecided-records.png` はページ送りの位置が変わるので撮り直す（リンクは取込で絞ったときだけ）。マニュアルの文（リンク・知らせ・欄の誤り・0 件）が実装と一致する（T-197） | `manual_screenshots.feature`、ステップ 6 の突き合わせ |
| 21 | 上限いっぱい（1 万件）のまとめての却下の時間 | **39d4 に送る**（決定 (1)。【前の決定の変更】(1)）。39d2b では作らない | — |
| 22 | まとめて却下した後の一覧 | 内容の違う原本だけが残り、リンクが消える。内容の違う原本もなければ「この取込に判断を待つ隔離はありません。」 | `reject_keyless_quarantines_ui.feature` |

## スコープ

### 入れるもの

- 行 1〜22 のテスト（行 21 を除く）。決定 (10) の型、受信記録のポートの 2 つの照会（マッパー・メモリの実装）、`ReceivedRecordDecisionService.rejectKeyless`、`UndecidedRecordQueryService` のまとめての却下の画面の中身・キーのない隔離の件数・件数が減ったときの読み直し、`UndecidedRecordController` のまとめての却下の画面と POST、テンプレート（`keyless-rejection.html` と `index.html` のリンク・知らせ・ページ送りの位置）、受入シナリオと Steps、キャプチャのシナリオ、デモのシナリオ（`@demo @demo-bolt-39d2b/reject-keyless`）、39d の UI のステップを Tab に戻す
- マニュアル: `docs/manual/12-外部データ.md` の 12.2 に「形式の誤りをまとめて却下する」（いつ使うか（直したファイルを取り込み直した後の片付け）、操作手順: 一覧を取込で絞る → 「この取込の形式の誤りをまとめて却下する」→ まとめての却下の画面で件数と情報源を確かめ理由を書く →「まとめて却下する」→ 一覧に戻る知らせ、内容の違う原本は含まないこと、0 件のとき）、一覧の画面の説明のページ送りの位置（⑤）、「この画面でできること」、注意・補足（取り消せない。【業務の判断】(e)）、付録 B のエラーメッセージ早見表に「この取込にまとめて却下できる形式の誤りはありません。」の 1 行（データ責任者が 2 人で同じ取込をまとめて却下したときに必ず出うる文なので足す。39d2 の「この隔離は判断済みです。」の前例）、新しいキャプチャと撮り直し
- 文書: 下の「設計文書に直す箇所」（ステップ 1）

### 入れないもの

- 上限いっぱい（1 万件）のまとめての却下の時間の計測（39d4。決定 (1)。行 21）、同じファイルを取り込み直すと再び判断を待つ隔離になる受入シナリオ（39d4。決定 (9)）
- 内容の違う原本のまとめての却下（【業務の判断】(g) の外）、取込をまたぐまとめての却下、まとめての採用
- 判断の画面からまとめての却下へのリンク、S-16 の結果の画面からまとめての却下へのリンク（入口は取込で絞った一覧の 1 つにする）
- 採用の判断（39d3）、判断の後に送り直されたときの扱い（39d4）、差異の判断（39h）、判断済みの一覧・却下の理由を読む画面（AUD-UI-01。W8）、判断の取り消し（D-95）

### 申し送り（既知の課題・入れないもの）

| 送り先 | 課題 | 扱い |
| :--- | :--- | :--- |
| 39d4 | まとめての却下の上限いっぱい（1 万件）の時間（判断・DE-29 の 1 万件、判断のリポジトリのセーブポイント 1 万回、イベントの発行記録 1 万件）。ログの「PERF-06 keyless-rejection」の行。5 分を超えたら止めて分け方を諮る | 39d4 の開始準備で 39d の行 19 の一覧の計測と同じ準備に入れる（T-242） |
| 39d4 | 同じファイルを取り込み直すと形式の誤りが再び判断を待つ隔離になる受入シナリオ（取り消せないことの文の裏付け） | 39d4 の開始準備で、39d2 の議題 2（却下した原本の送り直し）と合わせて確かめる |
| 39d3 | 採用の判断を同じ判断の画面に足す（39d2 のとおり） | 39d3 |
| W8 | 却下の理由を読む手段（AUD-UI-01）。まとめた却下は同じ判断の時刻・判断者・理由で読める | release_plan :1034 のまま |

### 設計文書に直す箇所（ステップ 1）

- ui_design: S-17 の URL の表（:193-195 の後）にまとめての却下の画面 `GET /staff/external-data/undecided-records/keyless-rejection?ingestion=…` と `POST`（PRG・知らせ・欄の誤り・0 件・404）の 2 行、一覧の行（:193）の「39d2b」の記述を「取込で絞り、形式の誤りが 1 件以上のときだけリンク」と件数が減ったときの示し方を作ったことに直す、画面遷移（S-17 の節の遷移図）に「一覧 → まとめての却下の画面 → 一覧」（【前の決定の変更】(3)）、S-17 の節（:1932・:1936 の後）に 39d2b の段落と画面イメージ（下の「画面イメージ」）、一覧の画面イメージ（:1951）のページ送りを表の前に（【前の決定の変更】(2)）
- domain_model: 判断のサービス（:324）に `rejectKeyless`（1 つのトランザクション、一意制約の違反はとばす、`KeylessRejectionOutcome`、`RejectKeylessQuarantinesCommand` に `@Command`）、受信記録のリポジトリの行に 2 つの照会、受信記録の判断の節（:1522）の「まとめて却下できる（39d2b）」を作ったに直す
- system_usecase（UC-15 の 3b）: まとめての却下を作ったこと（Bolt 39d2b）
- non_functional: PERF-06 の行（:62）に、まとめての却下の上限いっぱいの時間をログの「PERF-06 keyless-rejection」の行に出す計測を 39d4 で行うこと（参考の値。5 分を超えたら止めて分け方を諮る）（【前の決定の変更】(1)）
- test_strategy: 外部データの画面の行（:347）の末尾の「判断の表に行のある隔離を返さないことを契約のテストに上げること（39d の行 7）、件数と 1 ページの照会の間に件数が減ったときの示し方、まとめての却下と上限いっぱい（1 万件）の時間の計測は 39d2b」を、39d2b の確かめ方（上の「状態の組み合わせ」の冒頭の段落）と「上限いっぱいの計測は 39d4」に直す。39d の段落の「判断の表に行のある隔離を返さないことは `H2ExternalDataRecordRepositoriesTest`・`MyBatisExternalDataRecordRepositoriesIntegrationTest` だけ」を契約のテストに直す
- release_plan: 39d2b の行（:965）に計画へのリンクと「上限いっぱいの計測は 39d4 に送った」の注、39d4 の行に「まとめての却下の上限いっぱい（1 万件）の時間の計測（39d2b から送った）」（【前の決定の変更】(1)）。台帳の D-96（:1073）はこの開始準備で足した
- データモデル・ADR: 変えない（スキーマを変えない。ADR-004 の判断の記録と監査の形は 39d2 のまま）

## 設計（この Bolt の範囲）

### 画面遷移図（S-17 の 39d2b の分）

```plantuml
@startuml
title S-17 隔離・差異の確認（Bolt 39d2b）
[*] --> 一覧
一覧 : S-17 GET /staff/external-data/undecided-records?ingestion=…
一覧 : 取込で絞り、形式の誤りが 1 件以上なら「この取込の形式の誤りをまとめて却下する（N 件）」
まとめて : S-17 GET /staff/external-data/undecided-records/keyless-rejection?ingestion=…
まとめて : 取込・情報源・形式の誤りの件数、却下の理由の欄
一覧 --> まとめて : この取込の形式の誤りをまとめて却下する
まとめて --> まとめて : まとめて却下する（理由が空・1000 文字超）→ 欄の誤り
まとめて --> 一覧 : まとめて却下する（POST。PRG）→「形式の誤り N 件を却下しました。」
まとめて --> 一覧 : 0 件 →「この取込にまとめて却下できる形式の誤りはありません。」
まとめて --> 一覧 : 一覧に戻る
一覧 --> 判断 : 判断する（39d2。内容の違う原本は 1 件ずつ）
判断 : S-17 GET /staff/external-data/undecided-records/{受信記録 ID}
@enduml
```

### 画面イメージ

S-17 の一覧（取込で絞った。ページ送りを表の前に移し、まとめての却下のリンクを足す）:

```plantuml
@startsalt
{+
  <b>隔離・差異の確認（S-17）
  この取込の判断を待つ隔離 9,901 件（101〜200 件目を示しています）
  [ この取込の形式の誤りをまとめて却下する（9,900 件） ]
  [ すべての取込の隔離を見る ]
  [ 前の 100 件 ] [ 次の 100 件 ]
  {#
    取り込んだ日時 | 情報源 | 行 | 種類 | 外部 ID・外部版 | 隔離の理由 | 判断
    2026-11-16 10:05 Asia/Tokyo（UTC+09:00）（UTC 2026-11-16 01:05） | 開発海運（架空）（航海） | 102 行目 | 形式の誤り | — | 102 行目: 寄港の列の値が正しくありません（…）。提供元に確かめてください。 | [ 判断する ]
    2026-11-16 10:05 Asia/Tokyo（UTC+09:00）（UTC 2026-11-16 01:05） | 開発海運（架空）（航海） | 140 行目 | 内容の違う原本 | DEMO-201・3 | 140 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: 2026-11-15 09:00:00 UTC）。 | [ 判断する ]
  }
}
@endsalt
```

S-17 のまとめての却下の画面:

```plantuml
@startsalt
{+
  <b>形式の誤りのまとめての却下（S-17）
  取り込んだ日時 | 2026-11-16 10:05 Asia/Tokyo（UTC+09:00）（UTC 2026-11-16 01:05）
  情報源 | 開発海運（架空）（航海）
  形式の誤り | 9,900 件
  --
  この取込の判断を待つ形式の誤りをすべて、同じ理由で却下します。
  内容の違う原本は含みません。一覧で 1 件ずつ判断してください。
  形式の誤りは採用できません。直したファイルを取り込み直してください。
  却下の理由（必須、1000 文字まで）
  {+ "列の数を直したファイルを 11-16 に取り込み直した。          " }
  却下すると取り消せません。誤って却下したときは、提供元に同じ原本を送り直してもらってください。
  [ まとめて却下する ] [ 一覧に戻る ]
}
@endsalt
```

画面イメージの名前・時刻・外部 ID は説明用の架空の値である（ui_design の画面イメージの決まり）。

### クラスと依存

```plantuml
@startuml
title S-17 形式の誤りのまとめての却下（39d2b）
package "externaldata.interfaces.web" {
  class UndecidedRecordController {
    list(ingestion, page) <<39d。リンクとページ送りの位置は 39d2b>>
    decision(id, ingestion, page) <<39d2>>
    reject(id, RejectionForm) <<39d2>>
    keylessRejection(ingestion) <<39d2b>>
    rejectKeyless(ingestion, RejectionForm) <<39d2b>>
  }
  class RejectionForm <<39d2。そのまま使う>>
}
package "externaldata.application.internal.commandservices" {
  class ReceivedRecordDecisionService {
    reject() <<39d2>>
    rejectKeyless(AuthenticatedActor, RejectKeylessQuarantinesCommand) <<39d2b>>
  }
  interface KeylessRejectionOutcome <<sealed。39d2b>>
}
package "externaldata.application.internal.queryservices" {
  class UndecidedRecordQueryService {
    page() <<39d。読み直しは 39d2b>>
    keylessRejectionTarget(ingestionId) <<39d2b>>
  }
  class KeylessRejectionTarget <<record。39d2b>>
}
package "externaldata.domain.model.aggregates" {
  interface ReceivedRecordRepository {
    undecidedKeylessQuarantinesOf(ingestionId) <<39d2b>>
    countUndecidedKeylessQuarantines(ingestionId) <<39d2b>>
  }
  interface ReceivedRecordDecisionRepository {
    add() <<39d2>>
  }
  class ReceivedRecordDecision <<record。39d2>> {
    reject(ReceivedRecord, reason, decidedBy, decidedAt)
  }
  interface IngestionRepository {
    findById() <<今ある>>
  }
}
package "externaldata.domain.events" {
  class ReceivedRecordDecided <<DE-29。39d2。形は変えない>>
}
package "identity.application.internal.eventhandlers" {
  class ExternalRecordAuditEventHandler {
    on(ReceivedRecordDecided) <<39d2。変えない>>
  }
}
UndecidedRecordController ..> ReceivedRecordDecisionService
UndecidedRecordController ..> UndecidedRecordQueryService
ReceivedRecordDecisionService ..> IngestionRepository : 取込と情報源 ID
ReceivedRecordDecisionService ..> ReceivedRecordRepository : 対象
ReceivedRecordDecisionService ..> ReceivedRecordDecisionRepository : 受信記録ごとに追記
ReceivedRecordDecisionService ..> ReceivedRecordDecision
ReceivedRecordDecisionService ..> ReceivedRecordDecided : 受信記録ごとに publishEvent
ReceivedRecordDecisionService ..> KeylessRejectionOutcome
UndecidedRecordQueryService ..> ReceivedRecordRepository : 件数・読み直し
UndecidedRecordQueryService ..> KeylessRejectionTarget
ExternalRecordAuditEventHandler ..> ReceivedRecordDecided : @ApplicationModuleListener
@enduml
```

注: 一覧のリンクの件数は、照会のサービスの `page` が取込で絞ったときだけ `countUndecidedKeylessQuarantines` で数え、`UndecidedRecordPage` に持たせる（絞らないときは数えない。行 2）。まとめての却下の画面の中身は `keylessRejectionTarget`（取込を `findById`、情報源を引き、件数を数える。取込がなければ空で 404、0 件なら件数 0 で controller が一覧に戻す）。層の規則（LayerArchitectureTest・ModularityTest・MapperSchemaArchitectureTest）が置き場所を拒んだときは置き場所を動かさず止めて諮る。

### ER 図（この Bolt で読み書きする表。表は変えない）

```plantuml
@startuml
hide circle
skinparam linetype ortho
entity "ingestion\n取込" as ig {
  * id : UUID <<PK>>
  --
  * data_source_id : UUID <<DE-29 の情報源 ID>>
}
entity "received_record\n受信記録" as rc {
  * id : UUID <<PK>>
  --
  ingestion_id : UUID <<FK。その取込で絞る>>
  external_record_id : UUID <<NULL がキーのない隔離（対象）>>
  * classification : VARCHAR(30) <<QUARANTINED だけ>>
}
entity "received_record_decision\n受信記録の判断" as rcd {
  * id : UUID <<PK>>
  --
  * received_record_id : UUID <<FK, UK。受信記録ごとに 1 つ追記>>
  * decision : VARCHAR(30) <<REJECTED>>
  * decided_by : UUID <<全件で同じ>>
  * decided_at : TIMESTAMPTZ <<全件で同じ>>
  reason : VARCHAR(1000) <<全件で同じ文。H2 だけ VARCHAR(2000)>>
}
entity "identity.audit_record\n監査記録" as ar {
  * action : VARCHAR(100) <<EXTERNAL_RECORD_DECIDED。受信記録ごとに 1 つ>>
}
ig ||--o{ rc
rc ||--o| rcd
rcd ..> ar : DE-29（コミットの後。受信記録ごと）
@enduml
```

## ステップ計画（ステップごとに止める）

- [ ] **1. 設計の文書**（ui_design・domain_model・system_usecase・non_functional・test_strategy・release_plan）: 上の「設計文書に直す箇所」（開始準備の決定 (1)〜(10) と【前の決定の変更】(1)〜(3) を反映する）（完了の判定: 文書の差分。**承認ゲート**）
- [ ] **2. テストの Red（外側から）**: 行 1・4・18・19・22 の feature の Red（今は一覧にリンクがなく、ページ送りが表の後にあって落ちることを確かめる）、続いて行 1〜3・6・7・9・10・16・18 の controller、行 4・8〜12・14 のサービス、行 2・15・16 の照会のサービス、行 5・17 の契約、行 13 の配信、行 11・14 のセキュリティの統合テスト。直す既存のテストを一覧にする（T-217）（完了の判定: 期待した理由で落ちる記録。**承認ゲート**（画面の振る舞いの契約））
- [ ] **3. 対象の照会と行 7**: `undecidedKeylessQuarantinesOf`・`countUndecidedKeylessQuarantines`（ポート・マッパー（.java・.xml）・MyBatis・メモリの実装）、行 5 の契約、行 17 を契約に足して JDBC の行 7 を消す（1 次キャッシュの確かめ。決定 (6)）。行 5・17 がメモリ・H2・PostgreSQL で緑（完了の判定: 緑、`spotbugsMain`・`checkstyleMain`・`sonar-local:check`。**承認ゲート**）
- [ ] **4. まとめての却下のサービスと照会**: `rejectKeyless`・`KeylessRejectionOutcome`・`RejectKeylessQuarantinesCommand`（`@Command`）、照会のサービスの `keylessRejectionTarget`・`KeylessRejectionTarget`・一覧のキーのない隔離の件数・件数が減ったときの読み直し（決定 (8)）、配信の統合テスト。行 2・4・8〜16 のサービスと照会と配信が緑（完了の判定: 同上。**承認ゲート**（新しいファイル））
- [ ] **5. 画面**: `UndecidedRecordController` のまとめての却下の画面と POST、`keyless-rejection.html`、一覧のリンクと知らせ、ページ送りを表の前に移す、39d の UI のステップを Tab に戻す、受入シナリオの Steps。行 1〜3・6・7・9〜11・14・16・18・19・22 が緑、受入シナリオが緑、39d・39d2 の S-17 と S-16 の UI のシナリオが緑（完了の判定: 緑、`spotbugsMain`・`checkstyleMain`・`sonar-local:check`、`uiTest` と axe 違反 0。**承認ゲート**（画面、新しいファイル））
- [ ] **6. マニュアルとキャプチャ**: 12.2 の「形式の誤りをまとめて却下する」・一覧の画面の説明のページ送り（⑤）・この画面でできること・注意・補足、付録 B の早見表の 1 行、`manualScreenshots`（行 20。新しい `12-undecided-records-keyless-rejection.png` と `12-undecided-records.png` の撮り直し。関係のないキャプチャが時刻の画素だけで変わったら戻す）。リンク・知らせ・欄の誤り・0 件の文を controller の定数とテンプレートに突き合わせる（T-197）（完了の判定: キャプチャの差分と突き合わせの記録。**承認ゲート**（マニュアル、新しいファイル））
- [ ] **7. 品質ゲートと開発レビュー**: 変異、`check`・`uiTest`、その後に SonarQube、開発レビューとその直し。push の前に `check`（T-225）
- [ ] **8. 終了報告**: push して CI とデモ環境の配備を確かめ（デモ環境でデータ責任者でログインし、形式の誤りと内容の違う原本の混ざる取込を取り込んで、S-17 を取込で絞ってまとめて却下する）、`demoVideo` で `@demo-bolt-39d2b/reject-keyless` を撮る（新しいファイル）。release_plan（39d2b の行の計画と終了報告のリンク）・開発の索引・mkdocs を直す。【業務の判断】(g)・(h) を終了報告の議題の先頭に置く

### 時間の配分と打ち切り

| # | 目安 | 打ち切り |
| :--- | :--- | :--- |
| 1 | 15 分 | — |
| 2 | 20 分 | — |
| 3 | 30 分（対象の 2 つの照会のポート・マッパー・メモリ・契約の 20 分、行 7 の 10 分（39d2 の契約の行 5 が取込で絞った分を確かめているので、絞らない件数の行を足して JDBC の行を消すだけ）） | 対象の照会の置き場所を層の規則が拒むときは、置き場所を動かさず止めて規則の例外を諮る（CLAUDE.md の層の規則の決まり）。1 次キャッシュで件数が変わらず行 17 を判断のポートで書けないときは、理由を書いて今の順に戻し、止めずに進める |
| 4 | 35 分（まとめての却下のサービス・結果・コマンドの 15 分、まとめての却下の画面の中身と一覧の件数の照会の 5 分、件数が減ったときの読み直しとそのテストの 15 分） | 1 件ずつの却下（`reject`）の振る舞いを変える必要が出たら止めて諮る |
| 5 | 45 分（まとめての却下の画面・POST・テンプレート・受入シナリオの Steps の 25 分、一覧のリンクと知らせの 10 分、ページ送りの位置と UI のステップの 10 分） | — |
| 6 | 20 分（12.2 の節とキャプチャ 1 枚・撮り直し 1 枚、付録 B） | — |
| 7 | 30 分（待ちを除く） | — |
| 8 | 10 分（CI と動画の待ちを除く） | — |

合計 205 分に、受入シナリオの例の 15 分（ステップ 5 の外。形式の誤りのまとめての却下・理由の空・0 件になってリンクが消える・幅 320 px）と予備の 5 分を足して約 3 時間 45 分。上限いっぱい（1 万件）のまとめての却下の時間の計測（約 20 分。`ExternalDataIngestionPerformanceTest` に 1 本、キーのない隔離 1 万件の取込の準備、ログの「PERF-06 keyless-rejection」の行、CI の手動のジョブ `performance`）を入れると約 4 時間 05 分で、目安（2〜4 時間）を 5 分超えるので、39d4 に送る（決定 (1)。【前の決定の変更】(1)）。39d2 の開始準備の決定 (1) の見積り（約 3 時間 05 分）との差は決定 (1) に書いた。実装の中でさらに増えるときは止めて諮る。マニュアルは後に送らない。

## 確認ポイント（計画の承認の場でまとめて確認する。Try T-6）

| # | 確認すること | 場所 | 推奨 |
| :--- | :--- | :--- | :--- |
| 1 | **範囲と分け方**（【前の決定の変更】(1)） | 開始準備の決定 (1) | 上限いっぱい（1 万件）のまとめての却下の時間の計測を 39d4 に送り（39d の行 19 の一覧の計測と同じ準備で測る）、39d2b を約 3 時間 45 分にする |
| 2 | **対象**（【業務の判断】(g)） | 決定 (2) | 1 つの取込の判断を待つキーのない隔離だけ。受信記録のポートに対象の照会と件数を足す。新しい索引は足さない |
| 3 | **画面**（【前の決定の変更】(3)） | 決定 (3) | 取込で絞った一覧のリンク → まとめての却下の画面 → PRG で一覧。0 件は知らせ、取込がないと 404。フォームは `RejectionForm` を使う |
| 4 | **却下のサービス** | 決定 (4) | 今のサービスに `rejectKeyless`。1 つのトランザクション、判断の時刻は 1 回、一意制約の違反はとばし、ほかの例外は巻き戻す。認可と CSRF は今の規則の中 |
| 5 | **イベントと監査** | 決定 (5) | DE-29 と `EXTERNAL_RECORD_DECIDED` を受信記録ごとに。形・書き手・規則の行は変えない。まとめた印は足さない |
| 6 | **行 7** | 決定 (6) | 契約に絞らない件数の行を足し、JDBC の行 7 を消す |
| 7 | **ページ送り**（【前の決定の変更】(2)） | 決定 (7) | 表の前（件数の文とまとめての却下のリンクの後）に 1 つだけ置く。UI のステップを Tab に戻す |
| 8 | **件数が減ったとき** | 決定 (8) | 空のページなら数え直して最後のページを 1 回読み直す（39d2 の決定 (8) の形のまま） |
| 9 | **理由と取り消し**（【業務の判断】(d)・(e)・(h)） | 決定 (3)・(9) | 1 件ずつと同じ。理由は 1 つを全件に。件数の入力などの追加の確かめは求めない。取り込み直しで戻る受入シナリオは 39d4 |
| 10 | **型の名前** | 決定 (10) | `RejectKeylessQuarantinesCommand`・`KeylessRejectionOutcome`（入れ子は `RejectedAll`）・`KeylessRejectionTarget`、テンプレート `keyless-rejection.html` |
| 11 | **変異** | ステップ 7 | 下の 6 個 |

### 人に諮ること

なし。推奨を 1 つに決められない判断はない（業務の意味を含む (g)・(h) は推奨を 1 つに決め、下の「業務の判断の記録」に置き、(g) は台帳 D-96 に仮の決定として載せた）。新しい本番・セキュリティの判断はない: まとめての却下は状態を変える POST だが、CSRF は Spring Security の既定の同期トークン（SecurityConfiguration.java :73）、認可は `/staff/external-data/**` のデータ責任者（同 :224-225）の今の規則の中で、サービスでも役割を確かめる（前例 ReceivedRecordDecisionService.java :71-73）。スキーマは変えない。監査は新しい操作を足さず、書き手と規則の行を変えない。上限いっぱいの計測を 39d4 に送ることは前の決定の変更だが、推奨を 1 つに決められるので諮らず、計画の承認で記録する。

### 業務の判断の記録

終了報告の議題の先頭に置き、人が後から見直せるようにする（前例 Bolt 39d の (a)〜(f)、Bolt 39d2 の (g)）。39d2b で実装するのは (g)（と 1 件ずつの却下から引き継ぐ (a)・(d)・(e)）で、(h) は新しく記録する。

1. 【業務の判断】(g) **1 つの取込のキーのない隔離をまとめて却下できるようにする**（39d2 の開始準備の決定 (9)。台帳 D-96。この Bolt の決定 (2)）: 対象は 1 つの取込の判断を待つキーのない隔離（形式の誤り・ファイルの誤り・取り込めなかった部分）だけ、理由は 1 つを全件に、受信記録ごとに判断・DE-29・監査を 1 つずつ。内容の違う原本は 1 件ずつ判断する。比べた案は 39d2 の計画の業務の判断の記録 4 のとおり
2. 【業務の判断】(h) **まとめての却下は確かめの画面を挟み、その時点で残っている分だけを却下する**（決定 (3)・(4)）: 一覧のリンクから直に却下せず、取込・情報源・件数を示す画面で理由を書いてから却下する。件数の入力などの追加の確かめは求めない（件数は画面に示し、取り消せないことの文を「まとめて却下する」の近くに置く。1 件ずつの却下の画面と同じ水準）。画面を開いた後にほかの人が 1 件ずつ却下した分はとばし、実際に却下した件数を知らせる（ほかの人の判断を上書きしない。判断は受信記録 1 件につき 1 つ）。比べた案: (i) 一覧から直に却下する（1 万件を件数と情報源を確かめずに取り消せずに却下しうる）、(ii) 件数を入力させて一致したら却下する（取り消せない操作の確かめを強めるが、画面を開いた後に件数が減ると一致せず、データ責任者が理由の分からない誤りを読む）、(iii) 画面を開いた時点の件数と違えば却下せず画面に戻す（同じく減った分を読み直す手間だけが増え、却下される受信記録の範囲は変わらない）

打ち切りの 2 つ（層の規則の例外、1 件ずつの却下の振る舞いの変更）に当たったときは、そのステップで止めて諮る。

### 変異と落ちる見込みのテスト

| # | 変異 | 落ちる見込み |
| :--- | :--- | :--- |
| V1 | 対象の照会の `external_record_id IS NULL` を外す（内容の違う原本も対象にする） | 行 5（契約。メモリは対応する条件を外す） |
| V2 | 対象の照会の判断の `NOT EXISTS` を外す（判断済みも対象にする） | 行 5（契約）、行 8 の件数（セキュリティの統合テスト） |
| V3 | まとめての却下で一意制約の違反をとばさず上げる | 行 8（サービスの単体） |
| V4 | 一意制約の違反でない例外も握りつぶして続ける | 行 14（サービスの単体） |
| V5 | 一覧のリンクの条件から「取込で絞っている」を外す | 行 2（controller・照会のサービスの単体） |
| V6 | 件数が減ったときの読み直しを外す（読んだページが空でもそのまま返す） | 行 15（照会のサービスの単体） |

## 完了条件

- [ ] 上のステップ 1〜8 の完了の判定を満たす（ステップごとの承認を受ける）
- [ ] 行 1〜22（行 21 を除く。39d4）に、それを名乗るテストがある（既存のテストのクラスでは `@Nested class Bolt39d2b_…` の中、契約のテストは名前の頭に `Bolt39d2b_`）
- [ ] 対象の照会がメモリ・H2・PostgreSQL の契約のテストで同じ振る舞い（キーのない隔離だけ、判断済みとほかの取込を除く、並び）。39d の行 7 が契約のテストにあり、JDBC で判断の行を入れるテストがない
- [ ] まとめての却下で受信記録ごとに DE-29 が出て、監査記録 `EXTERNAL_RECORD_DECIDED` が配信の統合テストで受信記録ごとに 1 つ残る
- [ ] S-17 のページ送りが表の前にあり、39d の UI のステップが Tab で進む
- [ ] マニュアル 12.2 のまとめての却下・ページ送りの位置・付録 B の早見表とキャプチャ（`12-undecided-records-keyless-rejection.png`、`12-undecided-records.png` の撮り直し）が実装と合っている
- [ ] デモの動画（`@demo-bolt-39d2b/reject-keyless`）があり、終了報告のデモ項目からリンクしている
- [ ] `check`・`uiTest` が緑、SonarQube が PASS、CI が緑、デモ環境が新しい版で動く
- [ ] 【業務の判断】(g)・(h) が終了報告の議題の先頭にあり、台帳 D-96 がある
- [ ] release_plan の 39d4 の行に、まとめての却下の上限いっぱいの計測がある
- [ ] 終了報告が承認されている

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-05 | 初版作成（承認待ち）。開始準備の決定: 上限いっぱい（1 万件）の計測を 39d4 に送る（【前の決定の変更】(1)）、対象は 1 つの取込の判断を待つキーのない隔離だけ（【業務の判断】(g)。台帳に D-96 を足した）、一覧のリンクからまとめての却下の画面を挟む（【前の決定の変更】(3)）、今の却下のサービスに `rejectKeyless`（一意制約の違反はとばす）、DE-29 と監査は形を変えず受信記録ごとに、行 7 は絞らない件数の行を契約に足す、ページ送りを表の前に移す（【前の決定の変更】(2)）、件数が減ったときは数え直して 1 回読み直す、確かめの画面を挟み残っている分だけを却下する（【業務の判断】(h)）。見積りは約 3 時間 45 分。人に諮ることはなし | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [リリース計画](release_plan.md)（W7 の節の 39d2b の行、台帳の D-95・D-96）
- [Bolt 39d2 計画](bolt_39d2_plan.md)・[Bolt 39d2 終了報告](bolt_39d2_report.md)（39d2b の範囲と申し送り、【業務の判断】(g)）
- [Bolt 39d 計画](bolt_39d_plan.md)・[Bolt 39d 終了報告](bolt_39d_report.md)（行 7 と件数が減ったときの申し送り、【業務の判断】(a)〜(f)）
- [Bolt 39b4 終了報告](bolt_39b4_report.md)（取込の 1 万件の時間）
- [UI 設計](../../design/cargo-tracker/ui_design.md)（S-17）、[ドメインモデル](../../design/cargo-tracker/domain_model.md)（受信記録の判断、IA-INV-15）、[非機能要件](../../design/cargo-tracker/non_functional.md)（PERF-06）、[テスト戦略](../../design/cargo-tracker/test_strategy.md)、[ユーザーマニュアル 12 章](../../manual/12-外部データ.md)
