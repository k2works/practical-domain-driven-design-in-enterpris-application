---
type: Plan
title: "Bolt 40-9 終了報告 - 受領期限の超過の判定と依頼元への表示（escalation）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T12:00:00Z }
---

# Bolt 40-9 終了報告 - 受領期限の超過の判定と依頼元への表示（escalation）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **検出の方式は W8 計画の推奨どおり「読むときの判定」**: 受付済みで受領期限を厳密に過ぎたものと、上位対応中を「期限超過」とする（`ServiceCase.isReceiptOverdue`）。受領した案件は期限を過ぎても超過にしない。**定期処理による上位対応中への書き込み**と、**上位の運用責任者への通知（OBS-05。運用チャンネル）は W10**に送った。W8 の時点では、受付済みで期限を過ぎた案件は状態が「受付済み」のまま（一覧には出る）で、「上位対応中」への遷移を書く道は用意していない（`escalate` は集約にあるが、呼ぶ手段は W10） | 承認 | 計画 判断 (1)(2) |
| 2 | **US-20 AC3 は一部だけ満たした**: 「上位の運用責任者へ escalation され」は一覧（`ServiceCaseOverdueQueryService`）で見える状態にしたが、**通知は届かない**（W10）。「依頼元へ担当未確定と次回更新日時が示される」は `RequesterNotice` の値で用意し、画面での表示は 40-10・40-15。**US-20 の受入条件の照合（40-16）に残す** | 承認 | 申し送り |
| 3 | **テスト用の共有フェイクを別ファイルへ**: `InMemoryServiceCaseRepository`・`CountingServiceCaseNumberIssuer`（テストのソースの `domain.model.aggregates`）。パッケージ外から使えず、一度ビルドが落ちて切り出した | 承認 | — |
| 4 | **規律の違反（私の側）**: `python3 -I - <<EOF` という標準入力につながる形をまた 1 回書いた（空入力で即終了し実害なし）。W8 でこれまでに計 5 回。今後はファイル編集のツールだけを使う | 承認 | — |
| 5 | **品質ゲートで直したもの**: 用語集（`依頼元への表示`）、Sonar の指摘（空の Optional の確かめ）。`ServiceCaseAuthorization` を公開にした（照会サービスが使う） | 承認 | — |
| 6 | **スキーマ・認可の新しい定めはない**（認可は D-159 の運用役割）。`uiTest` は流していない（画面は触れない） | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-9 回（W8 の 10 本目） |
| 期間 | 2026-11-13 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 180 分。**実績は約 100 分（概算。時刻を厳密には記録していない）。差は約 −80 分** |
| GitHub | 変えていない |
| 計画 | [Bolt 40-9 計画](bolt_40-9_plan.md) |

## 成果

`ServiceCase.isReceiptOverdue(now)`・`requesterNotice()`、`RequesterNotice`、`ServiceCaseRepository.findReceiptOverdue`（MyBatis 実装。受付済みで期限を厳密に過ぎたものと上位対応中を期限の古い順に）、`ServiceCaseOverdueQueryService`・`OverdueServiceCase`、`TrackingConfiguration` の Bean。テスト: 集約の行 14・15、`ServiceCaseOverdueQueryServiceTest` の行 1〜5（時計を固定）、PostgreSQL の抽出。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest`（`uiTest` は流さない） | 1 回目は用語集で落ち、直した。**2 回目は BUILD SUCCESSFUL（8 分 35 秒）** |
| SonarQube | 1 回目はテストの指摘 2 件で FAIL。直して **2 回目は PASS**（新しいコードのカバレッジ 96.3%、重複 0.016%、違反 0） |

## 完了条件

- [x] 行 14・15、照会の行 1〜5、抽出が緑
- [x] `check documentationTest` と SonarQube が緑
- [x] 見積り／実績／差を書いた

## 申し送り

- W10: 定期処理（上位対応中への書き込み）と運用チャンネルへの通知（OBS-05）、取込がない日の通知などと一緒に作る。
- Bolt 40-10: カスタマーサポートの照会（US-11 AC1・AC3）。`requesterNotice` を使う。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri。「以降 Recommended で自律実行して」の指示） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
