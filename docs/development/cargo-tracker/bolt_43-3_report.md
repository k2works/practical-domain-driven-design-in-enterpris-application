---
type: Plan
title: "Bolt 43-3 終了報告 - 荷主の通知一覧（C-16。US-22 を締める）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T14:00:00Z }
---

# Bolt 43-3 終了報告 - 荷主の通知一覧（C-16。US-22 を締める）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **スキーマの変更（索引 1 本）**: `ix_notification_recipient`（移行 `V20261122100000`）。新しい列・表はない。この Bolt の着手前に**個別の承認は受けていない**（「以降、推奨で自律実行」の指示による）。42-1 の索引と同じ種類の最小の変更なので推奨で進めた。不要なら差し戻してよい | 承認 | [計画](bolt_43-3_plan.md) (3) |
| 2 | 通知モジュールの依存に `platform :: web`（日時表示の部品）を足した | 承認 | 計画 (2) |
| 3 | 荷主に出すのは本人宛ての荷主への業務の通知だけ。失敗した通知は宛先を示し、技術的な理由は示さない | 承認 | 計画の決定 |
| 4 | US-22 の 3 SP を数える（AC1〜AC4 を画面の層のシナリオで通した。AC1〜AC3 の契機はイベントを受ける入口へ与える形で、イベントの配信自体の確かめは統合テスト）。GitHub の Issue #23 のクローズは人の許可が要るので実行していない | 承認 | — |
| 5 | **作業の流儀（私の側）**: 標準入力につながる形（空の heredoc）をまた 1 回書いた（何も読まず何も変えていない。これまでの 2 回と合わせて 3 回目）。以後は Edit/Write だけを使う | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 43-3 回（W9。機能。US-22 を締める） |
| 期間 | 2026-11-14 |
| SP | **3**（US-22） |
| 見積り／実績 | 見積り 180 分。実績は概算 120 分 |
| 計画 | [Bolt 43-3 計画](bolt_43-3_plan.md) |

## 成果

C-16（`ShipperNotificationController` と `notification/customer/list.html`）、`NotificationQueryService`、`findRecentByRecipient`（MyBatis と見本）、索引の移行、UI シナリオ 4（`shipper_notification_ui.feature`。AC1〜AC4）、マニュアル 15 章（キャプチャ 1 枚）、受入条件の照合の表に US-22 の 4 行、ui_design の C-16。テスト: `ShipperNotificationControllerTest` 4、`NotificationQueryServiceTest` 3、`MyBatisNotificationRepositoryIntegrationTest` に 2、UI シナリオ 4。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | 1 回目は `uiTest` の 1 シナリオ（`login_ui.feature` の「まだ作っていない画面をナビから開くと準備中と示される」。荷主のナビの画面が全部できて、準備中の例がなくなった）で失敗（31 分 22 秒。ほかは通った）。そのシナリオを外した（準備中の仕組みの `PlaceholderController` は使われなくなった。削除は技術的負債として W9 の締め（Bolt 48）に送る）。流し直しは `spotlessCheck documentationTest` だけで、`uiTest` の全件は流し直していない（外したのはシナリオ 1 件の削除だけ） |
| SonarQube | **PASS**（新しい違反 0、カバレッジ 95.9%。1 回目は 1 件を指摘され、直して流し直した） |

## 申し送り

- 通知の配信設定・静穏時間・代替チャネルは第 2 段階（UC-13）。
- 次は Bolt 44（US-21 KPI-02 の記録。スキーマのゲート）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
