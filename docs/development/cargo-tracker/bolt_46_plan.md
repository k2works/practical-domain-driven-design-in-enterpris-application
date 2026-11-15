---
type: Plan
title: "Bolt 46 計画 - 荷主の見積りの辞退（US-24 AC2。D-86・ADR-018 の見直し）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-15T01:00:00Z }
---

# Bolt 46 計画 - 荷主の見積りの辞退（US-24 AC2）

[W9 計画](iteration_plan-W9.md)の機能 Bolt（US-24 AC2。SP は US-24 を締める Bolt 46-1 で数える。見積り 220 分）。**構造の判断**（D-86・ADR-018 の見直し、CP-4）と**スキーマの変更**を含む。

## 人の決定（2026-11-15。AskUserQuestion の選択。human:kakimomokuri）

選択肢は 3 つ（失効として記録し再見積りしない／失効として記録し営業が再見積りできる／新しい状態「辞退済み」を足す）。**「失効として記録し再見積りしない」（推奨）を選んだ**。台帳 D-168。選択肢の説明にスキーマの変更（DB スキーマの変更）を含めて示したので、この選択を列の追加の承認として扱う。

## 範囲

| # | 内容 |
| :--- | :--- |
| 1 | ドメイン: `Quotation.decline(respondent, reason, at)`（提示済み・期限前だけ。理由は 1〜500 文字。失効に遷移し、回答者・回答時刻・理由を残す。イベントは出さない）、`isDeclined`・`declineReason`、`reconstitute` に理由を持つ形 |
| 2 | 応用: `DeclineQuotationCommand`・`DeclineOutcome`・`QuotationResponseService.decline`（理由の検証、荷主企業で絞る照会） |
| 3 | スキーマ: 列 `decline_reason`（VARCHAR(500)）、回答の値に `DECLINED`、CHECK `ck_quotation_declined`（辞退は理由を持ち失効）。**回答者・回答時刻は既存の列を使う**（人が選んだ「3 列」より 1 列少ない。V20261006100000 が予告した形） |
| 4 | 画面: C-05 に辞退の欄（`POST …/response/decline`）、C-04 に辞退の表示、S-04 に辞退の案内（再見積りの操作なし） |
| 5 | テスト: 集約・サービス・永続化・コントローラー・業務ルールのシナリオ（`decline_quotation.feature`）・画面のシナリオ（`@US-24-AC2`）。文書: ui_design（C-05）、domain_model（DE-17 は R1.0 で発行しない）、data_model、user_story、ADR-018、test_strategy、マニュアル 4 章、台帳 D-86・D-168 |

## 決定（推奨のとおり。人にしか決められない判断は含まない）

- 辞退した見積りが再見積りできないのは、状態が失効（EXPIRED）なので既存の規則（`requoteRejectionAt` の失効の記録）がそのまま拒むため。新しい規則は足さない。
- 辞退は輸送要求を変えない。見積依頼が「見積提示済み」のまま残ることは、営業が S-04 の案内で気づき、荷主と相談して出し直す運用とする（業務責任者への確認事項。D-168）。

## 計画

- [x] (1) テストを先に書き失敗を確かめた（集約・サービス・コントローラー）
- [x] (2) 実装・スキーマ・画面・文書
- [x] (3) 品質ゲート・Sonar（Bolt 46-1 とまとめて流す）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-15 | 初版 | anthropic/claude-sonnet-5-5 |
