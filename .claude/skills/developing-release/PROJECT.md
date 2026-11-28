# developing-release のプロジェクト固有の定め（cargo-tracker）

`SKILL.md` と[リリースガイド](../../../docs/reference/リリースガイド.md)は汎用の手順です。食い違う点は、この文書を正とします（[Bolt 71](../../../docs/development/cargo-tracker/bolt_71_plan.md)。2026-11-28 承認）。

手順の詳細は[運用タスク手順書](../../../docs/operation/cargo-tracker/operation_tasks.md)の「リリース（版・CHANGELOG・タグ）」を先に読みます。

## コマンド

| 操作 | コマンド（リポジトリのルートで） |
| :--- | :--- |
| ドライラン | `npx gulp release:dry-run` |
| 品質ゲート | `npx gulp release:preflight`（約 30 分） |
| リリース | `npx gulp release:patch`・`release:minor`・`release:major` |
| テスト | `node --test ops/scripts/release.test.js` |

## 汎用の手順との差

| 項目 | このプロジェクト |
| :--- | :--- |
| リリースの単位 | cargo-tracker だけ。版の正本は `apps/cargo-tracker/build.gradle`。`package.json` の版は変えない |
| タグ | `cargo-tracker-vX.Y.Z` |
| CHANGELOG | `apps/cargo-tracker/CHANGELOG.md`。cargo-tracker のパスの `feat`・`fix`・`perf`・`build` と `!` 付き |
| 品質ゲート | Git の状態、運用スクリプトのテスト、Gradle の `check`・`documentationTest`・`uiTest`・`bootJar`。静的解析は `check` に含まれる。SonarQube は含まない |
| ブランチと Pull Request | `develop` からリリースする。リリースブランチからの Pull Request は作らない |
| デプロイ | `release:deploy:*` はない。承認の付いた `cargo-tracker-deploy` ワークフロー（`deploy:release`）で行う |

## 実行の前に

- リリース計画のリリース条件がすべて満たされていることを、人が確かめます。タスクは検査しません。未達があるときは、リリースを実行しません。
- タグと push は外に出る操作です。人の指示を受けてから行います。
