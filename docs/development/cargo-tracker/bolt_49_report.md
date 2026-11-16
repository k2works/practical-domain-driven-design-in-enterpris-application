---
type: Plan
title: "Bolt 49 終了報告 - W9 の持ち越しの返済 1（同時実行テストの診断・待ち方の共通化・死んだコードの削除）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T03:25:16Z }
---

# Bolt 49 終了報告 - W9 の持ち越しの返済 1

[Bolt 49 計画](bolt_49_plan.md)の終了報告（[W10 計画](iteration_plan-W10.md)。種別: 技術、SP 0）。人の承認待ち。

## 承認の議題（先に確かめてほしいこと）

| # | 議題 | 推奨 |
| :--- | :--- | :--- |
| 1 | **C-9 の原因は確かめられなかった。** 負荷なしで 3 回続けて流して再現せず（`/goal` で止めずに進めた）。失敗の診断（配信の記録の行）を足しただけで、待ち時間もロックの待ちの上限も変えていない。次に CI で失敗したら診断から (a)・(b) を決める。計画の P-2 は未決のまま残す | このまま閉じ、CI で再発したら再調査する（C-9 は「再現待ち」に更新） |
| 2 | 計画の前提を直した。固定の待ち 15 か所は固定の sleep ではなく、非同期の listener（DE-xx）の反映を待つ**開き直しの繰り返し**だった。画面の状態を待つ locator の待ちに置き換えられないので、共通の部品 `BrowserSession.awaitEventually`（Awaitility。10 秒の上限、呼び出したスレッドで評価）に集めた。動画の 1 か所は残した | 承認 |
| 3 | Sonar（`operating-qt`）は流していない | 次の Bolt の前に流す。人の判断が要る |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| 見積り | 150 分 + 事務 60 分 |
| 承認ゲート | P-1（`PlaceholderController` の削除）は計画で承認済み。`/goal` のもとで人の確認なしに進めた |

## 成果

- C-9: `MyBatisTrackingRecordLockIntegrationTest` の失敗の表示に、配信の記録の行（リスナー・公開・完了の日時）を足した。原因は未確定（上の議題 1）。
- C-7: `waitForTimeout` を 14 か所から外し、`awaitEventually` に置き換えた（`BookingUiSteps` 4、`RouteDesignUiSteps` 3、`RoutingUiSteps` 2、`ExternalDataIngestionUiSteps` 2、`QuotationUiSteps`・`QuotationConsultationUiSteps`・`ShipperApprovalUiSteps` 各 1）。`BrowserSession` の動画の待ち 1 か所だけが残る。`test_strategy.md` に「固定の待ちを使わない」規則と、通知の 2 回受信・KPI の 24 時間境界のテストの所在を足した。
- C-8: `PlaceholderController` と `identity/placeholder.html` を削除した。2 つの URL は `CustomerServiceCaseController`・`ShipperNotificationController` が受け、それぞれのテストが通る。`domain_model.md` の IR-INV-01 の古い記述を直し、PV-01 の除外を KPI-INV-05 として記録した（Release 1.0 では作らない）。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest` | BUILD SUCCESSFUL（8 分） |
| `uiTest`（全シナリオ） | 1 回目は 1 件失敗（`予約の状態が進むまで開き直す` の置き換えで最初の `browser.navigate(url)` を落とした。私の誤り）。直して、そのシナリオを流し直して成功。直したあとの全体の `uiTest` は、Bolt 53 のあとの全体のゲート（2026-11-16、`spotlessCheck check documentationTest uiTest`、30 分）で成功した |
| `checkstyleMain checkstyleTest spotbugsMain` | 成功 |
| SonarQube | 未実施 |

## 申し送り

- C-9 の再発の監視（CI の失敗に診断が出る）。
- `uiTest` の全体の流し直しを、W10 の締め（Bolt 56）のゲートで行う。
- 流す順を守れなかった点: 固定の待ちの置き換えを、先に失敗を見るテストなしで行った（既存のシナリオが守る。TDD の三原則から外れた）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版 | anthropic/claude-sonnet-5-5 |
