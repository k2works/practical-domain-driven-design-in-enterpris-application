---
type: Plan
title: "Bolt 2 終了報告 - CI と品質の安全網"
description: "2 回目の Bolt の終了報告。CI・静的解析・カバレッジの閾値・AT-02・AT-06・SonarQube の品質ゲートの成果、指標、仮説 H1〜H4 の結論、ゴールの指示で進めた【要確認】、ふりかえりをまとめる。"
tags: [development,bolt-report]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-01T13:53:25Z }
---

# Bolt 2 終了報告 - CI と品質の安全網

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 2 回 |
| 期間 | 2026-10-01 22:08〜22:55 JST（約 47 分。AI の実行時間で、人のレビューの時間を含まない） |
| 対象 | 技術タスク（Unit 横断） |
| 計画 | [Bolt 2 計画](bolt_02_plan.md) |
| GitHub | [#1 [技術] 開発基盤とウォーキングスケルトン](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/1)（Bolt 3 が残るため Open・In Progress のまま） |
| ゴール | push と Pull Request のたびに、GitHub Actions がテスト・層の規則・静的解析・カバレッジの閾値を検証する。ローカルでは SonarQube の品質ゲートで同じコードを判定できる |
| 進め方 | 計画の承認の後、人の指示（`/goal orchestrating-development Bolt2`）でステップ 1〜8 を続けて実行した。各ステップの承認ゲートと【要確認】は本報告でまとめて受ける |

## 成果

| ステップ | 状態 | コミット | 誤りの検出と修正（検出の手段） |
| :--- | :--- | :--- | :--- |
| 1. テストのイメージを固定する | 完了 | `a5b9a1b` | — |
| 2. 書式と静的解析 | 完了 | `98abfdc`（書式だけ）、`29d2e51` | SpotBugs の Gradle の enum の書き方を直した（ビルドの失敗）。SpotBugs の指摘 2 種を直すか抑止した（下の「判断」） |
| 3. 層とモジュールの規則 | 完了 | `e07841b` | AT-06 の検査対象を `runtimeClasspath` から `productionRuntimeClasspath` に直した。Spring Boot のプラグインが `runtimeClasspath` に `developmentOnly` を含めていたため（テストの失敗） |
| 4. カバレッジ | 完了 | `e7a9829` | — |
| 5. CI | 完了 | `0708ca6` | — |
| 6. SonarQube の品質ゲート | 完了 | `20f1da4` | 解析の反映を待たずに判定した結果が NONE だった（待ってから判定し直した）。Code Smell 3 件を直した（SonarQube の指摘） |
| 7. 文書を合わせる | 完了 | `35873d1` | — |
| 8. 検証と終了報告 | 完了（人の承認待ち） | 本報告 | — |

打ち切りの線（ステップ 6 を Bolt 3 へ）は使わなかった。`SONAR_TOKEN` は人が用意済みだった。

### デモ項目の結果

| # | デモ | 結果 |
| :--- | :--- | :--- |
| 1 | develop に push する | GitHub Actions の cargo-tracker CI が緑（`0708ca6`、`35873d1` の 2 回）。レポートは成果物 `cargo-tracker-reports` に残る |
| 2 | `domain`・`infrastructure` に一時的な違反を入れる | AT-01（Bolt 1）、AT-02 の合成ルートの規則、`domain.events` の規則、AT-06 がそれぞれ失敗した |
| 3 | `./gradlew jacocoTestReport` | 全体 行 97.6%・分岐 90.0%。全レイヤーが最初の閾値を満たす。閾値を 0.99 に上げると失敗することも確かめた |
| 4 | `npx gulp sonar-local:check` | Quality Gate PASS |

## 指標

| 指標 | 値 |
| :--- | :--- |
| 承認ゲート | 計画の承認 1 回。ステップごとのゲートは本報告でまとめて受ける |
| 人の変更依頼 | 0（実行中） |
| 実行中に AI が直した誤り | 3（ビルドの書き方 1、検査対象の取り違え 1、解析の反映待ち 1）。すべてテストかビルドの失敗、または判定の結果で見つけた |
| 静的解析の指摘 | SpotBugs 2 種（直した 1、理由つきで抑止 1）、Checkstyle 0、SonarQube の Code Smell 3（すべて直した） |
| リードタイム | 約 47 分 |
| CI の実行時間 | 全体 約 2 分（`./gradlew check` 1 分 38 秒） |
| テスト | 61 件、すべて成功（Bolt 1 から +4: AT-02 2、ドメインイベントの配置 2） |
| カバレッジ | domain 100%/100%、application 96.8%、interfaces 100%/83.3%、infrastructure 100%、全体 97.6%/90.0%（行/分岐） |
| SonarQube | Quality Gate PASS、Bug 0、Vulnerability 0、Code Smell 0、Security Hotspot 0、重複 0.0%、カバレッジ 97.3% |

## 品質ゲート

| 項目 | 結果 |
| :--- | :--- |
| 全テスト | 成功（61 件） |
| 書式・静的解析 | Spotless・Checkstyle・SpotBugs すべて成功 |
| カバレッジ | 全レイヤーで最初の閾値を満たす |
| CI | 緑（最新 `35873d1`） |
| SonarQube の品質ゲート | PASS |
| ユーザーマニュアル | 対象外（画面の変更なし） |

Bolt 1 のクローズで未達だった「CI が緑」と「Quality Gate が PASS」を満たした。

## 仮説の結論

| # | 仮説 | 結論 | 根拠 |
| :--- | :--- | :--- | :--- |
| H1 | CI で `./gradlew check` が時間の目標に収まる | 成り立った | 全体で約 2 分。目標は合計 8 分（2 分 + 6 分）。段階を分けずに 1 つのジョブで足りる |
| H2 | Bolt 1 のコードがテストを足さずに最初の閾値を満たす | 成り立った | 全レイヤーで閾値を 10 ポイント以上上回った |
| H3 | AT-02 を入れても、合成ルートの例外だけで既存のコードが通る | 成り立った | 違反は 0。合成ルートの外の `infrastructure` が `application` に依存すると失敗することも確かめた |
| H4 | SonarQube の品質ゲートを Bolt 1 のコードのまま通る | 成り立った | 最初のスキャンで PASS。Code Smell 3 件は品質ゲートの条件外だったが、0 にした |

## 判断と学び

### ゴールの指示で提案どおり進めた【要確認】（人の確認が必要）

計画では各ステップの実行前に確認するとしていたが、ゴールの指示（確認で止まらずに進める）に従い、計画の提案どおりに進めた。変えたい場合は戻せる。

| # | 事項 | 実施した内容 | 戻すときの影響 |
| :--- | :--- | :--- | :--- |
| 1 | Spotless の導入（技術スタックにないツール） | palantir-java-format で全ファイルの書式をそろえた（`98abfdc` は書式だけ） | `build.gradle` の `spotless` を外す。書式の変更はそのまま残してよい |
| 2 | D-5 合成ルートの例外 | AT-02 で `..infrastructure.config..` だけが `application` に依存してよいとした | 例外を認めないなら、サービスの組み立て場所を別のパッケージ（例: `<context>.config`）へ移す |
| 3 | D-1 `DomainEvent` を注釈に一本化 | `domain.events` のクラスは `@DomainEvent` の付いた record、という両方向の規則を入れた。ドメインモデルの共有カーネルの図（`DomainEvent` インターフェース）はまだ直していない | 一本化しないなら、規則を片方向に戻し、インターフェースを作る |
| 4 | push（外部への公開） | develop に 3 回 push した（CI を動かすため） | — |

D-1 を承認する場合は、ドメインモデルの共有カーネルの図からインターフェースを外す修正を、Bolt 3 の最初に行う。

### その他の判断

- **SpotBugs の抑止**: DI で受け取ったマッパーを保持することへの `EI_EXPOSE_REP2` は誤検知とし、`infrastructure` に限って理由つきで抑止した（`config/spotbugs/exclude.xml`）。集約へのコンストラクターの例外の指摘（`CT_CONSTRUCTOR_THROW`）は、集約を `final` にして直した
- **SpotBugs はテストコードを検査しない**: Mockito のスタブなど、意図した書き方の指摘が多いため
- **Checkstyle の規則は最小**: 書式は Spotless に任せ、バグの元になる書き方と循環的複雑度 10 以下だけを検査する。メソッドの行数（推奨 20 行）は推奨のため入れていない
- **AT-06 の検査対象**: Spring Boot の Gradle プラグインでは、`runtimeClasspath` が `bootRun` のために `developmentOnly` を含む。本番の成果物の検査は `productionRuntimeClasspath` で行う
- **SonarQube の判定の待ち**: スキャンの直後は解析が反映されておらず NONE になる。`sonar-local:check` の直後に PASS と見なさず、反映を待ってから判定する必要がある。運用スクリプトの改善候補として Bolt 3 で扱う

### Bolt 1 の Try の結果

| Try | 結果 |
| :--- | :--- |
| T-1 設計の約束を「入れる／入れない」の表に書く | Bolt 2 計画で実施した。AT-04・AT-05・Trivy・OIDC を意図的に入れなかったことが、計画の時点で見えた |
| T-2 AT-02 と `@DomainEvent` の配置の規則 | 入れた |
| T-3 テストダブルと契約テスト | 対象なし（新しいテストダブルを作っていない） |
| T-4 コミットする統合テストは自分の ID で待つ | 対象なし（新しい統合テストを作っていない） |
| T-5 CI・JaCoCo・フォーマッター・SonarQube | すべて入れた |

### ふりかえり（KPT）

- **Keep**:
  - 書式だけのコミットを分け、振る舞いの変更と混ぜなかった
  - 規則ごとに一時的な違反で失敗を確かめ、空振りしないことを示した
  - Bolt を分けて、約 47 分で収めた
- **Problem**:
  - 【要確認】を、ゴールの指示で事後の確認に回した。計画で「実行前に確認」とした約束と、進め方の指示が食い違った
  - SonarQube の判定を、解析の反映を待たずに読んだ
- **Try**:
  - T-6: ゴール（自律の実行）を指示されたときに、計画に【要確認】が残っていれば、実行の前にまとめて確認する（AI、次の Bolt）
  - T-7: `sonar-local:gate` が解析の反映を待つよう、運用スクリプトを直す（AI、Bolt 3）

## 次の Bolt

- **Bolt 3**:
  - E2E の基盤（Playwright、axe-core、`@ui`）
  - JIG・Spring Modulith の図の生成と、用語集とクラスの整合のテスト
  - 追記専用の表の権限（DB 利用者と `afterMigrate`。レビュー R-17）
  - アプリケーション開発環境の手順書（レビュー R-11。SonarQube の手順を含める）
  - `sonar-local:gate` の反映待ち（T-7）
  - D-1 を承認する場合は、ドメインモデルの共有カーネルの図の修正
- **ゲート密度**: AT-02 と CI がそろった。Bolt 1 の決定（「AT-02 と CI が入った後に、自律実行へ移るかをもう一度判断する」）に従い、人が判断する

## 更新履歴

| 日付 | 内容 | 作成 | 承認 |
| :--- | :--- | :--- | :--- |
| 2026-10-01 | 初版 | anthropic/claude-opus-5-5 | 承認待ち |
