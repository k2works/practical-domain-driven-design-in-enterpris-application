# cargo-tracker — ADR

cargo-tracker プロジェクトのADRドキュメントです。

## ドキュメント一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](./001-modular-monolith.md) | モジュラーモノリスを採用する | 承認済み |
| [ADR-002](./002-domain-logic-pattern-per-context.md) | ドメインロジックのパターンを境界づけられたコンテキストごとに選ぶ | 承認済み |
| [ADR-003](./003-inter-context-integration.md) | コンテキスト間は冪等コマンド・永続化したドメインイベント・オーケストレーション型のサガで連携する | 承認済み |
| [ADR-004](./004-external-data-ingestion.md) | 外部原本は Inbox・隔離・照合の 3 段で取り込む | 承認済み |
| [ADR-005](./005-server-side-rendering.md) | 画面はサーバーサイドレンダリングとハイパーメディアで提供する | 承認済み |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
