---
name: bolt32c-review
description: Bolt 32c（request ごとの利用者の確かめ、SessionRevalidationFilter）のアーキテクトレビューで残した指摘
metadata:
  type: project
---

Bolt 32c（2026-10-14 レビュー）の指摘。

- フィルターの順序: SessionRevalidationFilter は addFilterAfter(SessionLifetimeFilter) で順序値が UsernamePasswordAuthenticationFilter と同じになり、登録順（stable sort）で前に来ているだけ。チェーンの順序を固定する見張りテストを提案した
- 静的資源（/css・/js・/webjars・/favicon）にも認証済みなら毎回 SQL。テスト名「公開の URL では読まない」は未認証の /login しか確かめていない。並列の資源 request で SESSION_REVOKED が重複しうる
- UserStanding は 2 つの集約を持つ読み取り用の組（@ValueObject）。毎 request で password_hash も読む
- ロック（Bolt 32）は既存 session を止めない（authenticationRejection に入らない）。意図かを確かめる余地

**Why:** W10 の性能計測（request ごとの SQL）と Bolt 34（利用者の管理）でこれらが効いてくる。
**How to apply:** 32d・34・W10 のレビューで解消状況を確かめる。[[bolt21-review]] の listener 例外の扱いとも並べて見る。
