---
name: cargo-tracker-review-points
description: cargo-tracker のドキュメント・画面レビューで繰り返し見るべき論点（用語の併記、報告書の数値の検証方法、README 不在）。Bolt 1（2026-10-01）時点
metadata:
  type: project
---

Bolt 1（2026-10-01）のレビューで見つかった、以降の Bolt でも再発しやすい論点。

- 顧客向け画面は「見積依頼（輸送要求）」を併記する決まり（ui_design の画面オブジェクト節）。テンプレートでは「輸送要求 ID」だけが出がち
- UI 設計の共通部品（エラー要約、日時表示は利用者のタイムゾーン主）を骨格の画面が満たさないことがある。満たさないなら「Bolt n では省略」と設計か計画に書かせる
- 場所（UN/LOCODE）は大文字だけを受け付ける。小文字入力の扱い（正規化するか、メッセージで直し方を示すか）が UI 側で抜けやすい
- 報告書のテスト件数は `apps/cargo-tracker/build/test-results/test/*.xml` の tests 属性で突き合わせられる（Bolt 1 は 31 件で一致）
- apps/cargo-tracker に README がなく、docs/operation/cargo-tracker も空（2026-10-01 時点）。起動手順・前提（JDK 25、Docker）・画面の URL の置き場を毎回確認する

**Why:** AI が自律実行で作る成果物は、設計書の横断ルール（併記・共通部品）を骨格の段階で落としやすく、報告書もコードの後追いになりやすい。
**How to apply:** Bolt のレビューではまずこれらを確認し、解消済みなら本メモを更新・削除する。関連: [[cargo-tracker-doc-conventions]]
