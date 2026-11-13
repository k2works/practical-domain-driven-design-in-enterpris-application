---
type: Plan
title: "Bolt 40-2 計画 - 版の逆転の隔離の採用を DE-29 と監査の要約に残す（M5）"
description: "W8 の 3 本目（技術、SP 0、100 分）。版の逆転の隔離を採用したことと、比べた採用値の外部版を DE-29 と監査の要約に残す。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T04:00:00Z }
---

# Bolt 40-2 計画 - 版の逆転の隔離の採用を DE-29 と監査の要約に残す（M5）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-2 回（W8 の 3 本目。[W8 計画](iteration_plan-W8.md)） |
| 状態 | 計画（**承認済み**。2026-11-13 に「Recommend 承認で進めて」（human:kakimomokuri）により、推奨のとおり承認として記録） |
| 種別・SP | 技術・SP 0 |
| 見積り | **100 分**（作業のみ）。類推元は Bolt 39g4b（123 分）より小さい |
| トレース | [W7 完了報告書](iteration_report-W7.md)の週全体レビュー M5。理由の必須化は D-151 の確認の場で諮る（この Bolt では足さない） |
| 承認ゲート | 計画と終了報告で止める。スキーマ・認可・外部連携は変えない |

## Bolt ゴール

版の逆転になる隔離（今の取得時刻が、この外部 ID の最後に取得した採用値より前。D-142）をデータ責任者が採用したとき、DE-29 と監査の要約に「上書きした」ことと「比べた採用値の外部版」が残り、後から「誰が・いつ・どの版と比べて戻したか」を追える。逆転でない判断の要約は今までの形のまま。

## 状態の組み合わせ

| 行 | 状況 | 期待 | 確かめ方 |
| :--- | :--- | :--- | :--- |
| 1 | 逆転の隔離を採用する | DE-29 が `versionReversed=true` と最後に取得した採用値の外部版を運ぶ | `ReceivedRecordDecisionServiceTest`（Bolt 39h3 の行 5 a の拡張） |
| 2 | 逆転でない採用・却下 | DE-29 は `versionReversed=false`、外部版は null（今までの形） | 既存の `containsExactly` |
| 3 | 監査の要約（逆転の採用、理由あり・なし） | 理由の部品の前に `;versionReversed=true;comparedExternalVersion=…` | `ExternalRecordAuditEventHandlerTest` |
| 4 | 逆転で比べた外部版がない DE-29 | 書かずに ERROR のログ（内容の欠け） | 同上 |
| 5 | 保存済みの古い DE-29 の JSON | `versionReversed` を欠いても偽として復元できる | `DomainEventSerializationContractTest` |

## 開始準備の決定

(1) **互換**: DE-29 は発行記録の JSON に保存される。足す項目は後方互換にし、`versionReversed` は `Boolean` で受けて欠けを偽にする（Jackson はプリミティブの欠けを拒むため）。JSON の形を固定する契約テストは、現在の形（V2）に更新する。【前の決定の変更】DE-29 の形の固定を V1 から V2 に更新する。

(2) 判定は差異の規則と同じ定義（`DiscrepancyRule.isVersionReversed`・`partnerOf`）を使い、**採用値を足す前に**判定する。採用は止めない（警告は表示だけ。Bolt 39h3）。

## ステップ

| ステップ | 内容 | 分 |
| :--- | :--- | ---: |
| 1 | Red: 監査の要約・サービスの DE-29・契約の各テスト | 30 |
| 2 | Green: イベントの 2 項目、サービスの判定、監査の部品 | 30 |
| 3 | 設計文書（domain_model・data_model）、品質ゲート | 40 |

## 完了条件

- [ ] 行 1〜5 が緑
- [ ] `check` と SonarQube が緑
- [ ] 終了報告に見積り／実績／差を書いた

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
