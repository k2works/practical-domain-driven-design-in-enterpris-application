# cargo-tracker

A 社国際貨物輸送管理システム。境界づけられたコンテキストをモジュールに分けた、Spring Boot のモジュラーモノリス（ADR-001）。

- 起動・テスト・品質チェックの手順: [アプリケーション開発環境セットアップ手順書](../../docs/operation/cargo-tracker/application_development_setup.md)
- 品質チェックのコマンド: [開発戦略](../../docs/development/cargo-tracker/development_strategy.md) の「品質チェックのコマンド」
- 設計: [docs/design/cargo-tracker](../../docs/design/cargo-tracker/index.md)、ADR: [docs/adr/cargo-tracker](../../docs/adr/cargo-tracker/index.md)

```bash
./gradlew bootRun   # H2 で起動して http://localhost:8080/customer/transport-requests/new を開く
./gradlew check     # すべての検証（Docker が要る）
```
