---
name: project-session-input-a11y
description: cargo-tracker の入力の控え（Bolt 32f）の知らせ・案内の a11y レビューで見つけた論点（live region の時機、reset 後のイベント、文言の事実との一致）
metadata:
  type: project
---

Bolt 32f（2026-10-14 レビュー）の入力の控えは、main の前の空の aria-live="polite" 領域に読み込み直後に文言を入れ、フォーカスは移さない設計。

指摘した論点（次の Bolt で直ったか確かめる）:
- 読み込みと同時の live region 更新は NVDA/VoiceOver で落ちやすい。少し遅らせる案を出した
- 「戻した入力を消す」は form.reset() だけで input/change を出さず、C-03 の貨物種別の案内・提出の aria-disabled が追随しない
- 「ログアウトの前の入力」「ログインし直した後に戻します」は session が生きていた場合と事実が合わない
- A-03 の一文は 8 時間・submit の破棄条件を確かめずに出る
- 領域が landmark の外（main の前）

**Why:** 用語「控え」と「下書き」を区別する方針（ui_design.md）と、文言を事実に合わせる方針（Bolt 32e レビュー）がこのチームの慣習。
**How to apply:** session 系の UI をレビューするときは、文言が session の生死の両方の場合に正しいか、プログラムで値を変えた後にイベントが出るかを見る。[[session-warning]]
