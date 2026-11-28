---
name: project-bolt5-review-tests
description: Bolt 5（US-02 審査）テストレビューで見つけた未解消点。楽観ロックが同一トランザクション、メモリ版と本物の審査記録の並び順の差、4,000 文字の UTF-16 判定（2026-10-02）
metadata:
  type: project
---

Bolt 5 のテストレビュー（2026-10-02）で指摘した点。次の Bolt で解消されたか確かめる。

- 楽観ロックの統合テストは @Transactional の 1 トランザクション内で順に update するだけ。2 トランザクションの交差（READ COMMITTED で後の UPDATE が待って 0 件になる）は未検証（R-27 の残り）
- 本物の審査記録は ORDER BY decided_at, id（id は乱数 UUID）。メモリ版は追加順。同時刻の記録が 2 件あると本物では順が不定で、受入ステップの getLast と食い違いうる
- 4,000 文字の判定は String.length()（UTF-16）。PostgreSQL の VARCHAR(4000) は文字数。サロゲートペアの境界テストなし
- Controller テストで STALE_VERSION のスタブが版 1 の集約を返し、最新版 2 の再審査表示（隠し項目 versionNo=2）を確かめていない

**Why:** 業務ルール層はメモリ版で回すため、本物との差がシナリオでは隠れる。
**How to apply:** 状態を変える集約の次の Bolt（US-03 など）のレビューで同じ型の差を確認する。[[project-bolt3-e2e-review]]
