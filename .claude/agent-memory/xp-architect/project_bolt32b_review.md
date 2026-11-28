---
name: bolt32b-review
description: Bolt 32b（session の失効の警告と延長、ACC-06）のアーキテクトレビューで残した指摘
metadata:
  type: project
---

Bolt 32b（2026-10-14 レビュー）の指摘。高はなし。

- request の属性の名前を 2 つの定数で持つ形が 2 組目になった（登録を始めた時刻のキーに続く）。Bolt 33b で直す。直し方の候補は、名前を identity の両層が依存できる場所に 1 つ置くこと（例: SessionLifetime の方針を application 側へ）
- 共通のレイアウトの head の script（条件 sessionTimer）と body の警告（条件 currentActor and sessionTimer）で条件がずれ、customer・staff に 2 回ずつ写している。部品 session-warning.html に script も持たせる案を出した
- 延長について、止められた利用者（SessionRevalidationFilter）の延長が A-03 になるテストがない。全 URL の見張り（AuthenticationSecurityIntegrationTest）は /session/extend を自動で拾う
- ブラウザの期限の起点は DOMContentLoaded（応答の時刻より遅い）。サーバーより遅い側にずれる。延長の 302 で救えるので低
- 別のタブで再ログインした後の古いタブは、古い CSRF トークンで 403 になり続け、古い 8 時間の期限で数え続ける

**Why:** 33b で定数の直しが実際に入るか、部分更新（hx-*）を足す Bolt で JS が数える request を足すかが後で効く。
**How to apply:** 33b と、htmx の部分更新を足す Bolt のレビューで確かめる。[[bolt32c-review]] と並べて見る。
