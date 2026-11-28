# 運用

開発環境構築・デプロイ・運用に関するドキュメントです。

## プロジェクト一覧

| プロジェクト | 概要 | 状況 |
| :--- | :--- | :--- |
| [cargo-tracker](cargo-tracker/index.md) | A 社国際貨物輸送管理システム | アプリケーション開発環境セットアップ手順書を承認済み。AWS 環境は運用準備（W10）で作成 |

## 共通の運用コマンド

### OKF（知識バンドル）

`docs/` は OKF v0.2 の知識バンドルです（[OKF 導入ガイド](../reference/OKF導入ガイド_V0.2.md)）。
移行・検証は `migrating-okf` スキル、日常の運用は以下の Gulp タスクを使います。

| コマンド | 概要 |
| :--- | :--- |
| `gulp okf:check` | バンドルの適合性を検証する。ERROR があれば非ゼロ終了 |
| `gulp okf:upgrade` | 仕様バージョンへ追従する（`OKF_VERSION`、`OKF_DRY_RUN=1` で試行） |
| `gulp okf:setup` | 上流ツール `reference_agent` を `tmp/open-knowledge-format` にクローン・インストール |
| `gulp okf:viz` | バンドルをグラフ HTML に可視化する（既定 `tmp/okf/viz.html`） |
| `gulp okf:viz:open` | 可視化 HTML をブラウザで開く |
| `gulp okf:enrich` | BigQuery データセットからバンドルを生成する（要 GCP 認証・`OKF_DATASET`） |
| `gulp okf:help` | タスクと環境変数の一覧 |

環境変数は `.env.example` の OKF セクションを参照してください。

## 補足

- 実ドキュメントを追加したら、この一覧と `docs/index.md` を更新します。
- テンプレートは次を利用できます。
  - [template/アプリケーション開発環境セットアップ手順書.md](../template/アプリケーション開発環境セットアップ手順書.md)
  - [template/開発環境セットアップ手順書.md](../template/開発環境セットアップ手順書.md)
  - [template/AWSステージング環境セットアップ手順書.md](../template/AWSステージング環境セットアップ手順書.md)
  - [template/AWSプロダクション環境セットアップ手順書.md](../template/AWSプロダクション環境セットアップ手順書.md)
