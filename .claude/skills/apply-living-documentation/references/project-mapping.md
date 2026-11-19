# 本プロジェクトの成果物との対応

新しい仕組みを足す前に、既存の成果物がどの Living Documentation パターンに当たるかを確認する。複製せず、既存のスキル・ガイドを使う。

| パターン | 本プロジェクトの成果物・プラクティス | 使うスキル／参照 |
| :--- | :--- | :--- |
| 根拠の記録、Decision log | ADR（背景・決定・影響・代替案） | `creating-adr` |
| コミットメッセージによる文書化 | Conventional Commits、CHANGELOG 自動生成 | `git-commit`、`developing-release` |
| 整合性検証 | TDD の単体テスト、BDD のシナリオ | `docs/reference/BDD導入ガイド.md`、`developing-backend`、`developing-frontend` |
| サイドカー・メタデータ | OKF のフロントマター（来歴・検証状態・ライフサイクル） | `apply-okf` |
| プレーンテキストの図 | PlantUML・Mermaid | 各 `analyzing-*` スキル |
| Living Glossary | ドメインモデルのユビキタス言語、用語集 | `analyzing-domain-model`、`docs/reference/AI-DLC用語集.md` |
| 単一情報源からの公開 | Markdown を MkDocs で HTML 公開 | `operating-docs` |
| 既成のドキュメント | リファレンス・記事シリーズへのリンク（複製しない） | `docs/article/` |
| 判断と学びの記録 | 開発ジャーナル | `creating-journal` |
| 規約による文書化 | ドキュメント構成ガイドによる配置規約 | `apply-docs-structure` |
| アノテーション | 役割・性質・根拠をコードに持たせる | `docs/reference/アノテーション導入ガイド.md` |

## AI-DLC との関係

AI-DLC は「成果物は契約であり、コンテキストである」とする。Living Documentation に沿って成果物を永続化すると、AI に渡すコンテキストが常にコードと同期する。古い文書は AI の誤判断の原因になるため、Reliable（信頼できる）原則は AI-DLC で一層重要になる。

## 迷ったときの目安

- 設計書に書こうとしている内容がコード・DDL・テストにある → リンクか生成
- 「なぜこうしたか」が消えかけている → ADR かコミットメッセージで根拠を残す
- 新メンバーが読み始める入口がない → マップかツアー（`references/curation.md`）
- 手書きの一覧・用語集・図がずれる → 生成（`references/automation.md`）
