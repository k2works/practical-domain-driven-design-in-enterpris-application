# cargo-tracker — 開発

cargo-tracker プロジェクトの開発ドキュメントです。

## ドキュメント一覧

### リリース計画

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [リリース計画](release_plan.md) | MVP を 3 段階（最初の縦の流れ・パイロット準備完了・本格展開前）で届ける計画。Bolt と週次の見直し、SP、パイロット開始の条件、引継ぎ ID の台帳 | 承認済み |
| [開発戦略](development_strategy.md) | W1〜W14 を序盤・中盤・終盤に分けた TDD のアプローチ、週ごとのデモ項目、Living Documentation | 承認済み |

### 生成ドキュメント

コードと DB スキーマから生成します（`npx gulp docs:generate`）。公開サイトのビルド（`npm run docs:build`）でも毎回生成します。

| ドキュメント | 概要 | 生成元 |
| :--- | :--- | :--- |
| [ER 図（SchemaSpy）](../../assets/schemaspy-output/cargo-tracker/index.html){:target="_blank"} | identity・quotation・platform のスキーマの ER 図と表定義 | Flyway のマイグレーション |
| [JIG](../../assets/jig-output/cargo-tracker/index.html){:target="_blank"} | 用語集、パッケージ関連、業務ルール一覧 | `./gradlew jigReports` |

### Bolt 計画

Bolt がイテレーションに当たります。ふりかえりは各 Bolt の終了報告に含めます。

| Bolt | 計画 | 終了報告 | 週 | 状態 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | [ウォーキングスケルトン](bolt_01_plan.md) | [終了報告](bolt_01_report.md) | W1 | 完了 |
| 2 | [CI と品質の安全網](bolt_02_plan.md) | [終了報告](bolt_02_report.md) | W1 | 完了 |
| 3 | [E2E の基盤と生きたドキュメント](bolt_03_plan.md) | [終了報告](bolt_03_report.md) | W1 | 完了 |
| 4 | [業務番号と必須条件の検証（US-01）](bolt_04_plan.md) | [終了報告](bolt_04_report.md) | W1 | 完了 |
| 5 | [輸送条件の審査と差戻し（US-02）](bolt_05_plan.md) | [終了報告](bolt_05_report.md) | W1 | 完了 |
| 6 | [荷主の再提出の画面（US-02）](bolt_06_plan.md) | [終了報告](bolt_06_report.md) | W1 | 完了 |
| 7 | [見積依頼の必要書類（US-01 AC2）](bolt_07_plan.md) | [終了報告](bolt_07_report.md) | W1 | 完了 |
| 8 | [UI の骨格と見積依頼の段階入力のプロトタイプ（#36）](bolt_08_plan.md) | [終了報告](bolt_08_report.md) | W1 | 完了 |
| 9 | [Bolt 6〜8 レビューの返済（書類の差し替えと改善提案）](bolt_09_plan.md) | — | W2 | 計画済み |

Bolt を始めるときに行を追加します。

### 進捗サマリー

| 週 | 計画 SP | 実績 SP | 達成率 |
| :--- | ---: | ---: | ---: |
| W1 | 6 | 6 | 100% |
| **累計** | **6** | **6** | **100%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
| :--- | :--- | ---: | ---: | :--- |
| Release 0.1 | 最初の縦の流れ（W1〜W4） | 35 | 6 | 進行中 |
| Release 1.0 | パイロット準備完了（W5〜W10） | 54 | 0 | 未着手 |
| Release 1.1 | 本格展開前（W11〜W14） | 37 | 0 | 未着手 |

### リリース完了報告書

| リリース | 報告書 | 状態 |
| :--- | :--- | :--- |
| Release 0.1 | - | 未作成 |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
- 週の実績は [リリース計画](release_plan.md) の進捗状況を正とします。
