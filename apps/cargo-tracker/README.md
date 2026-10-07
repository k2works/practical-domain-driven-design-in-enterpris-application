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
| 経路設計者 | `route-designer@dev.cargo-tracker.example` | `dev-password-staff` | A 社（開発） |

営業の画面を使うときは、ヘッダーの「ログアウト」の後に「営業担当者でログイン（sales@dev.cargo-tracker.example）」を押してください。経路設計の画面（案件一覧・経路候補の比較）は「経路設計者でログイン（route-designer@dev.cargo-tracker.example）」で開きます。開発環境（デモ環境を含む）には、起動した時点で次のサンプルが入っています（Bolt 17・18。日時は起動した日からの相対なので、いつ起動しても期限は先にあります）。

| サンプル | 内容 |
| :--- | :--- |
| 見積依頼 | TR-2026-0901（荷主 B、審査中）、0902（荷主 A、見積り作成中）、0903（荷主 A、見積提示済み）、0904（荷主 B、経路設計中）、0905（荷主 A、経路設計中） |
| 経路設計の案件 | RC-2026-0901（0904。候補の算出待ち）、RC-2026-0902（0905。候補算出済み。適合 2 件、規則の未登録・接続不足・期限超過の除外 3 件） |
| 航海と接続時間規則 | 東京 → ロッテルダムの架空の航海 7 本（DEMO-…）と、シンガポールの必要最小接続時間 8 時間 |

荷主 A は `shipper@dev.cargo-tracker.example`、荷主 B は `shipper-b@dev.cargo-tracker.example` です。新しく提出した見積依頼の業務番号は TR-2026-0906 から振られます。H2 のコンソールは、ログインと CSRF の守りの外に置くことになるため Bolt 14 で外しました。

## デモ環境（Heroku）

関係者が触って確かめるデモ環境を、`dev` プロファイルのまま Heroku で動かしています（ADR-013）。上の開発用の利用者で、だれでもログインできます。本物のデータを入れないでください。データは再起動で初期状態に戻ります。

develop にアプリか設計文書（`docs/design/cargo-tracker/**`）の変更を push すると、cargo-tracker CI の check・ui が緑になった後に、`deploy-demo` ジョブが自動で配備します（Bolt 16）。配備するとデモ環境が再起動してデータが初期状態に戻るので、デモの最中は develop に push しないでください。

```bash
npx gulp deploy:demo:status   # リポジトリのルートで。動いているコミット（DEMO_REVISION）と URL
npx gulp deploy:demo:help     # ほかのタスク（ログ・再起動・停止、CI が使えないときの手元からの配備）
```

手順: [Heroku デモ環境セットアップ手順書](../../docs/operation/cargo-tracker/heroku_demo_setup.md)
