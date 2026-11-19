---
name: project-session-warning-review
description: Bolt 32b の session の警告（dialog・role=alert の期限の到来の案内）のレビューで見た論点。時間制限の UI を見るときに使う
metadata:
  type: project
---

session の警告は `<dialog>` + showModal で、モーダルのダイアログはこの用途だけの例外（ui_design.md「session の警告（Bolt 32b）」）。2026-10-14 のレビューで挙げた論点:

- 延長の失敗の文言を hidden の切り替えだけで出すと支援技術に伝わらない（4.1.3）。差し込み型の alert か、ダイアログ内のライブリージョンにする
- 「閉じる」・Esc の後は同じ期限で二度と開かず、延長の手段が画面に残らない
- 期限の到来の案内は position: fixed で上部に重なる。2.4.11 Focus Not Obscured と、DOM の末尾にあるのでリンクがタブ順の最後になる点
- `.app-session-warning` の max-width が UA の `calc(100% - 2em - 6px)` を上書きし、狭い幅で溢れうる（1.4.10）
- bfcache で戻った画面は lastRequestAt が古いまま（pageshow で localStorage を読み直す）

**Why:** 時間制限の UI は Bolt 32e（入力の保持）でも触るので、同じ論点を繰り返さないため。
**How to apply:** 32e のレビューで上の点が直ったかを先に確かめる。[[project-form-review-checklist]] とあわせて使う。
