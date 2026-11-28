---
name: project-bolt39g4c-plan
description: Bolt 39g4c 計画（見込みの根拠）の判断と検証で効いた指摘 — 根拠は保存、見込みと根拠を 1 項目で持つ、値の一致を CHECK で縛る、テストが導出の経路を通るか
metadata:
  type: project
---

Bolt 39g4c（S-12 に見込みの根拠の 1 行）の計画は 2026-11-12 に draft で作った（承認待ち）。推奨: 根拠は種類（ORIGINAL_ETA・MILESTONE・VOYAGE_ARRIVAL）と実績番号で保存、規則が LatestEstimate（時刻と根拠）を 1 回の評価で返し、集約も 1 項目で持つ（DerivedStatus の前例）。【前の決定の変更】(24)・(ax)・D-140 を使った。

**Why:** 検証で効いた指摘が型として再利用できる。(1) 「下書きの受け取り」は withMilestones(…, current()) で導出し直さないので、そこだけ通すテストは導出側の変異を落とさない。(2) listener 経由のテストは集約の判定が照会の変異を隠す。(3) 保存の理由が「正を 1 つ」なら、参照の存在だけでなく値の一致（latest_eta = original_eta 等）も不変条件と CHECK で縛る。(4) 画面で「見込み − 当初」を「遅れ」と呼ぶと規則の加算の形を写す。集約が出す値を「当初より … 遅れ」と書く。

**How to apply:** 追跡記録の導出を伴う計画では、テストの組が derivedWith / receiveVoyageArrival を実際に通るかを先に確かめる。reconstitute の形は 5 つになり負債（[[project-bolt39g4-plan]]）。

関連: [[project-bolt39g4-plan]]、[[project-bolt39g3-plan]]
