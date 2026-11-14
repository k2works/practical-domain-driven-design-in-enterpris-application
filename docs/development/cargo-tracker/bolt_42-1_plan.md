---
type: Plan
title: "Bolt 42-1 計画 - 顧客 Web の追跡照会の自動記録（IR-INV-01）と case_number の索引"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T08:00:00Z }
---

# Bolt 42-1 計画 - 顧客 Web の追跡照会の自動記録（IR-INV-01）と case_number の索引

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-23・US-21 の下ごしらえ。SP 0。見積り 200 分）。**スキーマの確認は人が済ませた**（2026-11-14。AskUserQuestion の回答「この計画で進める」。human:kakimomokuri）。

## 範囲

| # | 内容 | 備考 |
| :--- | :--- | :--- |
| 1 | `inquiry_record.case_number` の索引 `ix_inquiry_record_case_number`（移行 `V20261121100000`。列・表の追加はない） | 人が承認済み |
| 2 | C-10 の照会の結果（`/customer/tracking-records/{追跡番号}`）を開いたとき、顧客 Web の自己照会（`WEB_SELF`）を `WebInquiryRecorder` が記録する。同じ利用者・同じ追跡番号の 30 分以内の再表示は 1 件にまとめる（30 分ちょうどは別の照会） | IR-INV-01 の前半 |
| 3 | `InquiryRecordRepository.existsWebSelfAfter`（まとめの判定。指定の時刻より後を数える） | 追加のポート |

## 範囲に入れないもの（理由つき）

- **`WEB_INQUIRY`（顧客 Web の問い合わせ）の自動記録**: 荷主が Web から問い合わせを出す画面がまだない（C-14・C-15 は一覧と再開だけ）。出す画面を作る Bolt で一緒に記録する。
- **「照会の後 24 時間以内の問い合わせ → 有人対応へ移った」の規則**: KPI-02 の算式の解釈で、プロダクト責任者の確認が要る（domain_model の注）。Bolt 44（KPI-02 の記録）で、仮の決定として台帳に残して作る。
- 索引は `case_number` の 1 本だけ（まとめの判定用の索引は、件数が少ないうちは足さない）。

## 決定（推奨のとおり。人にしか決められない判断は含まない）

照会の記録に失敗したら照会の結果の表示も失敗する（同じトランザクションの外の別のトランザクションで握りつぶさない。バグを隠さない）。

## 計画

- [x] (1) テストを先に書き、失敗を確かめた（`WebInquiryRecorderTest` 4 件はコンパイルエラー、`CustomerTrackingControllerTest` の行は「呼ばれていない」で失敗）
- [x] (2) 実装、統合テスト（PostgreSQL。まとめの判定の境界、索引の存在）
- [x] (3) 品質ゲート・Sonar・文書（data_model、domain_model）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
