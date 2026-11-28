---
name: project-bolt3-review
description: cargo-tracker Bolt 3（E2E の基盤・生きたドキュメント・追記専用の権限）のアーキテクトレビューの指摘（Flyway と実行時の DB 利用者が未分離、REVOKE の列挙が fail-open、CI の paths が設計文書を含まない、E2E のステージング移行の継ぎ目）
metadata:
  type: project
---

2026-10-02 に Bolt 3（170eaf1..f09659d）をレビューした。主な指摘:

- Flyway の実行利用者とアプリの実行利用者が分かれていない（`spring.flyway.user` なし）。テストもアプリ本体は所有者で接続し、アプリ利用者の権限で動く経路は統合テストの 4 件だけ。本番で DA-02 の分離をどう実現するかが未定
- afterMigrate は「全表に GRANT → 追記専用を名指しで REVOKE」。新しい追記専用の表で REVOKE を忘れると黙って保護が外れる（fail-open）。利用者がなければ何もしない分岐も、本番の設定誤りを黙らせる
- ALTER DEFAULT PRIVILEGES は不要と判断（毎回の afterMigrate で足り、既定権限は fail-open を広げる）。ただしスキーマ自動初期化（Modulith・Spring Session）を無効のままにする前提を明記すべき
- CI の paths が `apps/cargo-tracker/**` だけで、用語集の整合テストが読む docs/design の変更で CI が動かない
- test タスクの入力に src/main/java と domain_model.md を足したため、文書の変更で Testcontainers を含む全テストが再実行される。整合テストは別タスクに分けるのがよい
- E2E は `http://localhost:port` と @SpringBootTest に固定。ステージングへ移すには基底 URL の継ぎ目が要る。完了画面の UUID を読む手順は D-4（UUID を画面に出さない）と衝突する
- D-3 が「最初の提出で固定」と決めたので、Bolt 1 の KPI 購読の冪等性（PK 衝突）は「後続の版は無視」で直せる

**Why:** 次の Bolt（US-01 AC2、業務番号）と運用準備（ステージング・db:init・db:grants:verify）がこれらに依存する。
**How to apply:** 次のレビューでは上記と [[project-bolt2-review]]・[[project-bolt1-review]] の残件を最初に確認する。
