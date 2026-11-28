---
name: project-bolt39b4-ingestion-scale-quality-gate
description: Bolt 39b4（取込の規模・上限・H2 補助文字）の品質ゲート。変異 V1〜V8 すべて落ちた、test 4075 件、Sonar PASS（2026-11-04）
metadata:
  type: project
---

Bolt 39b4 ステップ 6 の品質ゲート（2026-11-04、fd570d27b）。変異 V1〜V8（V4 は行数・バイト数の 2 通り、V5 は received_record・external_record の 2 通り）はすべて見込みのテストで落ちた。check 緑（test 4075、documentationTest 5）。SonarQube PASS（新しいコードのカバレッジ 95.7%、重複 0.02%、違反 0）。

**Why:** 計画の変異の表がそのまま通った前例。V1（REQUIRES_NEW を外す）は行 4 だけでなく行 3（接続の最大が 1）でも落ちる。
**How to apply:** `performanceTest --tests '*.行1*'` は行 12 も拾う（名前の前方一致）。寄港 60 を避けたいならメソッド名を完全に書く。ローカルでは 3 本で 3 分を超えるので background で流す。[[project-bolt39b3b-external-audit-quality-gate]]
