---
name: project-bolt4-review
description: cargo-tracker Bolt 4（業務番号の採番・輸送条件・DE-01 の業務番号）のアーキテクトレビューの指摘（REQUIRES_NEW の接続枯渇、連番 URL の他社参照、null 許容の二重基準、文書の不一致）
metadata:
  type: project
---

2026-10-02 に Bolt 4（207ecfc..46a5397）をレビューした。主な指摘:

- 採番は年の行がないときだけ REQUIRES_NEW で INSERT する。外側の接続を握ったまま 2 本目を取るため、プールの大きさ以上の同時の「年の最初の提出」で接続が枯渇しうる。並行テストは 8 スレッド（既定のプール 10 未満）なので露見しない
- 完了画面の URL が連番の業務番号で、所有者の絞り込みなし（Q-INV-08 は US-18・AC3 に先送り）。番号を推測して他社の要求を見られる
- マイグレーションは NOT NULL（既存データなしの前提）、DE-01 と KPI の業務番号は null 許容（既存データありの前提）で、前提が二重
- domain_model.md の SubmissionViolations「直し方を持つ」は実装（文言は画面の層）と不一致
- TransportRequestNumberIssuer がポートなのに model.aggregates に置かれている

**Why:** 次の Bolt（US-18 の認証、版の改訂）がこれらに依存する。
**How to apply:** 次のレビューでは上記と [[project-bolt3-review]] の残件を最初に確認する。
