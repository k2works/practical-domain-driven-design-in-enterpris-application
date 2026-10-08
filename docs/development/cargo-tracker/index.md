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
| 11 | [見積りの失効と置換（US-03 AC4・AC5）](bolt_11_plan.md) | [終了報告](bolt_11_report.md) | W2 | 完了 |
| 12 | [詳細経路設計へ進む回答（US-24 AC1）](bolt_12_plan.md) | [終了報告](bolt_12_report.md) | W2 | 完了 |
| 13 | [TOTP と Spring Security 7 の多要素認証のスパイク（TS-01）](bolt_13_plan.md) | [終了報告](bolt_13_report.md) | W2 | 完了 |
| 14 | [password によるログインと session（US-18 の一部）](bolt_14_plan.md) | [終了報告](bolt_14_report.md) | W2 | 完了 |
| 15 | [dev プロファイルによる Heroku のデモ環境（技術タスク）](bolt_15_plan.md) | [終了報告](bolt_15_report.md) | W3 | 完了 |
| 16 | [デモ環境への CI からの配備（技術タスク）](bolt_16_plan.md) | [終了報告](bolt_16_report.md) | W3 | 完了 |
| 17 | [経路候補の算出と比較（US-06 AC1〜AC3）](bolt_17_plan.md) | [終了報告](bolt_17_report.md)（[動画](../../assets/demo/bolt-17/compare-route-candidates.webm)） | W3 | 完了 |
| 18 | [デモ環境のサンプルの業務データ（技術タスク）](bolt_18_plan.md) | — | W3 | 完了 |
| 19 | [判断根拠を記録して経路を確定する（US-07 AC1・AC2）](bolt_19_plan.md) | [終了報告](bolt_19_report.md)（[動画](../../assets/demo/bolt-19/confirm-route.webm){:target="_blank"}） | W3 | 完了 |
| 20 | [確定した経路の割当てと荷主の承認（US-24 AC4・AC5）](bolt_20_plan.md) | [終了報告](bolt_20_report.md)（[動画](../../assets/demo/bolt-20/approve-quotation-and-route.webm){:target="_blank"}） | W3 | 完了 |
| 21 | [KPI-01 の提示時刻とリードタイム（US-21 AC1）](bolt_21_plan.md) | [終了報告](bolt_21_report.md)（[動画](../../assets/demo/bolt-21/kpi-01-lead-time.webm){:target="_blank"}） | W3 | 完了 |
| 22 | [日時表示・期間表示を platform の Web の部品に集める（#41）](bolt_22_plan.md) | [終了報告](bolt_22_report.md) | W4 | 完了 |
| 23 | [本予約の確定と失効（US-04 AC1・AC2）](bolt_23_plan.md) | [終了報告](bolt_23_report.md) | W4 | 完了 |
| 23b | [本予約の確定の画面（US-04 AC1・AC2）](bolt_23b_plan.md) | [終了報告](bolt_23b_report.md)（[動画](../../assets/demo/bolt-23b/confirm-booking.webm){:target="_blank"}） | W4 | 完了 |

Bolt を始めるときに行を追加します。

### デモの動画

各 Bolt のデモ項目に当たる画面の層のシナリオを、Playwright で録画した動画です（`./gradlew demoVideo`。シナリオの `@demo-<Bolt>/<名前>` の印で選ぶ）。Bolt 9 までの動画は、2026-10-05 にいまのコードで撮り直したもので、その Bolt の時点の画面ではなく、その Bolt で作った機能のいまの振る舞いを示します（例: Bolt 5 の審査の確定の後は、Bolt 10 から見積りの作成へ進む）。2026-10-06 の Bolt 14 で全部を撮り直し、どの動画もログイン（A-01）を経て画面を開きます。

| Bolt | 内容 | デモ | 動画 |
| :--- | :--- | :--- | :--- |
| 1 | ウォーキングスケルトン | キー操作だけで輸送条件を提出し、見積依頼の詳細と社内の KPI 計測記録の一覧に同じ業務番号が出る | [submit-to-kpi.webm](../../assets/demo/bolt-01/submit-to-kpi.webm){:target="_blank"} |
|  |  | 誤りのある入力で提出すると、エラー要約から項目へ移って直し出し直せる | [input-error.webm](../../assets/demo/bolt-01/input-error.webm){:target="_blank"} |
| 2 | CI と品質の安全網 | （画面のデモなし。CI と静的解析） | — |
| 3 | E2E の基盤と生きたドキュメント | キー操作だけの提出から社内の一覧まで（画面の層の基盤） | [keyboard-submit-to-kpi.webm](../../assets/demo/bolt-03/keyboard-submit-to-kpi.webm){:target="_blank"} |
| 4 | 業務番号と必須条件の検証（US-01） | 提出すると業務番号（TR-年-連番）が示される | [submit-with-number.webm](../../assets/demo/bolt-04/submit-with-number.webm){:target="_blank"} |
|  |  | 出発地と目的地を同じにし希望到着期限を空にすると、エラー要約から直して提出できる | [error-summary.webm](../../assets/demo/bolt-04/error-summary.webm){:target="_blank"} |
|  |  | 特殊貨物を選ぶと対象外と手動窓口が示され提出できない | [special-cargo.webm](../../assets/demo/bolt-04/special-cargo.webm){:target="_blank"} |
| 5 | 輸送条件の審査と差戻し（US-02） | 受付一覧から開いてキー操作だけで審査を確定する | [approve.webm](../../assets/demo/bolt-05/approve.webm){:target="_blank"} |
|  |  | 理由を入れずに差し戻すとエラー要約が出て入力が残る | [send-back-error.webm](../../assets/demo/bolt-05/send-back-error.webm){:target="_blank"} |
| 6 | 荷主の再提出の画面（US-02） | 差し戻された見積依頼を一覧から開き、理由を読み、目的地を直して出し直す | [resubmit.webm](../../assets/demo/bolt-06/resubmit.webm){:target="_blank"} |
| 7 | 見積依頼の必要書類（US-01 AC2） | 商業送り状を選んで提出し、詳細から同じファイルを取得する | [attach-and-download.webm](../../assets/demo/bolt-07/attach-and-download.webm){:target="_blank"} |
|  |  | 形式の誤ったファイルを選ぶとエラー要約で選び直しを求める | [wrong-format.webm](../../assets/demo/bolt-07/wrong-format.webm){:target="_blank"} |
|  |  | 営業担当者が審査画面で書類を取得する | [staff-download.webm](../../assets/demo/bolt-07/staff-download.webm){:target="_blank"} |
| 8 | UI の骨格と見積依頼の段階入力のプロトタイプ | 荷主の画面のナビと窓口の案内 | [customer-navigation.webm](../../assets/demo/bolt-08/customer-navigation.webm){:target="_blank"} |
|  |  | 営業の画面のナビ | [staff-navigation.webm](../../assets/demo/bolt-08/staff-navigation.webm){:target="_blank"} |
|  |  | キー操作だけで 4 段階を進み確認の段階から提出する | [stepwise-submit.webm](../../assets/demo/bolt-08/stepwise-submit.webm){:target="_blank"} |
|  |  | 確認の段階から戻って直しても選んだ書類が残る | [stepwise-documents-kept.webm](../../assets/demo/bolt-08/stepwise-documents-kept.webm){:target="_blank"} |
| 9 | Bolt 6〜8 レビューの返済 | 出し直しで商業送り状を選び直すと書類が差し替わる | [replace-documents.webm](../../assets/demo/bolt-09/replace-documents.webm){:target="_blank"} |
|  |  | 形式の誤りのエラー要約（表示名は 1 回） | [wrong-format.webm](../../assets/demo/bolt-09/wrong-format.webm){:target="_blank"} |
|  |  | ほかの項目の誤りで、選んだ書類をもう一度選ぶよう求める | [reselect-documents.webm](../../assets/demo/bolt-09/reselect-documents.webm){:target="_blank"} |
| 10 | 根拠付き見積りの提示（US-03 AC1〜AC3） | 審査を確定して見積りを算出・社内承認・提示し、荷主の詳細に見積りが出る | [present-quotation.webm](../../assets/demo/bolt-10/present-quotation.webm){:target="_blank"} |
|  |  | 料金明細を入れずに算出するとエラー要約が出て入力が残る | [quotation-error-summary.webm](../../assets/demo/bolt-10/quotation-error-summary.webm){:target="_blank"} |
| 11 | 見積りの失効と置換（US-03 AC4・AC5） | 提示した見積りからキー操作だけで再見積りし、受付一覧の見積提示済みから新しい見積りを開き、旧版の置換済みと荷主の案内を確かめる | [requote-quotation.webm](../../assets/demo/bolt-11/requote-quotation.webm){:target="_blank"} |
|  |  | 有効期限を過ぎた承認待ちの見積りは失効と示され、提示できず再見積りの操作がある | [expired-quotation.webm](../../assets/demo/bolt-11/expired-quotation.webm){:target="_blank"} |
| 12 | 詳細経路設計へ進む回答（US-24 AC1） | 荷主が C-04 からキー操作だけで C-05 を開いて詳細経路設計へ進み、経路設計中（A 社の対応待ち）を確かめ、営業が受付一覧の経路設計中から見積りを読み取り専用で開く | [request-route-design.webm](../../assets/demo/bolt-12/request-route-design.webm){:target="_blank"} |
|  |  | 有効期限を過ぎた見積りの回答の画面を開くと、理由が示されて見積依頼の詳細に戻り、回答の入口がない | [expired-response.webm](../../assets/demo/bolt-12/expired-response.webm){:target="_blank"} |
| 14 | password によるログインと session（US-18 の一部） | 荷主担当者がログインし、荷主のホームとナビとヘッダーを確かめ、社内の受付一覧の URL を直接開くと権限なしになる | [shipper-login.webm](../../assets/demo/bolt-14/shipper-login.webm){:target="_blank"} |
|  |  | 営業担当者がログインし、社内のナビとヘッダーを確かめ、荷主の画面の URL を直接開くと権限なしになる | [staff-login.webm](../../assets/demo/bolt-14/staff-login.webm){:target="_blank"} |
|  |  | 誤った password でログインしようとすると、どれが誤りかを示さない文言が出て、メールアドレスだけが残る | [wrong-password.webm](../../assets/demo/bolt-14/wrong-password.webm){:target="_blank"} |
| 15 | dev プロファイルによる Heroku のデモ環境（技術タスク） | 公開の URL で荷主担当者がログインして見積依頼を提出し、営業担当者が受付一覧から開いて審査を確定し、荷主の画面は権限なしになる（`demoVideo` ではなく、公開の URL を Playwright で録画した。`demoVideo` が消さないよう `docs/assets/heroku-demo` に置く） | [submit-review-on-heroku.webm](../../assets/heroku-demo/bolt-15/submit-review-on-heroku.webm){:target="_blank"} |

### 進捗サマリー

| 週 | 計画 SP | 実績 SP | 達成率 |
| :--- | ---: | ---: | ---: |
| W1 | 6 | 6 | 100% |
| W2 | 8 | 5 | 進行中 |
| **累計** | **14** | **11** | — |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
| :--- | :--- | ---: | ---: | :--- |
| Release 0.1 | 最初の縦の流れ（W1〜W4） | 35 | 11 | 進行中 |
| Release 1.0 | パイロット準備完了（W5〜W10） | 54 | 0 | 未着手 |
| Release 1.1 | 本格展開前（W11〜W14） | 37 | 0 | 未着手 |

### リリース完了報告書

| リリース | 報告書 | 状態 |
| :--- | :--- | :--- |
| Release 0.1 | - | 未作成 |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
- 週の実績は [リリース計画](release_plan.md) の進捗状況を正とします。
* [Bolt 23b 終了報告 - 本予約の確定の画面（US-04 AC1・AC2、#10）](./bolt_23b_report.md) - 23b 回目の Bolt の終了報告。S-02 の予約の確定待ちの表、S-09 本予約の確定（画面そのものを確認の領域にする）、S-24 予約の詳細の最小の表示を作り、見積りの公開 API の照会を業務番号と見積り番号に改めた（ADR-016 の改訂）。/goal で止まらなかった承認ゲート、開発レビューの対応（承認の後に失効した見積りの再見積りの行き止まりほか）、既知の課題を記録する。
