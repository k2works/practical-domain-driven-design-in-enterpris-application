---
type: Plan
title: "Bolt 40-10 終了報告 - カスタマーサポートの照会（S-11・S-12 の照会のみ）と「同じ値」の結果の文"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T01:00:00Z }
---

# Bolt 40-10 終了報告 - カスタマーサポートの照会（S-11・S-12 の照会のみ）と「同じ値」の結果の文

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **認可の変更（`SecurityConfiguration`）**: 追跡の一覧（S-11）と詳細（S-12）の GET を、追跡管理者に加えて**カスタマーサポートにも開いた**。実績の登録の画面・登録・採用など、それ以外は追跡管理者だけのまま（カスタマーサポートが叩くと 403）。担当企業の範囲（US-16 AC4）はカスタマーサポートにもそのまま効き、範囲外は 404。システム管理者・営業担当者などは開けない。セキュリティの統合テスト（PostgreSQL・本物のフィルター）で確かめた | 承認 | 計画 決定 (1) |
| 2 | **`shown_to_customer` の列は足さない（スキーマの変更なし）**: 顧客に出るのは採用済みの実績だけ（`customerView`）なので、S-12 の各実績の「顧客への表示」（採用済み＝表示する、下書き・確認中＝表示しない（社内の確認状態））は状態から決まる。data_model :809 の「US-11・US-13 で足す」という予定を見直した。**US-13（実績の訂正）で別の理由で列が要る場合は、その Bolt で諮る** | 承認 | 計画 決定 (2) |
| 3 | **US-11 の受入条件の達成は一部**: AC1（顧客に開示できる情報と社内の確認状態を区別して示す）は S-12 で達成。AC3（予約の代理確定・変更は許可されない）は、追跡の画面に予約の操作がなく、カスタマーサポートは実績の登録・採用もできないことで満たす。**AC2（担当部門へ引き継いで受付番号を発行する）は画面（S-14・S-15・S-23）が要るので 40-11・40-15 で満たす** | 承認 | 申し送り |
| 4 | **「同じ値」の結果の文を分けた**（Bolt 39gc の議題 3）: 採用の POST で同じ値の実績が確認中なら「確認中の実績 M と同じ値です。採用は要りません。」（`SameAsUnderReview`）。以前は「採用済みの実績」と誤って示していた。ユースケース層の結果の型に 1 つ足した | 承認 | 計画 決定 (4) |
| 5 | **マニュアル 9 章と ui_design を同じ Bolt で更新した**（画面の文章の追加。キャプチャは変わらない）。**`uiTest` を流した**（画面に触れたため。2026-11-13 の Bolt 40-5 の CP-2 の規約） | 承認 | — |
| 6 | **作業の流儀（私の側）**: 標準入力につながる形を、この Bolt でも無害な形で 1 回書いた（`python3 -c "print(1)" >/dev/null`。入力は待たない）。TDD は Red を確かめてから実装した（画面の行 1・3、セキュリティの CS の 2 件） | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-10 回（W8 の 11 本目） |
| 期間 | 2026-11-13〜11-14 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 170 分。**実績は約 120 分（概算。時刻を厳密には記録していない）。差は約 −50 分**。`check documentationTest uiTest` は 26 分 55 秒、Sonar は約 10 分 |
| GitHub | 変えていない |
| 計画 | [Bolt 40-10 計画](bolt_40-10_plan.md) |

## 成果

`SecurityConfiguration` の追跡の画面の認可、`TrackingRecordViews.MilestoneView.customerDisplay`、`TrackingRecordController` の `canOperate`、`show.html`（顧客への表示・照会のみ・操作の出し分け）、`MilestoneAdoptionOutcome.SameAsUnderReview` とコントローラーの文。テスト: 画面の Bolt 40-10 の行 1〜3 と行 53b、`TrackingRecordCommandServiceTest` の行 53c、`TrackingAssignedScopeSecurityIntegrationTest` のカスタマーサポート 2 件。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest uiTest`（画面に触れたので `uiTest` も流した） | BUILD SUCCESSFUL（26 分 55 秒） |
| SonarQube | **PASS**（新しい違反 0） |

## 完了条件

- [x] 上のテストが緑
- [x] `check documentationTest uiTest` と SonarQube が緑
- [x] マニュアルと設計文書を同じ Bolt で更新した
- [x] 見積り／実績／差を書いた

## 申し送り

- Bolt 40-11: S-14（有人案件の一覧。未受領・期限超過・対応中の絞り込み）と S-15（詳細の表示と受領の操作）。`ServiceCaseOverdueQueryService`（40-9）と `ServiceCaseCommandService.receive`（40-7）を使う。アウトサイドイン（受入シナリオ先）、htmx/PRG、マニュアル。
- US-11 AC2（担当部門へ引き継いで受付番号を発行）は 40-11・40-15 の画面で満たす。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri。「以降 Recommended で自律実行して」の指示） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
