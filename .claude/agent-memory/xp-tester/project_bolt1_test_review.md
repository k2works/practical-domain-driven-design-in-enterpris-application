---
name: bolt1-test-review
description: cargo-tracker Bolt 1（2026-10-01）のテストレビューで見つけた、テストの信頼性・戦略との乖離の要注意点。後続 Bolt のレビューで解消を確認する
metadata:
  type: project
---

2026-10-01 に apps/cargo-tracker の Bolt 1（ウォーキングスケルトン）をテスター視点でレビューした時点の未解消の論点。

- ADR-007 の「Clock をマイクロ秒に切り捨てる」が ClockConfiguration に未実装で、テストも無い（統合テストは事前に切り捨てた値しか使っていない）
- イベント配信の統合テストは非トランザクションで DB にデータを残し、待ち条件が `!findAll().isEmpty()`。発行記録の完了確認は待たずに検証しており競合の余地がある
- 受入シナリオの組み立てで MutableClock とメモリ上のリポジトリが singleton（シナリオ間で状態が漏れる）
- H2 のスモークテストが無い（H2 は developmentOnly で test クラスパスに無い）。H2 用マイグレーションは手動 bootRun でしか確かめていない
- メモリ上のリポジトリは put（上書き）、MyBatis は INSERT（PK 違反）で振る舞いが違う。契約テストが無い

**Why:** Bolt 2 以降で受入シナリオと統合テストが増えると、これらがフレーキーや空振りの原因になる。
**How to apply:** 次の Bolt のレビューでは、まずこれらが解消されたか確認する。解消済みならこの記録を消す。関連: [[cargo-tracker-test-strategy]]
