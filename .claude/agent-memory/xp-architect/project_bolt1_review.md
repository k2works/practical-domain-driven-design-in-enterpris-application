---
name: project-bolt1-review
description: cargo-tracker Bolt 1（ウォーキングスケルトン）のアーキテクトレビューで指摘した未解決のリスク（受信側の冪等性、AT-02 未実装、イベントの直列化契約、afterMigrate 未作成）
metadata:
  type: project
---

2026-10-01 に Bolt 1（コミット 967823e..1221679）をレビューした。主な指摘:

- DE-01 の購読（KpiObservationEventHandler）が冪等でない。PK 衝突で配信が未完了のまま残り、再配信を有効にすると失敗し続ける
- 再配信（起動時・定期）、完了済み発行記録の掃除（DA-01）が未設定。設計（architecture_backend の ARCH-HO-01）は実装済みと読める
- AT-02（層の向き）未実装。`infrastructure.config` がアプリケーションサービスを組み立てる決定と、文字どおりの依存規則が食い違う（合成ルートの例外を明記すべき）
- イベントの payload に共有カーネルの record（CompanyId、UtcInstant）が入り、Jackson で直列化されて保存される。共有カーネルの部品名の変更が保存済みイベントを壊す
- 追記専用の transport_request_version に対する afterMigrate の権限剥奪が未作成
- D-1 は「注釈に一本化」に賛成。ただし ArchUnit で events パッケージと注釈を相互に縛り、受信側の冪等キーは業務キーで取ると明記する条件付き

**Why:** 後続の Bolt（U2・U3 のサガ、再配信）でこれらが前提になるため。
**How to apply:** 次の Bolt のレビューでは、上記が解消されたか、意図的な負債として記録されたかを最初に確認する。[[bolt-gate-density]]
