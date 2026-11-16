---
type: Plan
title: "Bolt 49 計画 - W9 の持ち越しの返済 1（同時実行テストの安定化・待ち方・死んだコードの削除）"
description: "W10 の最初の Bolt。W9 の持ち越し C-9（H2TrackingRecordLockTest の一過性の失敗）、C-7（UI の固定の待ちとテストの所在）、C-8（死んだ PlaceholderController の削除と文書の追記）を返す。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T01:44:46Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-16T01:44:46Z }
---

# Bolt 49 計画 - W9 の持ち越しの返済 1

[W10 計画](iteration_plan-W10.md)の最初の Bolt（種別: 技術、SP 0、見積り 150 分、壁時計 210 分）。依存はなし。

**状態**: 承認済み（2026-11-16、human:kakimomokuri。P-1 は推奨のとおり）。検証済み（validating-design。validating-iteration-plan は技術 Bolt のため、引用した事実の確認で代えた）。

## 課題と、確かめた事実

| 持ち越し | 確かめた事実 | 返し方 |
| :--- | :--- | :--- |
| C-9 | CI の run 43671271549 で `H2TrackingRecordLockTest` の「行37_行38_H2でも配信が重なっても発行記録が両方完了する」が 1 回失敗した。失敗の位置は `MyBatisTrackingRecordLockIntegrationTest.java:681`（`await().atMost(20 秒)` の中の `completedPublications(first) == 2`）。同じ追跡番号に 2 つの発行が非同期で重なり、H2 のロックの待ちの上限は 10 秒（行 22）。**原因は未確定**。仮説は 2 つ: (a) CI が遅く、後の配信が 10 秒でロックを諦めて例外になり、インメモリの H2 は再配信しない（`H2TrackingRecordLockTest` の行 37 のコメントのとおり）。(b) 配信の完了の記録が 20 秒に間に合わない | まず原因を確かめる（ステップ 1）。直す前に、同じ条件で失敗を再現する |
| C-7 | UI のステップで `page().waitForTimeout(250〜500)` の固定の待ちが 15 か所（`BookingUiSteps` 4、`RouteDesignUiSteps` 3、`RoutingUiSteps` 2、`ExternalDataIngestionUiSteps` 2、`QuotationUiSteps`・`QuotationConsultationUiSteps`・`ShipperApprovalUiSteps` 各 1、`BrowserSession` 1）。`BrowserSession` の 1 か所は動画の最後の一枚の待ちで意図がある。通知の「同じ契機の 2 回受信」は `MyBatisNotificationRepositoryIntegrationTest` などにあるが、W9 レビュー M-10 は所在が分からないと指摘した。KPI の 24 時間の境界は `WebInquiryEscalatorTest`（ちょうど・1 秒古い）で押さえている | 固定の待ちを、画面の状態を待つ形（Playwright の locator の待ち・`waitFor`）に置き換える。所在は、新しい文書を作らず `test_strategy.md` の「不変条件とテストの対応」（IR-INV-01〜02 の行）と「時刻と境界値」に、通知の 2 回受信と KPI の 24 時間の境界のテスト名を書いて示す。固定の待ちを使わない規則は「置き場所とステップ定義」の節に 1 行足す |
| C-8 | `PlaceholderController` は `/customer/inquiries` と `/customer/notifications` の準備中の画面を返すが、この 2 つは W9 で `CustomerServiceCaseController`（C-14・C-15）と `ShipperNotificationController`（C-16）に置き換わった。コントローラーの中の対応表はこの 2 つだけで、`staff` 側はない。テンプレート `identity/placeholder.html` も使われない | 削除する。削除の前に、2 つの URL が本物のコントローラーに届くことと、準備中の画面を前提にしたテストがないことをテストで確かめる |

文書の追記（C-8 の後半。横断整合性検証で直した）: IR-INV-01 の 24 時間の規則は `domain_model.md`（1495 行ほか）と `test_strategy.md`（274 行）にすでにある。**古いままの記述だけを直す**: `domain_model.md` 1198 行の「照会記録（IR-INV-01）は W9 で作るので、それまでの荷主の照会は…」を、W9 で作った（Bolt 42-1）と分かる書き方にする。PV-01 の除外の記録は設計文書に見当たらない（`KPI-INV-01` の行だけ）ので、除外の内容を W9 レビューと台帳（D-164〜D-172）から確かめてから、`domain_model.md` の該当の節に足す。時間ごとのジョブの ShedLock と監視は Bolt 53-1 に送る。

## 決定

| # | 決定 | 理由 |
| :--- | :--- | :--- |
| 1 | 原因を確かめる前にテストの待ち時間を延ばさない | 待ちを延ばすと、本当の原因（ロックの待ちで諦める、配信が失われる）を隠す。一過性の失敗を「許容」にしない |
| 2 | 固定の待ちの置き換えは、画面のシナリオを 1 か所ずつ流して緑を確かめながら進める | 15 か所を一度に変えると、どの置き換えが不安定にしたか分からない |
| 3 | アプリのコードは `PlaceholderController` の削除だけ。振る舞いは変えない | 技術の返済。画面の振る舞いは変わらず、マニュアルの更新は要らない |

## 確認ポイント（人に諮る）

| # | 内容 | 推奨 |
| :--- | :--- | :--- |
| P-1 | `PlaceholderController` と `identity/placeholder.html` を**削除する**（ファイル削除は確認必須） | **推奨のとおり承認（2026-11-16、human:kakimomokuri）: 削除する。URL は本物のコントローラーが受けている** |
| P-2 | C-9 の原因が (a)（CI が遅くてロックを諦める）と分かった場合、H2 のロックの待ちの上限（10 秒）を変えるか、テスト側で諦めを許すか | 原因が分かってから選択肢を示す。**仮に決めない**（未決。ステップ 1 のあとに諮る） |

スキーマ・認可・外部連携の変更はない。

## 計画（TDD の三原則は変えない）

- [ ] (1) C-9 の原因の調査: 失敗のログを読み、同じテストを CPU に負荷をかけて繰り返し流して再現する（再現しなければ「再現できなかった」と記録し、仮説 (a)(b) のどちらを支持する証拠もないと書く）。結果をこの計画に書き、P-2 を人に諮る
- [ ] (2) C-9 の修正: Red（再現するテスト、または失敗を捕まえる観測）→ 最小の修正 → Green。守る既存のテストを先に流す
- [ ] (3) C-7 の固定の待ち: `BrowserSession` の動画の待ち以外の 14 か所を、画面の状態を待つ形に置き換える。1 か所ごとに該当のシナリオを名前の絞り込みで流す
- [ ] (4) C-7 の所在: 通知の冪等性・KPI の 24 時間の境界・`KpiWeeklyQueryServiceTest` のスタブの比べ方（引数の完全一致）を調べ、直すものは直す。残りは所在の一覧に書く
- [ ] (5) C-8: 2 つの URL が本物のコントローラーに届くことの確認 → `PlaceholderController` とテンプレートの削除（P-1 の承認後）→ 文書の追記
- [ ] (6) 品質ゲート: `--continue spotlessCheck check documentationTest uiTest`、push の前に `checkstyleMain checkstyleTest spotbugsMain`、Sonar、`okf:check`

見積り（分）: (1) 40、(2) 30、(3) 40、(4) 20、(5) 20 = 150。事務の 60 を足して 210。(1) の再現が長引くと (2) を超える（仮定。実測の根拠なし）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版（draft） | anthropic/claude-sonnet-5-5 |
