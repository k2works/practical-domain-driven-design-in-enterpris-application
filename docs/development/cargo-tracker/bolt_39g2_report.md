---
type: Plan
title: "Bolt 39g2 終了報告 - 集荷の採用と引渡しの確認を予約の公開 API で予約に返し、追加する実績の選び方を直す"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-11T11:17:52Z }
---

# Bolt 39g2 終了報告 - 集荷の採用と引渡しの確認を予約の公開 API で予約に返し、追加する実績の選び方を直す

## 承認の議題（T-36。先に確かめてほしいこと）

追跡管理者が集荷の実績を登録または採用すると、追跡が DE-09（`PickupMilestoneAdopted`）を出し、追跡の listener が予約の公開 API（`TransportPhaseNotification.notifyPickedUp`）を呼んで、予約が輸送中・集荷後になるようにしました。引渡しでは DE-10（`DeliveryConfirmed`）から `notifyDelivered` で完了・完了になります。予約は追跡のイベントを購読しません（ADR-014）。段は戻さず、どの順に届いても結果は同じです。主要実績の保存は、表にある実績の中身が集約と違うか、表にあって集約にない実績があれば `IllegalStateException` で止めます（P-3）。計画の確認ポイント 1〜11 は承認済み（2026-11-11）のとおりに実装しました。スキーマと認可は変えていません。

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **計画に名指しのない形の判断**: (a) 予約の保存の版の不一致は、新しい例外の型を作らず Spring の `OptimisticLockingFailureException` にした（行を押さえた後には起きないはずの技術の失敗で、listener のトランザクションごと戻すだけなので、ドメインの型は要らないと判断した。`BookingRepository.update` の Javadoc に書いた）。(b) 集約の結果は決定 (3) の Refactor で「`Optional` を 2 つ足す」を選び、`MilestoneRegistration`・`MilestoneAdoption.Adopted` に `pickupAdopted`・`deliveryConfirmed` を足した。行 14（イベントは 1 つまで）は 2 つの結果の型のコンストラクターの不変条件にした。(c) 入力ポート `TransportPhaseCommandService` の引数は予約 ID・操作者・追跡番号・実績番号（後の 2 つはログだけ） | この形で承認する | `BookingRepository`、`MilestoneRegistration`、`MilestoneAdoption`、`TransportPhaseCommandService` |
| 2 | **守る既存のテストを 1 本直した**: 39gc の `TrackingRecordCommandServiceTest` の行 16 は「発行が空」を確かめていたが、時系列どおりの引渡しが DE-10 を出すようになったので、DE-18 が出ないことだけを確かめる形にした（行が縛っていたのは DE-18）。ほかの守る既存のテストは変えずに通った | このまま承認する | `TrackingRecordCommandServiceTest.Bolt39gc_確認中とDE18` |
| 3 | **P-3 の「表にあって集約にない」の確かめは、追加の INSERT の後に置いた**: 先に置くと、既存のテスト（同じ出典の同時の登録を、集約にない実績番号の行で作る）が一意制約の違反（競合）の前に例外になった。後に置けば、同じ出典の同時の登録は今までどおり競合、ほかの行は誤りとして止まる | この順で承認する | `MyBatisTrackingRecordRepository.saveMilestones` |
| 4 | **予約状態の写しの完了の変異は、統合テストではなく単体で落ちる**: 追跡記録の保存は `booking_status` の列を書かないので、集約で写しを完了にしても表には残らない。単体（行 15 の DE-10 のテスト）に「写しは確定のまま」を足して、変異 M11 を落とした | 記録として承認する | `TrackingRecordMilestoneTest` 行 15 |
| 5 | **規律の違反が 2 回あった**（禁止の形 `cat >`・`python -` を含むコマンド）: 意味のない `cat > /dev/null < /dev/null; python - …` を打った 1 回（入力待ちで止まり、止めた）と、別のコマンドの先頭に `cat > /dev/null` を付けた 1 回。作業ツリーへの影響はない（確かめた）。違反ではないが確かめの誤りも 1 回あった: 変異のスクリプトの最初の版は `gradlew.bat` を見つけられず全件 exit 1 になり、「落ちた」と誤って出た（テスト名がないことで気づき、作り直して流し直した） | T-247（拒むフック）の議題に足し、W7 の締めで諮る | 下の「ふりかえり」 |
| 6 | **受入シナリオの Red は単体と同じ段で書いた**（39gc の Try）。テストのコンパイルの失敗（javac の上限の 100 件）を確かめてから実装した。守る既存のテストの一覧を本体を直す前に流し直すこと（T-217）は、Red のテストのコードがコンパイルを止めるので、本体を直す前には流せていない（Green の後に `check` で全件流した） | 記録として承認する。次からは Red のテストを書く前に一覧を流す | 下の「計画から変えたこと」 |
| 7 | **既存のデータ**（決定 (9)）: 39g2 の前に採用した集荷・引渡しの予約は確定・集荷前のまま残る。配備の前にステージング・本番の件数を確かめる（既知の課題） | 計画のとおり | 下の「既知の課題」 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39g2 回（W7 の 30 本目） |
| 期間 | 2026-11-11（計画の承認 `03a4dcf35` から終了報告まで） |
| 対象 | 予約: `Booking.recordPickup`・`recordDelivery`、`TransportPhaseChange`・`TransportPhaseRefusal`、`TransportPhaseCommandService`・`TransportPhaseOutcome`、`BookingRepository.findByIdForUpdate`・`update`、公開 API `TransportPhaseNotification`・`TransportPhaseNotificationRequest`・`TransportPhaseNotificationReceipt` とアダプター。追跡: `PickupMilestoneAdopted`（DE-09）・`DeliveryConfirmed`（DE-10）、`BookingTransportPhaseNotificationEventHandler`、`BookingTransportPhases`、`MyBatisTrackingRecordRepository`（P-3）。マニュアル 7 章・9 章・用語集 |
| GitHub | なし（US を数えない技術の Bolt） |
| SP | 0 |
| ブランチ | develop（push していない） |
| 計画 | [Bolt 39g2 計画](bolt_39g2_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model（用語 5 行と既存の 2 行、予約の操作の名前、状態遷移図に確定 → 完了、B-INV-06・B-INV-12 の注、DE-09・DE-10 の行、ADR-014 の段落、「Bolt 39g2 で決めたこと」、P-3）、architecture_backend（追跡 → 予約を ADR-014 の形に、公開 API の一覧）、ADR-014（適用と改訂の経緯）、data_model、ui_design（S-24）、test_strategy、release_plan（39g2 の行、39g3 の行、順序、W7 CP19 の済み、台帳 D-128・D-129 と D-117 の注）、development_strategy（W7 の行に 39g3）。変えた図は `plantuml -charset UTF-8 -tsvg` で描画を確かめた | `8195c6e90` |
| 2〜9. Red・予約・公開 API・追跡のイベント・listener・統合・P-3 | 新しい型がない理由でテストのコンパイルが落ちる（100 件）ことを確かめてから実装した。状態の組み合わせの行 1〜24・26 のテスト。用語の検査のため用語集に `TransportPhaseRefusal` の行を足した | `c0696b3a3` |
| 10. S-24 と画面の層のシナリオ | 行 25（`BookingControllerTest`）、行 27（`transport_phase_from_tracking_ui.feature`、`@demo-bolt-39g2/transport-phase`）。行 28（認可）は既存の `@WebMvcTest` が変わらず通る | `9079e206c` |
| 11. マニュアル | 7 章（S-24 の状態に輸送中・輸送完了、開き直して確かめる注、確認中の集荷では確定済みのまま）、9 章（集荷・引渡しを登録・採用すると予約の状態が進む）、用語集（輸送中・輸送完了（予約の状態）） | `adeed4e46` |
| 12. 品質ゲート | 下の「品質ゲート」。checkstyle・spotless の直しはステップ 2〜9 のコミットに含めた。SonarQube の直しと動画 | `2c4064f16` |
| 13. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check`（`--continue`）の `test` 5,438 件、失敗 0（Bolt 39gc の 5,349 件から +89）。`documentationTest` 5 件、失敗 0。`checkstyleMain`・`checkstyleTest`・`spotlessJavaCheck`・`spotbugsMain` を含む。1 回目は `checkstyleMain`（`sameContent` の循環的複雑度 11）・`checkstyleTest`（受入シナリオの配信の分岐の循環的複雑度 11、使わない import）・`spotlessJavaCheck` が落ち、メソッドを分け、分岐をまとめ、`spotlessApply` で直した。最後の直し（SonarQube の違反のメソッド名）の後にもう一度流して成功した |
| `uiTest` | 1 回目（最後の直しの前）: 221 シナリオ、失敗 0（JUnit の XML では 442 件、うち 221 件はシナリオの外枠で skipped と数えられる。39gc と同じ数え方）。新しいシナリオは 1 回目で通った。最後の直し（`2c4064f16`）の後の 2 回目も 221 シナリオ、失敗 0 |
| `manualScreenshots` | 成功。新しいキャプチャはない。日付だけ変わったキャプチャ 56 件は HEAD に戻した |
| `demoVideo` | 成功。`bolt-39g2/transport-phase.webm` だけを残し、撮り直しで変わったほかの動画 88 件は HEAD に戻した |
| 変異 | 計画の 12 個（P-3 は中身の比べと表にあって集約にない確かめの 2 つに分けて 13 個）をスクラッチパッドの使い捨てのスクリプトで 1 つずつ当て、決めたテストを流した。すべて落ちた（下の表）。1 回目のスクリプトは Gradle の起動の誤りで全件の exit 1 を「落ちた」と出したので、作り直して流し直した結果を書く |
| SonarQube | 1 回目は Quality Gate が FAIL（新しいコードの違反 1 件: `TransportPhaseCommandService` の private メソッドの名前 `record` が制限された識別子と同じ）。`recordTransportPhase` に直した後の 2 回目は PASS（新しいコードのカバレッジ 96.1%、重複 0.01824%、違反 0） |
| `okf:check` | ERROR 0（WARN 10 は template の既存のリンク切れ） |
| CI | **流していない**。指示により push していないので、CI は走っていない |

### 変異

| # | 変異 | 落ちたテスト（行） |
| :--- | :--- | :--- |
| M1 | 集荷で輸送段階を集荷後にしない | `BookingTest` 行 1・3、受入シナリオ（行 26）、`TransportPhaseNotificationIntegrationTest`（行 19） |
| M2 | 完了の後の集荷を輸送中に戻す | `BookingTest` 行 1・3 |
| M3 | 確定からの引渡しを記録しない | `BookingTest` 行 2・3 |
| M4 | 取消済みにも記録する | `BookingTest` 行 1・2 |
| M5 | 記録済みでも保存する | `TransportPhaseCommandServiceTest` 行 4 |
| M6 | 予約の行を押さえない | `TransportPhaseNotificationIntegrationTest` 行 7 の 2 本 |
| M7 | 確認中の集荷でも DE-09 を出す | `TrackingRecordMilestoneTest` 行 10・12 と 39gc の行、受入シナリオ（行 26 と US-12 AC3） |
| M8 | 追跡が確認中なら DE-09・DE-10 を出さない | `TrackingRecordMilestoneTest` 行 12 |
| M9 | 保存の前に発行する | `TrackingRecordCommandServiceTest` 行 16 |
| M10 | listener（腐敗防止層）が記録しないを例外にする | `BookingTransportPhaseNotificationEventHandlerTest` 行 17・18 |
| M11 | DE-10 で予約状態の写しを完了にする | `TrackingRecordMilestoneTest` 行 15（議題 4） |
| M12b | P-3 の中身の比べを外す | `MyBatisTrackingRecordRepositoryIntegrationTest` 行 22 (b) の 8 本 |
| M12c | P-3 の表にあって集約にない確かめを外す | 同 行 22 (c) |

### 文書の突き合わせ（T-197・T-251）

マニュアル 7 章・用語集の状態の表示名（「確定済み」「輸送中」「輸送完了」）が `BookingViews.status` の文と一字ずつ一致することをコマンドで確かめた。9 章の登録の結果の文は変えていない（`TrackingRecordController` の文のまま）。

## 計画から変えたこと

- 守る既存のテストの一覧を、本体を直す前に流していない（議題 6）
- 行 5 の H2 の確かめは専用の統合テストを作らず、dev のプロファイル（H2）で動く `uiTest`（行 27 で予約の UPDATE が通る）で代えた。PostgreSQL は統合テストで行 5〜7・19・21・22 を確かめた（39gc と同じ）
- ステップ 2〜9 のコードを 1 つのコミットにした（結果の型の形の変更が集約・コマンドサービス・受入シナリオの組み立てにまたがり、途中で分けると develop にコンパイルの通らない状態が積まれるため）
- 受入シナリオのステップ定義で、追跡のステップの予約 ID を予約のステップ定義に写した（`AcceptanceStepArchitectureTest` が、ステップ定義がほかのコンテキストのステップに依存することを拒んだ。依存させず値を写した）
- 画面の層のシナリオの発生時刻は「今の 1 時間前」のステップにした（固定の日付を実の時計と比べない）。`TrackingUiSteps` にステップを 1 つ足した
- 行 7 と動画は送らずに済んだ（見積りの打ち切りは使わなかった）

## デモ項目

- 動画: [`@demo-bolt-39g2/transport-phase`](../../assets/demo/bolt-39g2/transport-phase.webm){:target="_blank"}（S-13 で集荷を登録すると、S-24 の状態が輸送中になる）
- マニュアル: 7 章・9 章・用語集を直した。S-24 のキャプチャは確定済みの予約のままで、撮り直していない
- デモ環境の画面は開いて確かめていない（push していないので配備もしていない）

## 判断と学び

- 段を戻さないことを予約の集約の 2 つの操作の網羅の switch にしたので、B-INV-12 の版の比べを予約に持ち込まずに届く順の守りができた（行 3、変異 M2・M3）
- 同時の通知は予約の行を押さえるだけで、読み直しの繰り返しなしに両方とも例外なく終わる（行 7、変異 M6）。前例（DE-13 の listener）の形がそのまま使えた
- 1 つの操作で出るイベントは 1 つまでを結果の型の不変条件にすると、変異 M7（確認中の集荷でも DE-09）は DE-18 と重なってコンストラクターで落ちる。表の行をテストの名前に付けたので、落ちた行がすぐ分かった
- 変異のスクリプトは「落ちたテストの名前」が出ていることまで確かめないと、起動の失敗を「落ちた」と読み違える

### ふりかえり（KPT）

- Keep: 状態の組み合わせの表の行をテストの名前に付け、13 個の変異がすべてどこかの行で落ちた。受入シナリオを単体の Red と同じ段で書いた
- Problem: 標準入力につなぐ形のコマンドを 2 回打った（議題 5）。どちらも確かめのつもりの短いコマンドに不要な先頭を付けたもの。守る既存のテストを本体を直す前に流せなかった
- Try: Bash のコマンドは必要な部分だけを打ち、先頭に何も足さない（T-247 のフックで拒む）。守る既存のテストは Red のテストを書く前に流す。変異のスクリプトは落ちたテストの名前がない exit 1 を「落ちた」にしない（今回そう直した）

### 既知の課題（後に持ち込む）

- **配備の前**: ステージング・本番で、採用の集荷・引渡しを持つ追跡の予約が確定・集荷前のままの行の数を確かめる（決定 (9)。補正のマイグレーションは作っていない）。目安の照会は「`tracking.milestone` の `state = 'ADOPTED'` かつ `kind IN ('PICKUP','DELIVERY')` の追跡番号の、`booking.booking` の `status = 'CONFIRMED'` の行」。件数があれば人が判断する。照会は運用手順書のタスクで行う（独自のスクリプトを作らない）
- 39g3: 到着見込みと鮮度、S-11 の到着の列（Bolt 26 の U-3）
- W7 の締め: T-247 に標準入力のコマンドを足す（議題 5）
- W10: 定期の再配信と未完了の発行の件数の監視（DE-09・DE-10 の通知が起動まで残る経路）
- W11: 申請中の予約への集荷・引渡し（`BOOKING_UNDER_REQUEST` の決め直し）、B-INV-06 の承認のときの確かめ、予約状態の写しの形（DE-08 と完了）、DE-09・DE-10 の監査（US-17）
- W12: 確認中を補正の承認で採用にしたとき DE-09・DE-10 を出す（US-13）
- Release 1.0 のパイロットの前: 業務の判断 (al)・(am) を業務責任者に確かめる（台帳 D-128・D-129）

## 次の Bolt

39g3（到着見込みと鮮度）。release_plan の順序のとおり 39h の前。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-11 | 初版作成 | anthropic/claude-sonnet-5-5 |

## 関連ドキュメント

- [Bolt 39g2 計画](bolt_39g2_plan.md)、[Bolt 39gc 終了報告](bolt_39gc_report.md)
- [ADR-014](../../adr/cargo-tracker/014-downstream-to-upstream-notification.md)、[ドメインモデル](../../design/cargo-tracker/domain_model.md)、[ユーザーマニュアル 7 章](../../manual/07-本予約の確定と予約一覧.md)、[9 章](../../manual/09-追跡管理.md)
