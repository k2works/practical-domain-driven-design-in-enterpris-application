---
name: bolt2-quality-gate-review
description: cargo-tracker Bolt 2（CI・JaCoCo・ArchUnit・AT-06・SonarQube）の品質ゲートをテスター視点でレビューした時点（2026-10-02）の未解消の空振り・抜け道。後続 Bolt で解消を確認する
metadata:
  type: project
---

2026-10-02 に Bolt 2（c606428..48bb848）の品質ゲートをレビューした時点の論点。Bolt 1 の指摘（Clock の切り捨て、配信テストの待ち、ScenarioReset、契約テスト、H2 スモーク）はこの時点で解消済みと確認した。

- `sonar-local:gate` は status が OK でなくても done() で成功終了する（FAIL を表示するだけ）。解析の反映待ちも無く、前回の結果を読む余地がある
- JaCoCo の閾値は対象が 0 件（分母 0）だと NaN で黙って通る。application の分岐は 0 件、domain の分岐は shared/domain の 4 件だけで、分岐の閾値は実質空振り
- カバレッジは test タスク 1 本の集計で、受入シナリオと統合テストがユニットの不足を隠す
- AT-02 は interfaces → domain（リポジトリを含む）を許し、platform・ルートのクラスは層の外で無検査
- テスト戦略の「受入条件の数と @US-nn-ACm のシナリオを CI で照合」が未実装
- CI の @wip の grep は @wip を含む push 自体を落とし、戦略の「必須の実行から外す」と食い違う

**Why:** Bolt 3 以降でコードが増えると、分母 0 の空振りや gate の常に成功が偽の安心になる。
**How to apply:** 次の Bolt のレビューではまずこれらの解消を確認し、解消済みならこの記録を消す。関連: [[cargo-tracker-test-strategy]]
