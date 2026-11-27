---
type: Plan
title: "Bolt 44 計画 - KPI-02 の記録（US-21 AC2・AC3）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-15T09:00:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T10:08:31Z }
---

# Bolt 44 計画 - KPI-02 の記録（US-21 AC2・AC3）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-21 の最初。SP は US-21 を締める Bolt 44-2 で数える。見積り 180 分）。**スキーマの変更**（索引 1 つ）と、**モジュール構造の変更なし**。

## 人の決定（2026-11-15。AskUserQuestion の選択。human:kakimomokuri）

US-21 全体の計画（KPI-02 の記録・週次の照会・基準値・未完了の発行の件数。スキーマは `inquiry_record` の索引 1 つと `kpi_baseline` 表の新設）に対し、「**監査担当者だけが S-22 を使う**（推奨）」を選んだ。台帳 D-170〜D-172。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | IR-INV-01 の 24 時間の規則: 顧客 Web の照会の後 24 時間以内（ちょうどを含む）に同じ荷主企業・同じ追跡番号の有人案件が起票されたら、その照会を有人対応へ移ったと更新する（`WebInquiryEscalator`、`InquiryEscalation`。起票の共通の手順 `ServiceCaseIntake` が保存の後に呼ぶ） |
| 2 | 荷主が Web で再開を依頼したら、Web の問い合わせ（WEB_INQUIRY。受付番号つき）として記録する（`WebInquiryRecorder.recordReopen`。C-15 の再開の後） |
| 3 | 照会記録のリポジトリに、探索（`findUnescalatedWebSelf`）と更新（`escalate`）を足す。索引 `ix_inquiry_record_tracking_received`（追跡番号・受付時刻）を足す |
| 4 | テスト: `WebInquiryEscalatorTest`（境界を含む）、`ServiceCaseOpenEscalationTest`、`WebInquiryRecorderTest`、永続化の統合テスト |

## 決定（推奨のとおり）

- 24 時間・同じ予約という解釈は KPI-02 の算式の解釈で、業務責任者への確認事項（D-170。仮）。
- 起票の経路は問わない（利用者の起票・システムの起票とも、共通の手順から更新する）。

## 計画

- [x] (1) テストを先に書き失敗を確かめた
- [x] (2) 実装・スキーマ（索引）・文書
- [x] (3) 品質ゲート・Sonar（44-2 でまとめて流す）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-15 | 初版 | anthropic/claude-sonnet-5-5 |
