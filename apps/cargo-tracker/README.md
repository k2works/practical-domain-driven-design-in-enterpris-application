# cargo-tracker

A 社国際貨物輸送管理システム。境界づけられたコンテキストをモジュールに分けた、Spring Boot のモジュラーモノリス（ADR-001）。

- 起動・テスト・品質チェックの手順: [アプリケーション開発環境セットアップ手順書](../../docs/operation/cargo-tracker/application_development_setup.md)
- 品質チェックのコマンド: [開発戦略](../../docs/development/cargo-tracker/development_strategy.md) の「品質チェックのコマンド」
- 設計: [docs/design/cargo-tracker](../../docs/design/cargo-tracker/index.md)、ADR: [docs/adr/cargo-tracker](../../docs/adr/cargo-tracker/index.md)

```bash
./gradlew bootRun   # H2 で起動して http://localhost:8080/ を開く（ログインの画面は開発用の荷主で入力済み）
./gradlew check     # すべての検証（Docker が要る）
```

## 開発環境のログイン

`./gradlew bootRun`（`dev` プロファイル）では、開発用の企業と利用者が入り、ログインの画面（A-01）は荷主の利用者で入力済みになります。ログインの画面の下の「開発用の利用者でログイン」から、荷主担当者・営業担当者のどちらかを選んでそのままログインすることもできます（Bolt 14、ADR-012）。値は開発用の固定の値で、本物の秘密ではありません。ステージング・本番にはこの利用者も入力済みもありません。

| 役割 | メールアドレス | password | 企業 |
| :--- | :--- | :--- | :--- |
| 荷主担当者 | `shipper@dev.cargo-tracker.example` | `dev-password-shipper` | 荷主 A（開発） |
| 荷主担当者（別の企業） | `shipper-b@dev.cargo-tracker.example` | `dev-password-shipper` | 荷主 B（開発） |
| 営業担当者 | `sales@dev.cargo-tracker.example` | `dev-password-staff` | A 社（開発） |

営業の画面を使うときは、ヘッダーの「ログアウト」の後に「営業担当者でログイン（sales@dev.cargo-tracker.example）」を押してください。H2 のコンソールは、ログインと CSRF の守りの外に置くことになるため Bolt 14 で外しました。
