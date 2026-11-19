---
name: project-bolt39b2-format-error-quality-gate
description: Bolt 39b2（形式の誤りの隔離）の品質ゲート。変異 10 個はすべて落ち、Sonar は new_violations 10 で FAIL（2026-11-03）
metadata:
  type: project
---

Bolt 39b2 ステップ 7 の品質ゲート（2026-11-03、コミット 52111bf8e）。

- 変異 V1〜V10 はすべて落ちた。V9（注記を切らない）は計画の見込み「契約」では落ちず、`IngestionServiceTest` の行 17 で落ちた（契約は 1000 文字の往復だけで切ることは見ない）。V3 は `IngestionServiceTest` では落ちない（コマンドを手で作るため）。組み立て・統合・Cucumber で落ちる
- `--tests` で絞っても `RunCucumberTest`（Suite）が一緒に走る。受入シナリオの確かめを足すのに便利
- check: test 3805・documentationTest 5、失敗 0
- Sonar: new_coverage 95.6、重複 0.03%、new_violations 10 で FAIL。`", size="` の重複リテラル（IngestFileCommand :74）、unnamed pattern（catch の e）、record pattern、テストの assertThatThrownBy の lambda に複数の呼び出し（39b の [[project-bolt39b-ingestion-quality-gate]] と同じ型の指摘が再発）

**Why:** 39b でも同じ Sonar の型の指摘が出ており、ステップの承認で spotbugs だけを見て Sonar の規則を見ていない。
**How to apply:** 外部データの Bolt のステップ計画に「lambda の中は 1 呼び出し、catch は `_`、record pattern」を書き込むよう提案する。
