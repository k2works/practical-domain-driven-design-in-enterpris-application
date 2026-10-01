---
name: project-bolt2-review
description: cargo-tracker Bolt 2（CI と品質の安全網）のアーキテクトレビューで残した指摘（AT-02 の層外パッケージの抜け、イベントの依存先の規則なし、アクションの SHA 固定なし、D-1/D-5 の文書化）
metadata:
  type: project
---

2026-10-02 に Bolt 2（c606428..48bb848）をレビューした。主な指摘:

- AT-02 は `consideringOnlyDependenciesInLayers` のため、層外の `platform`・ルート（ClockConfiguration）・`shared.annotation` と外部ライブラリへの依存を見ない。application が Web・MyBatis に依存しても通る
- D-5（infrastructure.config だけ application 可）は妥当。ADR 新設は不要、architecture_backend の確定と ADR-001 への追記で足りる。config に @Configuration 以外を置かない規則を推奨
- D-1 の両方向規則は、イベント専用の enum・入れ子の値を domain.events に置けなくする。イベントの依存先（java・UUID・shared.domain だけ）を縛る規則がない
- D-1 は ADR-003 の改訂か domain_model の図の修正で記録すべき（図に DomainEvent interface が残る）
- CI のアクションはタグ参照で SHA 固定なし、Dependabot なし。配備ジョブ追加時は id-token: write をジョブ単位に
- sonar_local.js の gradle は `check sonar` になり、check の失敗で解析自体が走らない。トークンがコマンドライン引数

**Why:** Bolt 3 以降でサガ・配備ジョブ（ADR-008 の OIDC）が入る前に安全網の穴を塞ぐため。
**How to apply:** 次のレビューで上記と [[project-bolt1-review]] の残件の解消状況を最初に確認する。
