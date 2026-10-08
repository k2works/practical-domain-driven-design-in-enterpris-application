---
name: project-bolt21-kpi-first-presentation-tests
description: Bolt 21（KPI 計測記録の最初の提示時刻・KPI-01 リードタイム）のテストレビューで見つけた未解消点（2026-10-08）
metadata:
  type: project
---

Bolt 21 のレビュー（2026-10-08）で指摘した、テストの未解消点。

- InMemoryKpiObservationRepository の findByTransportRequestId が保存している実体をそのまま返す（エイリアス）。listener が集約を変えた時点で store も変わるため、listener から saveFirstPresentation を呼ぶのをやめても、listener の単体テストとメモリで動く受入テストは通る。保存の呼び出しを確かめているのは PostgreSQL の配信の統合テストだけ
- 「再び届いても変わらない」受入シナリオは同じ時刻の再配信なので、「最初が勝つ」と「最後が勝つ」を見分けられない（見分けているのは再見積りのシナリオ）
- 契約テストに、計画にあった「同じ時刻」のケースがない。DB の CHECK（>=）のちょうど同時刻のケースもない（Bolt 6〜8 と同じ型の抜け）
- DE-03 の統合テストは leadTime >= 0 しか見ていない（値を見積りの提示時刻と照合しない）。提出の記録がない DE-03 で発行の記録が未完了のまま残ることは統合テストで確かめていない
- 提出時刻より前の提示時刻は直らない拒否なのに例外を投げて未完了に残すため、起動のたびに再配信される毒メッセージになりうる

**Why:** メモリのテストダブルが本物と違う参照の意味を持つと、単体・受入の層の安全網が空振りする。
**How to apply:** 次に identity や他のモジュールのメモリのリポジトリを触るレビューでは、取得が写しを返すか確かめる。KPI の W9（週次の値・PV-01）のレビューで、これらが直ったかを見る。関連: [[project-bolt20-shipper-approval-tests]]、[[project-bolt6-8-review-tests]]
