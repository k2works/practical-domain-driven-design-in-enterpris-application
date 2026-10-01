# cargo-tracker — 設計

cargo-tracker プロジェクトの設計ドキュメントです。

## ドキュメント一覧

- [cargo-tracker バックエンドアーキテクチャ](./architecture_backend.md) — 境界づけられたコンテキスト、コンテキストごとのドメインロジックパターン、パッケージ構成、サガとドメインイベントによる連携（ARCH-HO-01〜03）、受信サービスの方針。
- [cargo-tracker フロントエンドアーキテクチャ](./architecture_frontend.md) — 利用チャネル、サーバーサイドレンダリングによる画面提供、画面の配置、アクセシビリティと開示制御。
- [cargo-tracker ドメインモデル](./domain_model.md) — 業務領域の分類、ユビキタス言語、6 コンテキストの集約・不変条件・状態遷移、コマンド・クエリ・イベント、予約サガ。
- [cargo-tracker データモデル](./data_model.md) — 概念データモデル、スキーマ分割、H2 と PostgreSQL の共通部分の規約、コンテキストごとの論理データモデルと ER 図。
- [cargo-tracker UI 設計](./ui_design.md) — 画面のオブジェクト、画面一覧、画面遷移、主要画面のイメージ、共通部品、エラー時を含むインタラクション。
- [cargo-tracker テスト戦略](./test_strategy.md) — テスト形状、テストレベル、BDD のシナリオ階層とタグ規約、不変条件・ストーリーとテストの対応、カバレッジ、CI。
- [cargo-tracker 技術スタック](./tech_stack.md) — バックエンド・画面・DB・テスト・ビルド・インフラの技術、バージョン、サポート期限、アップグレード計画。
- [cargo-tracker インフラストラクチャアーキテクチャ](./architecture_infrastructure.md) — デプロイ形態、環境構成、データ保護、可観測性、CI/CD。

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
