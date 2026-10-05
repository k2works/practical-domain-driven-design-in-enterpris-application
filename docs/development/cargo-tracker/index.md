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
| 9 | [Bolt 6〜8 レビューの返済（書類の差し替えと改善提案）](bolt_09_plan.md) | [終了報告](bolt_09_report.md) | W2 | 完了 |
| 10 | [根拠付き見積りの提示（US-03 AC1〜AC3）](bolt_10_plan.md) | [終了報告](bolt_10_report.md) | W2 | 完了 |

Bolt を始めるときに行を追加します。

### デモの動画

各 Bolt のデモ項目に当たる画面の層のシナリオを、Playwright で録画した動画です（`./gradlew demoVideo`。シナリオの `@demo-<Bolt>/<名前>` の印で選ぶ）。Bolt 9 までの動画は、2026-10-05 にいまのコードで撮り直したもので、その Bolt の時点の画面ではなく、その Bolt で作った機能のいまの振る舞いを示します（例: Bolt 5 の審査の確定の後は、Bolt 10 から見積りの作成へ進む）。

| Bolt | 内容 | デモ | 動画 |
| :--- | :--- | :--- | :--- |
| 1 | ウォーキングスケルトン | キー操作だけで輸送条件を提出し、見積依頼の詳細と社内の KPI 計測記録の一覧に同じ業務番号が出る | [submit-to-kpi.webm](../../assets/demo/bolt-01/submit-to-kpi.webm) |
|  |  | 誤りのある入力で提出すると、エラー要約から項目へ移って直し出し直せる | [input-error.webm](../../assets/demo/bolt-01/input-error.webm) |
| 2 | CI と品質の安全網 | （画面のデモなし。CI と静的解析） | — |
| 3 | E2E の基盤と生きたドキュメント | キー操作だけの提出から社内の一覧まで（画面の層の基盤） | [keyboard-submit-to-kpi.webm](../../assets/demo/bolt-03/keyboard-submit-to-kpi.webm) |
|  |  | 入口の一覧から見積依頼の作成画面へ移る | [entrance.webm](../../assets/demo/bolt-03/entrance.webm) |
| 4 | 業務番号と必須条件の検証（US-01） | 提出すると業務番号（TR-年-連番）が示される | [submit-with-number.webm](../../assets/demo/bolt-04/submit-with-number.webm) |
|  |  | 出発地と目的地を同じにし希望到着期限を空にすると、エラー要約から直して提出できる | [error-summary.webm](../../assets/demo/bolt-04/error-summary.webm) |
|  |  | 特殊貨物を選ぶと対象外と手動窓口が示され提出できない | [special-cargo.webm](../../assets/demo/bolt-04/special-cargo.webm) |
| 5 | 輸送条件の審査と差戻し（US-02） | 受付一覧から開いてキー操作だけで審査を確定する | [approve.webm](../../assets/demo/bolt-05/approve.webm) |
|  |  | 理由を入れずに差し戻すとエラー要約が出て入力が残る | [send-back-error.webm](../../assets/demo/bolt-05/send-back-error.webm) |
| 6 | 荷主の再提出の画面（US-02） | 差し戻された見積依頼を一覧から開き、理由を読み、目的地を直して出し直す | [resubmit.webm](../../assets/demo/bolt-06/resubmit.webm) |
| 7 | 見積依頼の必要書類（US-01 AC2） | 商業送り状を選んで提出し、詳細から同じファイルを取得する | [attach-and-download.webm](../../assets/demo/bolt-07/attach-and-download.webm) |
|  |  | 形式の誤ったファイルを選ぶとエラー要約で選び直しを求める | [wrong-format.webm](../../assets/demo/bolt-07/wrong-format.webm) |
|  |  | 営業担当者が審査画面で書類を取得する | [staff-download.webm](../../assets/demo/bolt-07/staff-download.webm) |
| 8 | UI の骨格と見積依頼の段階入力のプロトタイプ | 荷主の画面のナビと窓口の案内 | [customer-navigation.webm](../../assets/demo/bolt-08/customer-navigation.webm) |
|  |  | 営業の画面のナビ | [staff-navigation.webm](../../assets/demo/bolt-08/staff-navigation.webm) |
|  |  | キー操作だけで 4 段階を進み確認の段階から提出する | [stepwise-submit.webm](../../assets/demo/bolt-08/stepwise-submit.webm) |
|  |  | 確認の段階から戻って直しても選んだ書類が残る | [stepwise-documents-kept.webm](../../assets/demo/bolt-08/stepwise-documents-kept.webm) |
| 9 | Bolt 6〜8 レビューの返済 | 出し直しで商業送り状を選び直すと書類が差し替わる | [replace-documents.webm](../../assets/demo/bolt-09/replace-documents.webm) |
|  |  | 形式の誤りのエラー要約（表示名は 1 回） | [wrong-format.webm](../../assets/demo/bolt-09/wrong-format.webm) |
|  |  | ほかの項目の誤りで、選んだ書類をもう一度選ぶよう求める | [reselect-documents.webm](../../assets/demo/bolt-09/reselect-documents.webm) |
| 10 | 根拠付き見積りの提示（US-03 AC1〜AC3） | 審査を確定して見積りを算出・社内承認・提示し、荷主の詳細に見積りが出る | [present-quotation.webm](../../assets/demo/bolt-10/present-quotation.webm) |
|  |  | 料金明細を入れずに算出するとエラー要約が出て入力が残る | [quotation-error-summary.webm](../../assets/demo/bolt-10/quotation-error-summary.webm) |

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
