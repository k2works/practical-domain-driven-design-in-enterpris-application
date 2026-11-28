---
name: project-bolt9-10-quotation-tests
description: Bolt 9〜10（見積りの算出・提示、書類の差し替え D-25）のテストレビューで見つけた抜け（2026-10-05）
metadata:
  type: project
---

Bolt 9〜10 のテストレビュー（git diff 5c3bdfe..e930c9e）で指摘した未解消点。

- 金額の上限がドメインにない（DB は NUMERIC(15,2)）。13 桁超・合計のあふれが 500 になりうる
- 算出と提示を同じ時刻で行うため、提示時刻が算出時刻と取り違えられても AC1 のシナリオ・QuotationTest・MyBatis テストが通る
- 提示時に有効期限切れかを確かめない（承認待ちのまま期限を過ぎても提示できる）。Q-INV-17 に規則なし、PO 確認事項
- 同時の算出は UK で弾くが DuplicateKeyException を結果に変えていない
- 出し直しの違反（多すぎる）の受入シナリオが D-25 の差し替えで置き換わり消えた（単体の RequiredDocumentPolicyTest だけ）
- 単体の Red はコンパイルの失敗だけで確かめている（境界 isAfter はミューテーションで補う提案）

**Why:** 次の Bolt（11: 失効・置換）で同じ領域を触るため、時刻と期限の検証が積み重なる。
**How to apply:** Bolt 11 のレビューでは、時計を進めて算出・提示・失効の時刻を区別しているかを最初に見る。[[project-bolt5-review-tests]] の楽観ロック 1 トランザクション問題も MyBatisQuotationIntegrationTest で再発している。
