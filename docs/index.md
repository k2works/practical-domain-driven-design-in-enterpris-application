---
okf_version: "0.2"
---

# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [記事](./article/index.md) - DDD の開発ガイドライン（第 1〜3 章）と学習用の記事シリーズの入口です。

## ドキュメント構成

| カテゴリ | 概要                                  | 状況 |
| :--- |:------------------------------------| :--- |
| [戦略](./strategy/index.md) | 企業分析、経営戦略、ビジネスアーキテクチャ、インセプションデッキの整理 | `index.md` を整備済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口               | cargo-tracker の要件定義書を作成済み |
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能の整理              | cargo-tracker のアーキテクチャ設計・技術スタック・ドメインモデル・データモデル・UI 設計・テスト戦略・非機能要件・運用要件を作成済み |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理               | cargo-tracker のリリース計画・開発戦略を作成済み。Bolt 1（ウォーキングスケルトン）と Bolt 2（CI と品質の安全網）を完了し `apps/cargo-tracker` に実装 |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理                   | `index.md` を整備済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録                      | cargo-tracker のユースケース・分析成果物・Bolt 1・Bolt 2 のレビュー 4 件 |
| [ADR](./adr/index.md) | Architecture Decision Records の管理   | cargo-tracker の ADR-001〜009 を承認済み |
| [記事](./article/index.md) | 開発ガイドラインと学習用の記事シリーズ一覧              | DDD 開発ガイドライン 3 章と公開サイトへのリンク集 |
| [リファレンス](./reference/index.md) | 開発ガイドラインやベストプラクティス                  | 39 件のドキュメントを配置 |
| [テンプレート](./template/index.md) | 各種ドキュメントの作成テンプレート                   | 18 件のテンプレートを配置 |

## 補足

- `requirements/cargo-tracker/` に A 社国際貨物輸送管理システムの要件定義書を配置しています。
- `design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
