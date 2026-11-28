---
type: Report
title: "Bolt 23 終了報告 - 本予約の確定と失効（US-04 AC1・AC2、#10）"
description: "23 回目の Bolt の終了報告。新設の booking モジュールで本予約の確定（確定条件、追跡番号、予約版、commit 時刻、予約サガの処理中、DE-07）と失効（BR-10）を業務ルール層まで作り、見積りの公開 API で輸送要求を予約確定済みにした。/goal で止まらなかった承認ゲート（スキーマを含む）、開発レビューの対応（DE-04 より先に DE-07 が届く不具合ほか）、計画からの変更、既知の課題を記録する。"
tags: [development,bolt-report]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-08T09:18:56Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-08T09:20:00Z }
---

# Bolt 23 終了報告 - 本予約の確定と失効（US-04 AC1・AC2、#10）

## 承認の議題（T-36。先に確かめてほしいこと）

人の指示（`/goal Bolt23 承認`）により、計画の承認から終了報告の前まで、承認ゲートで止まらずに進めた。W4 の計画はリスク台帳に従い Bolt 23 を「止める Bolt」としていたので、止まらなかったゲートごとの判断を次に置く。**議題 2（スキーマ）は確認必須の操作で、人の承認が要る。**

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **アーキテクチャのゲート**: ADR-015（予約サガは状態を予約に持ち、追跡の開始は追跡が DE-07 を購読して行う。ADR-003 の決定 3 と「再試行の担い手」を改訂）と ADR-016（予約は commit 時刻を渡して見積りの公開 API で確定に使えるかを確かめる）の文面 | 決定の形は W4 の開始準備と Bolt 23 の開始準備で人が決めたとおり。開発レビューで、W8 の有人案件の起票（予約から追跡を呼ばない形）と、ADR-016 のネガティブ（置換ではなく DE-06 との競合）を書き直した | [ADR-015](../../adr/cargo-tracker/015-booking-saga-starts-tracking-by-event.md)、[ADR-016](../../adr/cargo-tracker/016-booking-checks-quotation-at-commit-time.md) |
| 2 | **データベースのゲート（確認必須）**: `booking` スキーマ（`booking`・`booking_version`・`booking_saga`）とマイグレーション `V20261008150000__create_booking.sql`、`afterMigrate` の GRANT | 表と制約は data_model.md のとおり。計画からの変更は 3 つ: (a) 予約版の経路版を案件番号 `routing_case_number` で持つ（見積りの写しに合わせる）、(b) B-INV-11 の UK を予約版から貨物予約の `booking.quotation_id`（`uk_booking_quotation`）に移した（開発レビュー M-2・A-4。予約版の UK では US-05 の予約版 2 が違反する）、(c) 予約サガから再試行の列（`attempts`・`next_retry_at`・`last_error`）を消し、索引を `(status, started_at)` にした（開発レビュー A-2。ADR-015 で予約サガは再試行しない）。(b)(c) は、永続化された DB がまだない（デモ環境は配備ごとに H2 を作り直す）ため、push 前のマイグレーションを書き直した | マイグレーション、[データモデル](../../design/cargo-tracker/data_model.md) の `booking` スキーマ |
| 3 | **確定の規則・確定サービスの Red／Green のゲート**（ステップ 3・4）。2 つのステップで、Red を実行して確かめる前に実装を書いた（規律の逸脱。下の Problem） | テストの表は B-INV-01・08 と DE-07 の payload、受入シナリオは US-04 AC1・AC2 と test_strategy.md の境界の 3 点のとおり。開発レビューの対応（H-1・M-1・L-1）は Red を確かめてから直した | 計画のステップ 3・4 |
| 4 | **モジュールの境界のゲート**（ステップ 5）: 見積りの公開 API（`BookableQuotationQuery`・`BookingNotification`）、輸送要求の `BOOKED`、予約の DE-07 の listener | 公開 API の形は ADR-014（Bolt 20 の経路の割当てと同じ）。ModularityTest で「見積りは予約に依存しない」「予約は経路設計に依存しない」を確かめた | `quotation.api`、`booking/package-info.java` |
| 5 | **H3 の結論の訂正**: 計画のときは「DE-04 と DE-07 は同じ輸送要求を並行に更新しない」としたが、開発レビュー H-1 で、荷主の承認の直後（DE-04 の配信の前）に確定できると分かった。`markBooked` を「先の状態へだけ進める」に直し、並行の更新は共通の部品の読み直しで解く | 下の「仮説の結論」と「開発レビュー」 | `TransportRequest.markBooked` |
| 6 | 開発レビューの対応の範囲。直したもの 15 件、後に回したもの 6 件（下の「開発レビュー」） | 状態のずれ（H-1）、スキーマの直し（M-2・A-2）、誤った結果（M-1）、検証の穴（L-3〜L-6）は、この Bolt の目的そのものなので直した。Bolt 24・25・W6・W8 で作る振る舞いに要る決定は既知の課題にした | 開発レビュー |
| 7 | #10 は開いたまま、AC1・AC2 の業務ルール層までの結果をコメントする（クローズは Bolt 25） | 計画どおり。画面（S-09・S-24・S-02 の予約の確定待ちの表）と `@ui`・受入動画は Bolt 23b | [#10](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/10) |
| 8 | **人の指摘で直した**: 予約サガを `domain.model.sagas` に置いていたのは開発ガイドライン（第 3 章の `application.sagas`）と違う。`application.sagas` に戻し、層の規則 D-5 に「永続化の実装（`infrastructure.persistence`）だけが `application.sagas` を参照してよい」例外を足した。配置は ArchUnit の「サガはアプリケーション層の sagas に置く」で守る | AI は D-5 の規則が拒否したとき、ガイドラインを確かめずに置き場所を変えた。規則とガイドラインが食い違うときは、規則の例外を設計の判断として人に諮るべきだった（Try T-68） | `LayerArchitectureTest`、`booking/application/sagas` |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 23 回 |
| 期間 | 2026-10-08 16:44 JST（計画の承認 `8e1fa9a`）〜 本報告 |
| 対象 | U6 予約管理（新設の `booking` モジュール）、U1 見積りの公開 API と輸送要求の予約確定済み |
| GitHub | [#10](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/10)（SP 5 は Bolt 25 で数える） |
| 計画 | [Bolt 23 計画](bolt_23_plan.md) |

## 成果

### ステップの結果

| ステップ | 結果 | コミット | 備考 |
| :--- | :--- | :--- | :--- |
| 1. ADR-015・ADR-016 と設計文書 | 完了 | `d1f40b5` | ADR-003 の改訂、RTY-02 を「処理中の滞留の上限」に |
| 2. booking スキーマ | 完了 | `e60b20c` | 開発レビューで UK の移動と再試行の列の削除（議題 2） |
| 3. 貨物予約の規則 | 完了 | `b3d9ef3` | Red の確認の前に実装を書いた |
| 4. 確定サービスとリポジトリ | 完了 | `b3d9ef3` | 見積りの照会をステップ 5 から前倒しした。Red の確認の前に実装を書いた |
| 5. 見積りの公開 API・予約確定済み・DE-07 の listener | 完了 | `6c2571f` | `TransportRequestProgression` を共通の場所に移し、結果を返す |
| 6. 開発レビューと終了報告 | 完了 | `ea07876`、本報告 | 開発レビュー（プログラマー・テスター・アーキテクト）、SonarQube |

### 作ったもの

- `booking` モジュール（`allowedDependencies = {"shared", "quotation :: api", "platform :: web"}`）
  - ドメイン: 貨物予約 `Booking`（確定、DE-07 `BookingConfirmed`）、予約版 `BookingVersion`、予約 ID・追跡番号（`CT` と紛らわしい文字を除いた 12 桁、約 59 bit）・確定条件・予約条件、予約サガ `BookingSaga`（処理中で始める。状態とリポジトリは開発ガイドラインのとおり `application.sagas`）
  - アプリケーション: 確定サービス `BookingCommandService`（営業担当者の役割 → commit 時刻 → 見積りの照会 → 確定条件 → 追跡番号 → 保存 → 予約サガ → DE-07）、腐敗防止層 `QuotationBookability`・`QuotationBookingNotifications`、DE-07 の listener
  - インフラ: MyBatis のリポジトリ 2 つ（見積り ID の UK の違反だけを「すでに予約がある」にする）、追跡番号の発行（存在を確かめて引き直す。上限 3 回）
- 見積り: 公開 API `BookableQuotationQuery`（commit 時刻で確定に使えるか。判定は `Quotation.bookingRejectionAt` の 1 か所）と `BookingNotification`（輸送要求を予約確定済みにする。冪等）、輸送要求の `BOOKED`（社内「予約確定済み」、荷主「予約確定済み（追跡の開始の準備中）」）
- テスト: 業務ルール層の受入シナリオ `confirm_booking.feature`（確定、1 分前・同時刻・1 分後、未承認、確認なし、二度目、カスタマーサポート、予約確定済みへの配信）、PostgreSQL の統合テスト（スキーマ、リポジトリ、確定から DE-07 の配信・予約確定済み・予約サガの処理中まで）、ModularityTest の規則 2 つ、DE-07 の直列化の契約、`module-booking.puml`

### デモ項目

業務ルール層の受入シナリオの通過で示した（予約待ちの見積りを確定すると追跡番号が出て予約サガが処理中になる、期限と同時刻以後は失効で確定できない）。画面のデモと受入動画は Bolt 23b。

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `test` 1,278 件（Bolt 22 の 1,210 件から +68）、`uiTest` 59 本（変わらず。画面は Bolt 23b）、`documentationTest` 1 件 |
| 承認ゲートの通過 | 計画の承認 1 回、終了報告（本報告）。途中のゲート 4 つは止まらなかった（議題 1〜4） |
| 人の変更依頼 | 1（予約サガの置き場所。議題 8） |
| リードタイム | 約 2 時間 20 分（計画の承認から本報告まで。テストと SonarQube の待ち時間を含む） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `test`・`documentationTest` | 通過（1,278 件・1 件） |
| `uiTest`（axe-core を含む） | 通過（59 本） |
| ModularityTest・ArchUnit | 通過 |
| Spotless・Checkstyle・SpotBugs | 通過 |
| SonarQube（ローカル） | 品質ゲート PASS（新しいコードのカバレッジ 96.3%、重複 0%）。1 回目の走査で新しい指摘 5 件（空の `application.sagas` パッケージ、浮いた Javadoc、例外を投げ得る呼び出しが 2 つあるラムダ 2 件、統合テストの assert の数）が出たので直して 2 回目で通った |
| `okf:check` | ERROR 0 |

## 仮説の結論

| # | 仮説 | 結論 |
| :--- | :--- | :--- |
| H1 | 予約は見積りの公開 API だけで確定条件をそろえられ、`routing` に依存しない | 成り立った。経路版は見積りが割り当てた経路の写し（案件番号と経路版）で足り、ModularityTest の「予約は経路設計に依存しない」が通る |
| H2 | 失効の判定を見積りの 1 か所に置けば、予約の側に期限の規則の写しは要らない | 成り立った。予約のコードに有効期限の比較はない。境界（1 分前・1 マイクロ秒前・同時刻・1 分後）は見積りの側の単体テストで確かめた |
| H3 | DE-07 の受け口は、Bolt 22 の割り込みの部品で DE-04 の listener との競合に耐える | 部品を使った。計画のときは「並行に更新しない」と結論したが誤りで（議題 5）、DE-04 の配信より先に DE-07 が届くと並行に更新し得る。競合は部品が読み直して解く。並行の統合テストは作っていない（単体の競合のテストは Bolt 22 の部品の側にある） |

## 開発レビュー

プログラマー（テスターの観点を含む）とアーキテクトの 2 つの観点で行った。

| # | 指摘 | 重大度 | 対応 |
| :--- | :--- | :--- | :--- |
| H-1 | 荷主の承認の直後、DE-04 の配信の前に確定すると、DE-07 は荷主承認待ちの輸送要求に届き、予約待ちだけを進める `markBooked` が何もしない。後から DE-04 が届いて輸送要求が予約待ちで止まる | 高 | 直した（Red を確かめてから、`markBooked` を「先の状態へだけ進める」に。`TransportRequestTest`・`BookingNotificationServiceTest` に「荷主承認待ちで DE-07 が届く」） |
| A-1 | W8 の有人案件の起票を予約サガが依頼すると `booking → tracking` になり、ADR-015 に反する | 高 | 直した（ADR-015 に、起票は予約のイベントを購読する形、業務の失敗は追跡が自分で起票すると書いた） |
| A-2 | 予約サガの表に再試行の列が残り、滞留の判定の索引がない | 高 | 直した（議題 2 (c)） |
| M-2 / A-4 | 予約版の見積り ID の UK が、US-05 の予約版 2 と衝突する | 中 | 直した（議題 2 (b)） |
| M-1 | 追跡番号の重なりも「すでに予約がある」になる | 中 | 直した（Red を確かめてから、制約名で分け、追跡番号の重なりは技術の失敗に。メモリのリポジトリも同じ） |
| A-3 | ADR-016 のネガティブの前提（置換との競合）が誤り。承認済みの見積りは置換できず、競合するのは DE-06 | 中 | 直した（DE-06 との競合に書き直し、`FOR SHARE` か期待版を W6 の課題にした） |
| A-5 | DE-07 の通知が業務の理由で受け付けられないとき、予約サガに見えない | 中 | 直した（architecture_backend.md の予約サガの表に「見えない失敗」と監視を書いた） |
| A-6 | Bolt 25 に要る値（荷主・荷受人の ID、予定の区間の取得元）と `booking :: api` がない。`tracking_record` が `routing_case_id` のまま | 中 | `tracking_record` を `routing_case_number` に直した。値の取得元と `booking :: api` は ADR-015 のコンプライアンスに Bolt 25 の決定として書いた |
| A-7 | 「予約サガの再試行」の古い記述が 5 か所。ADR-015 のサガの置き場所 | 中 | 直した |
| L-1 | 確定条件が欠けていても追跡番号を発行する（DB を照会する） | 低 | 直した（Red を確かめてから、条件を先に判定する） |
| L-3 | 確定サービスの分岐（見つからない・置換済み・貨物の要約が空・権限の先行）が未検証 | 低 | 直した（`BookingCommandServiceTest` 4 件。見積りの照会と追跡番号の発行を差し替えた） |
| L-4 | 境界が分単位だけ | 低 | 直した（1 マイクロ秒前の行） |
| L-5 | 受入のステップ定義が見積り番号だけで見積りを探し、見つからないと乱数の ID で進む | 低 | 直した（シナリオの輸送要求で絞り、見つからなければ失敗） |
| L-6 | 予約サガの状態のステップが文言だけを比べる | 低 | 直した（表示名から `BookingSagaStatus` への対応） |
| L-2 | 予約の `created_at` を commit 時刻と別に Clock から読む | 低 | 直さない。`created_at` は監査の列で、業務の commit 時刻は予約版の `committed_at`（ADR-016）。同じ値にそろえる必要はない |
| A-8 | 確定条件のうち 3 つを `true` で固定しており、B-INV-01 の検証が形だけ | 低 | 意図はコードの注記（見積りが確定に使えると判定した時点で、有効な見積り・荷主の承認・承認済み経路版はそろっている。ADR-016）にある。W6 で経路版の再設計要（B-INV-05）を見積りの判定に足すとき、条件の組み立てを見直す |
| A-9 | `TransportRequestProgression` がロガーとイベント名を受ける | 低 | 後に回す（サブパッケージへの移動は、輸送要求を進める処理が増えたときに） |
| A-10 | `platform :: web` をまだ使っていない | 低 | 想定どおり（Bolt 23b で使う） |
| A-11 | 「予約は追跡に依存しない」の規則を今でも書ける | 低 | 後に回す（`tracking` を作る Bolt 25 で ModularityTest に足す。ADR-015 のコンプライアンス） |
| A-12 | B-INV-06 は DE-09 を予約の api で受ければ予約の状態だけで判定できる | 低 | 既知の課題に置いた（W7 で DE-09 を先に整えることを US-05 の前提にする） |

## 判断と学び

### 既知の課題（後の Bolt に持ち込む）

- **Bolt 23b**: 予約サガが処理中なら画面に「完了」と出さない。S-09 の確認の表示は参考で、確定の可否は POST の照会で決まる（ADR-016）ことを画面に示す。S-02 の予約の確定待ちの表と `TransportRequestLabels` の `BOOKED` の表示を、画面の層のシナリオで確かめる
- **Bolt 24**: 重複の確定に既存の追跡番号を返す（B-INV-03、AC4）とき、UK の違反で `AlreadyBooked` を返すいまの経路より先に処理済みコマンドを引く順序を決める。DE-21・DE-04 の再配信のシナリオ
- **Bolt 25**: 追跡の開始に要る値（荷主・荷受人の企業 ID、予定の区間の取得元）を DE-07 に足すか `booking :: api` の照会にするか決める。`booking :: api`（`@NamedInterface("api")`）を作り、ModularityTest に「予約は追跡に依存しない」を足す
- **W6**: 確定の照会と DE-06（再設計要）の競合を `FOR SHARE` か見積りの期待版で防ぐ（ADR-016）。確定条件の組み立てを見直す（A-8）
- **W7・W8**: DE-09・DE-10 を ADR-014 の形にそろえ、B-INV-06 を予約の状態で判定できるようにする（US-05 の前提）。予約サガの滞留の判定と有人案件の起票は、予約のイベントを購読する形で作る（ADR-015）
- **監視**: DE-07 の通知が業務の理由で受け付けられないときは警告のログだけ。OBS で件数を見る（architecture_backend.md）

### ふりかえり（KPT）

- Keep
  - 開始準備の整合性検証で依存の循環（予約サガが追跡を呼ぶ形）を見つけ、ADR を書いてから作ったので、モジュールの依存の向きを ModularityTest で最初から守れた
  - 失効の判定を見積りの 1 か所に置いたので、境界のテストが 1 か所で済み、予約に期限の写しがない
  - 開発レビューを 2 つの観点で行い、状態のずれ（H-1）とスキーマの将来の衝突（M-2）を push の前に見つけた
- Problem
  - ステップ 3・4 で、テストを書いた後に Red を実行して確かめる前に実装を書いた（TDD の三原則からの逸脱）
  - H3 の結論を、イベントの届き順（荷主の承認の commit から DE-04 の配信までの間に確定できる）を確かめずに書いた
  - スキーマの承認ゲートを止まらずに進め、開発レビューで 2 か所を書き直した。人が見ていれば先に気づけた可能性がある
- Try
  - T-66: 状態を進める listener を足すときは、「前提にした状態より前に届く」場合を単体テストの表に入れる（ほかの `mark*` と同じ「先の状態へだけ進める」を既定にする）（AI、次の Bolt から）
  - T-68: 層の規則（ArchUnit）が開発ガイドライン（`docs/article` の第 1〜3 章）の置き場所を拒否したときは、置き場所を動かさずに規則の例外を設計の判断として人に諮る。決めた置き場所は ArchUnit の規則で固定する（AI、次の Bolt から。CLAUDE.md に反映）
  - T-67: `/goal` で止まらずに進めるときも、スキーマ（確認必須）のステップは止める。止めないなら、その前に開発レビューのアーキテクトの観点をスキーマだけにかける（AI、次の Bolt から）

## 次の Bolt

Bolt 23b: 本予約の画面（S-09 確定、S-24、S-02 の予約の確定待ちの表）と画面の層のシナリオ（`@ui`）、受入動画。上の既知の課題（処理中を完了と出さない）を確認ポイントにする。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-08 | 承認の議題 1〜8（スキーマ、D-5 の例外を含む）を承認し、Bolt 23 を終えた（人の変更依頼 1: 予約サガの置き場所）。CI（run 144〜146）は緑 | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-08 | 人の指摘（予約サガの置き場所が開発ガイドラインと違う）で `application.sagas` に戻し、議題 8 と Try T-68 を足した | anthropic/claude-opus-5-5、指摘 human:kakimomokuri |
| 2026-10-08 | 初版（ステップ 1〜6 の結果、開発レビュー、品質ゲート、既知の課題、承認の議題 1〜7、Try T-66・T-67） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 23 計画](bolt_23_plan.md)
- [リリース計画](release_plan.md)（W4）
- [Bolt 22 終了報告](bolt_22_report.md)
- [ADR-015](../../adr/cargo-tracker/015-booking-saga-starts-tracking-by-event.md)
- [ADR-016](../../adr/cargo-tracker/016-booking-checks-quotation-at-commit-time.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)
- [データモデル](../../design/cargo-tracker/data_model.md)
- [バックエンドアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)
