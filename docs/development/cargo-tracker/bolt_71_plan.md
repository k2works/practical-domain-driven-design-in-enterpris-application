---
type: Plan
title: "Bolt 71 計画 - リリースの仕組みを cargo-tracker で動くようにする"
description: "release.js を直し、release のタスクが cargo-tracker の品質ゲート・版・CHANGELOG・タグで動くようにする技術 Bolt（SP 0）の計画。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T02:29:36Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-28T01:18:44Z }
  - { by: human:kakimomokuri, at: 2026-11-28T02:29:37Z }
---

# Bolt 71 計画 - リリースの仕組みを cargo-tracker で動くようにする

`ops/scripts/release.js` を直し、`release:*` の運用タスクが cargo-tracker の品質ゲート・版・CHANGELOG・タグで動くようにします（技術、SP 0）。**この Bolt ではリリースしません。** タグと CHANGELOG は作らず、push もしません。

**状態**: **承認済み（2026-11-28、human:kakimomokuri。「推奨のまま承認」）**。下の CP-1〜CP-7 はすべて推奨のとおりです。実装と確認は 2026-11-28 に終えました（下の「結果」）。**報告は 2026-11-28 に承認されました（human:kakimomokuri。「承認」）。** 承認の議題 1・2 は推奨のとおりです（運用スクリプトのテストを CI で流すのは、後で足します）。

検証:

- `validating-iteration-plan`: 技術 Bolt なので、事実の確認（下の「確認した事実」）で代えました。ストーリー、集約、テーブル、画面は変えません。
- `validating-design`: A は、開発戦略の終盤の技術 Bolt で、業務の流れは変えません。振る舞いのテストを先に書きます。B は、`architecture_infrastructure.md` の CI/CD と食い違いません（配備のイメージのタグはコミットの SHA のままです）。ただし、設計文書にはリリースの版とタグの決まりがないので、この Bolt で決めて運用タスク手順書に書きます。C は、運用タスクを Bolt 54 の形（`--dry-run`、純粋な関数のテスト）にそろえます。[Release 0.1](release_report-0_1.md) と [Release 1.0](release_report-1_0.md) の報告書の「タグと CHANGELOG は `developing-release` で作る」という想定とも合います。

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| 単位 | 技術（運用スクリプト）。SP 0 |
| 位置づけ | 週の外の Bolt。[W11 計画](iteration_plan-W11.md)（承認待ち）の本数には含めない |
| 番号 | 71（使用済みの最後は 70） |
| アプローチ | TDD。版・CHANGELOG・前提の確認は純粋な関数にして単体で、コミットとタグは一時の Git リポジトリで確かめる |
| 見積り | 作業 150 分。品質ゲート 1 回（約 30 分）と報告を足して、壁時計で約 4 時間（実測の根拠はない） |
| 前の Bolt の反映 | [Bolt 51-4](bolt_51-4_plan.md) の「構成を変えると、別のタスクの前提が崩れる」を踏まえ、版を変える影響を Dockerfile まで確かめる（CP-6） |

## 確認した事実（2026-11-28）

| # | 事実 | 確かめた方法 |
| :--- | :--- | :--- |
| F-1 | `gulpfile.js` は `release.js` を読み込んでいない。`release:dry-run` は「Task never defined」で失敗する | 実行した |
| F-2 | 読み込みを足すだけでは直らない。`release:deploy:*` が未定義の `deploy:prd` を参照し、登録の途中で例外になる | 本物の gulp に登録して実行した |
| F-3 | 品質ゲートは `npm run lint`・`test`・`build`・`test:e2e` を呼ぶが、ルートの `package.json` にない | ファイルを読んだ |
| F-4 | 版を上げる対象は `package.json` だけ。アプリの版は `apps/cargo-tracker/build.gradle` の `0.0.1-SNAPSHOT`、ルートは `0.0.0` | ファイルを読んだ |
| F-5 | 今の版の計算は、`0.0.1-SNAPSHOT` を渡すと 3 つ目が `NaN` になる | 実行した |
| F-6 | Dockerfile は `cargo-tracker-*-SNAPSHOT.jar` を写す。版から `-SNAPSHOT` を外すと、イメージのビルドが失敗する | ファイルを読んだ（ビルドは試していない） |
| F-7 | `git log` の失敗を握りつぶし、CHANGELOG が「No notable changes」になる。出力が `execSync` の既定の上限（1 MB）を超えても同じ。今の件名の合計は約 200 KB | コードを読み、大きさを測った |
| F-8 | タグは 1 つもなく、ドライランのプレビューは全 1,708 コミットから 1,757 行になる | 実行した |
| F-9 | `uiTest` は `check` に含まれない。`documentationTest` は含まれる | `build.gradle` を読んだ |
| F-10 | 運用スクリプトのテスト（`node --test`）は、CI で流していない | ワークフローを検索した |
| F-11 | `origin/main` は `origin/develop` より 1,687 コミット遅れている（push した後の値） | `git rev-list` |

## 確認ポイント（承認が要る判断）

| # | 判断 | 推奨 | ほかの案と、推奨しない理由 |
| :--- | :--- | :--- | :--- |
| CP-1 | リリースの単位と版の正本 | cargo-tracker だけをリリースする。正本は `build.gradle` の `version`。ルートと `apps/*` の `package.json` は変えない。版の計算は数字の部分から行い（`0.0.1-SNAPSHOT` からの minor は 0.1.0、major は 1.0.0）、書き戻すときは `-SNAPSHOT` を付けない。リリースの後に次の `-SNAPSHOT` へは進めない | `package.json` と両方を上げる案は、正本が 2 つになる。次の `-SNAPSHOT` へ進める案は、コミットが 1 つ増え、今は使う場面がない |
| CP-2 | タグの名前 | `cargo-tracker-vX.Y.Z`。リリース計画の Release 0.1・1.0・1.1 は、版の 0.1.0・1.0.0・1.1.0 に当てる | `vX.Y.Z` は短いが、このリポジトリは書籍の実践用で、ほかのアプリが増えると区別できない |
| CP-3 | CHANGELOG の置き場と範囲 | `apps/cargo-tracker/CHANGELOG.md`（最初のリリースで作る新規ファイル）。対象のパスは `apps/cargo-tracker`、`ops/terraform`、`ops/scripts/operation*.js`、`.github/workflows/cargo-tracker-*.yml`。載せる種類は `feat`・`fix`・`perf`・`build`。範囲は直前の `cargo-tracker-v*` のタグから。タグがなければ全履歴 | 全部の種類を載せる案は、`test`・`refactor`・`docs` が多く、利用者向けの変更が埋もれる。`!` 付きのコミットは種類にかかわらず載せる |
| CP-4 | 品質ゲートの中身 | 作業ツリーが clean、ブランチが `develop` で `origin/develop` と一致、`node --test ops/scripts/*.test.js`、`./gradlew check documentationTest uiTest bootJar`（約 30 分）。SonarQube は含めない | SonarQube を含める案は、ローカルのサーバーとトークンが要る。含めない代わりに、手順書の確認の欄に残す。CI の結果を読むだけにする案は、手元のコミットの検証にならない |
| CP-5 | `release:deploy:*` | 消す。配備は、承認の付いた `cargo-tracker-deploy` ワークフローで行う | 残してワークフローを起動する案は、本番に関わる操作を 1 つのコマンドにまとめることになる |
| CP-6 | 【配備の設定の変更】Dockerfile と jar の名前 | `bootJar` の出力の名前を `app.jar` に固定し、Dockerfile は版に依らない名前を写す。手元で `demo` のステージをビルドし、起動してログイン画面を確かめる | Dockerfile のワイルドカードを広げる案は、素の jar も作る流れになると 2 つに当たって壊れる |
| CP-7 | この Bolt でやらないこと | 実際のリリース、タグの作成と push、Release 0.1 へのさかのぼりのタグ、`main` への Pull Request、CI への運用スクリプトのテストの追加。`release:patch`・`minor`・`major` は一時のリポジトリでだけ動かす | さかのぼりのタグ（`cargo-tracker-v0.1.0`。候補は Release 0.1 の条件を満たした `e89773d102`）は、外に出る操作なので、必要なら別に指示を受ける |

CP-4 の「`develop` からリリースする」は、[リリースガイド](../../reference/リリースガイド.md)の「リリースブランチから Pull Request」と違います。このプロジェクトは `develop` で開発しているためです。ガイドは汎用の文書なので変えず、差は `PROJECT.md` に書きます。

## 計画

テストを先に書き、失敗を確かめてから直します。

- [x] (1) 版: `ops/scripts/release.test.js` を作る（新規ファイル）。`build.gradle` の版の読み書き（`-SNAPSHOT` あり・なし、`version` の行がない・2 つある）と版の計算のテストを書いてから実装する
- [x] (2) CHANGELOG: パスと種類の絞り込み、`cargo-tracker-v*` のタグからの範囲（ほかのタグは無視する）、`!` 付き、`git log` の失敗をそのまま失敗にするテストを、一時の Git リポジトリで書いてから実装する（F-7・F-8）
- [x] (3) コミットとタグ: 同じタグがあれば何も書き換える前に失敗する、版と CHANGELOG だけをコミットする（`chore(release): cargo-tracker vX.Y.Z`）、タグを打つ、というテストを書いてから実装する
- [x] (4) 品質ゲート: 実行するコマンドの並び（Windows は `gradlew.bat`）と、ブランチと origin の一致の確認のテストを書いてから実装する（CP-4）
- [x] (5) 登録: 本物の gulp に登録できて `release:dry-run` があるテストを書き、`release:deploy:*` を消して `gulpfile.js` に足す（F-1・F-2、CP-5）
- [x] (6) jar の名前: `build.gradle` と Dockerfile を直し、`demo` のステージをビルドして起動を確かめる。版を `-SNAPSHOT` なしにした一時の状態でもビルドできることを確かめる（F-6、CP-6）
- [x] (7) 文書: `.claude/skills/developing-release/PROJECT.md`（新規ファイル）にパス、コマンド、ガイドとの差を書く。運用タスク手順書に「リリース」の節（タスク、前提、リリース条件を人が確かめること、失敗したときの戻し方）を足す
- [x] (8) 確認: 実際のリポジトリで `release:dry-run` を流し、ファイル・タグ・コミットが変わらないことを確かめる。品質ゲートの各タスクを流す（作業ツリーの確認は、コミットの前は失敗するのが正しい）
- [x] (9) この計画に結果を書き、報告の承認を受ける

## 完了条件（DoD）

- [x] `npx gulp --tasks` に `release:dry-run`・`release:preflight`・`release:patch`・`release:minor`・`release:major` が出て、ほかのタスクも一覧に残っている
- [x] `node --test ops/scripts/*.test.js` がすべて成功する
- [x] `check`・`documentationTest`・`uiTest`・`bootJar` が、`release:preflight` の定義するコマンドで成功する
- [x] `release:dry-run` のプレビューが cargo-tracker の対象の変更だけで、実行の前後で `git status` とタグの一覧が変わらない
- [x] `demo` のイメージがビルドでき、dev プロファイルで起動してログイン画面が出る
- [x] 手順書と `PROJECT.md` を直し、OKF の検査が ERROR 0
- ユーザーマニュアルは変えない（利用者の画面と振る舞いは変わらない）

## 結果

### 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `release:preflight:gradle`（`check`・`documentationTest`・`uiTest`・`bootJar`） | BUILD SUCCESSFUL（29 分 13 秒）。`test` 6,600 件、`uiTest` 252 シナリオ、`documentationTest` 11 件、失敗 0 |
| `node --test ops/scripts/*.test.js`（`release:preflight:ops-test` でも流した） | 39 件成功（うち `release.test.js` は 15 件） |
| `release:dry-run`（実際のリポジトリ） | 成功。実行の前後で `git status`・タグ・`HEAD` は変わらない。プレビューは 390 行（Features 265、Bug Fixes 96、Build 6） |
| `release:preflight:clean`・`branch`（未コミットと push 前の状態） | どちらも理由を表示して失敗した（想定どおり） |
| `demo` のイメージ | 今の版（`0.0.1-SNAPSHOT`）と、写しで版を `0.1.0` にした状態の両方でビルドでき、dev プロファイルで起動して `/login` が 200 |
| 直す前の再現（F-6） | 版を `0.1.0` にした写しは、`bootJar` の後の `cp` で失敗した |
| SonarQube | 流していない（Java のソースを変えていない） |

`demo` のイメージの確認は、[Bolt 70](bolt_70_plan.md) で未確認だった「Spring Boot 4.1.3 のデモ用のイメージの起動」の手元での確認にもなります。Heroku での起動は未確認です。

### 計画と違った点

| # | 内容 |
| :--- | :--- |
| 1 | `release:preflight` の最後に、clean と origin の一致の確認をもう一度足した。ゲートの約 30 分の間にコミットや変更が入ると、確かめていない中身にタグが付くため |
| 2 | 最初の実装では、版とタグを書き換える部分を `release:minor:execute` などの名前付きのタスクにしていた。直接呼ぶと品質ゲートを通らないので、テストを足して失敗を確かめ、名前を登録しない形に直した |

### 確認していないこと

- `release:patch`・`minor`・`major` の最初から最後までの通し（R-2）。コミットとタグは一時のリポジトリのテストだけで確かめた。
- `release:preflight` の 1 回の通しの実行と、`release:preflight:branch` が実際のリポジトリで成功する側（テストは判定の関数だけ）。
- Windows 以外での `release:preflight:gradle`。コマンドの組み立てはテストで確かめた。
- 運用スクリプトのテストは、CI では今も流れない（CP-7）。

### 承認の議題

| # | 議題 | 推奨 |
| :--- | :--- | :--- |
| 1 | 計画と違った点 1・2 を含めて、この報告を承認するか | 承認する |
| 2 | 運用スクリプトのテストを CI で流すか（F-10） | 次の運用スクリプトの変更までに、小さな Bolt かこの後の技術の返済で足す |

## リスクと未確認

| # | 内容 | 扱い |
| :--- | :--- | :--- |
| R-1 | 変更に `apps/cargo-tracker` が含まれるので、push すると `deploy-demo` が動く。デモ環境は 2026-11-28 の時点で Heroku の稼働時間の枠の表示が出て 503 で、失敗する見込み | この Bolt の完了条件にデモ環境への配備は入れない。手元のイメージの起動で代える。push は別に指示を受ける |
| R-2 | `release:minor` などの最後までの通し（品質ゲートからタグまで）は、実際のリポジトリでは動かさないので、最初のリリースで初めて通る | ゲートと実行の部分を別々に確かめる。最初のリリースは `release:dry-run` の確認から始める |
| R-3 | リリース条件（性能テスト、時点復旧の訓練など）は、タスクでは検査しない | 手順書の確認の欄にして、人が `release_plan.md` で確かめる |
| R-4 | Release 1.0 はこの Bolt の後もリリースできる状態にならない（条件 7 件が未達） | 仕組みの修正と、リリースの判断は別 |

## 人が用意するもの

- CP-1〜CP-7 の判断。
- Docker が動くこと（ステップ 6 と品質ゲートの Testcontainers）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-28 | 初版（`validating-design` を反映） | anthropic/claude-sonnet-5-5 |
| 2026-11-28 | 人の承認を記録した（CP-1〜CP-7 は推奨のとおり） | anthropic/claude-sonnet-5-5 |
| 2026-11-28 | 実装し、結果を書いた（承認待ち） | anthropic/claude-sonnet-5-5 |
| 2026-11-28 | 報告の承認を記録した（議題 1・2 は推奨のとおり） | anthropic/claude-sonnet-5-5 |
