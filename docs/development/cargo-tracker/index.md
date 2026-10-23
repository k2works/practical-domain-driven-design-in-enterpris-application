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
| [ユーザーマニュアル（HTML）](../../assets/manual-output/cargo-tracker/index.html){:target="_blank"} | 業務担当者向けの画面の操作の手引き（`docs/manual` の Markdown を変換） | `npx gulp manual:build`（画面のキャプチャは `./gradlew manualScreenshots`） |

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
| 24 | [本予約の確定の再送と重複確定の防止（US-04 AC4）](bolt_24_plan.md) | [終了報告](bolt_24_report.md)（[動画](../../assets/demo/bolt-24/resend-booking.webm){:target="_blank"}） | W4 | 完了 |
| 25 | [追跡の開始と予約サガの成功の経路（US-04、#10）](bolt_25_plan.md) | [終了報告](bolt_25_report.md)（[動画](../../assets/demo/bolt-25/start-tracking.webm){:target="_blank"}） | W4 | 完了 |
| 25b | [S-10 予約一覧の最小の表示（S-24 へ戻る入口）](bolt_25b_plan.md) | [終了報告](bolt_25b_report.md)（[動画](../../assets/demo/bolt-25b/booking-list.webm){:target="_blank"}） | W4 | 完了 |
| 26 | [追跡管理者の入口（S-11 追跡一覧・S-12 追跡の詳細の最小の表示）](bolt_26_plan.md) | [終了報告](bolt_26_report.md)（[動画](../../assets/demo/bolt-26/tracking-records.webm){:target="_blank"}） | W4 | 完了 |
| 26b | [主要実績の登録の業務のルールと永続化（US-12 AC1・AC2）](bolt_26b_plan.md) | [終了報告](bolt_26b_report.md) | W4 | 完了 |
| 26c | [主要実績の登録の画面（US-12 AC1・AC2。S-13 の登録と S-12 の主要実績の一覧）](bolt_26c_plan.md) | [終了報告](bolt_26c_report.md)（[動画](../../assets/demo/bolt-26c/register-milestone.webm){:target="_blank"}） | W4 | 完了 |
| 27 | [荷主の追跡の照会（US-09 AC1。C-10 追跡の照会）](bolt_27_plan.md) | [終了報告](bolt_27_report.md)（[動画](../../assets/demo/bolt-27/query-tracking.webm){:target="_blank"}） | W4 | 完了 |
| 27b | [C-06 予約一覧の最小の表示（荷主）](bolt_27b_plan.md) | [終了報告](bolt_27b_report.md)（[動画](../../assets/demo/bolt-27b/customer-booking-list.webm){:target="_blank"}） | W4 | 完了 |
| 28 | [主成功の流れの `@ui` シナリオと Release 0.1 のデモ](bolt_28_plan.md) | [終了報告](bolt_28_report.md)（[動画](../../assets/demo/bolt-28/main-flow.webm){:target="_blank"}） | W4 | 完了 |
| 29 | [画面の層のステップ定義のキー操作の部品を 1 か所に集める（#45）](bolt_29_plan.md) | [終了報告](bolt_29_report.md) | W5 | 完了 |
| 29b | [マニュアルで見つけた画面の文言と表示の課題（#46 の前半）](bolt_29b_plan.md) | [終了報告](bolt_29b_report.md) | W5 | 完了 |
| 29c | [本予約の確定と経路候補の比較の画面の語（#46 の後半）](bolt_29c_plan.md) | [終了報告](bolt_29c_report.md) | W5 | 完了 |
| 30 | [ADR-011 の採用と、TOTP の資格・回復コード・ログインの失敗の表とドメイン](bolt_30_plan.md) | [終了報告](bolt_30_report.md) | W5 | 完了 |
| 31 | [TOTP の秘密の暗号化と 2 段階のログイン（A-02）](bolt_31_plan.md) | [終了報告](bolt_31_report.md)（[動画](../../assets/demo/bolt-31/two-step-login.webm){:target="_blank"}） | W5 | 完了 |
| 31b | [暗号文の AAD と初回の TOTP の登録（A-07）・回復コードの発行](bolt_31b_plan.md) | [終了報告](bolt_31b_report.md)（[動画](../../assets/demo/bolt-31b/enroll.webm){:target="_blank"}） | W5 | 完了 |
| 31c | [回復コードによるログインと password の段の主体の型](bolt_31c_plan.md) | [終了報告](bolt_31c_report.md)（[動画](../../assets/demo/bolt-31c/recovery.webm){:target="_blank"}） | W5 | 完了 |
| 31d | [認証の部品の守りの直し（照合の 3 値、暗号のパッケージ、特性テスト、スパイクの削除）](bolt_31d_plan.md) | [終了報告](bolt_31d_report.md) | W5 | 完了 |
| 32 | [ログインのロック（US-18 AC3）](bolt_32_plan.md) | [終了報告](bolt_32_report.md)（[動画](../../assets/demo/bolt-32/lock.webm){:target="_blank"}） | W5 | 完了 |
| 32c | [request ごとの利用者の確かめ（US-18 AC6）](bolt_32c_plan.md) | [終了報告](bolt_32c_report.md)（[動画](../../assets/demo/bolt-32c/revoked.webm){:target="_blank"}） | W5 | 完了 |
| 32d | [認証の部品の低の指摘とテストの整理](bolt_32d_plan.md) | [終了報告](bolt_32d_report.md) | W5 | 完了 |
| 32b | [session の失効の警告と延長（ACC-06）](bolt_32b_plan.md) | [終了報告](bolt_32b_report.md)（[動画](../../assets/demo/bolt-32b/extend.webm){:target="_blank"}） | W5 | 完了 |
| 32e | [失効の後に開いていた画面へ戻す（ACC-06）](bolt_32e_plan.md) | [終了報告](bolt_32e_report.md)（[動画](../../assets/demo/bolt-32e/return.webm){:target="_blank"}） | W5 | 完了 |
| 32f | [失効の時の入力の保持（ACC-06）](bolt_32f_plan.md) | [終了報告](bolt_32f_report.md)（[動画](../../assets/demo/bolt-32f/restore.webm){:target="_blank"}） | W5 | 完了 |
| 33 | [通知モジュールの新設と password の再設定の依頼（US-18 AC8 の前半）](bolt_33_plan.md) | [終了報告](bolt_33_report.md) | W5 | 完了 |
| 33a | [password の再設定（A-06。US-18 AC8 の後半）](bolt_33a_plan.md) | [終了報告](bolt_33a_report.md) | W5 | 完了 |
| 33b | [回復コードによる TOTP の再登録（US-18 AC9）と #13 のクローズ](bolt_33b_plan.md) | [終了報告](bolt_33b_report.md) | W5 | 完了 |
| 33c | [session の属性の名前と期間の置き場所の整理（振る舞いの変わらない直し）](bolt_33c_plan.md) | [終了報告](bolt_33c_report.md) | W5 | 完了 |
| 33d | [TOTP と回復コードの部品の整理（振る舞いの変わらない直し）](bolt_33d_plan.md) | [終了報告](bolt_33d_report.md) | W5 | 完了 |
| 33e | [認証の入口と session の型と期限の整理（振る舞いの変わらない直し）](bolt_33e_plan.md) | [終了報告](bolt_33e_report.md) | W5 | 完了 |
| 33f | [期間の表示と回復コードの型と session の URL の整理（振る舞いの変わらない直し）](bolt_33f_plan.md) | [終了報告](bolt_33f_report.md) | W5 | 完了 |
| 34 | [固定役割の付与と取消しと職務分離（US-16 の一部）](bolt_34_plan.md) | [終了報告](bolt_34_report.md)（[動画](../../assets/demo/bolt-34/change-roles.webm){:target="_blank"}。Bolt 34d で撮った） | W5 | 完了 |
| 34b | [システム管理者の管理範囲と範囲外の試行の記録（US-16 の一部）](bolt_34b_plan.md) | [終了報告](bolt_34b_report.md) | W5 | 完了 |
| 34d | [利用者の利用停止と再開（US-16 の一部）](bolt_34d_plan.md) | [終了報告](bolt_34d_report.md)（[動画](../../assets/demo/bolt-34d/suspend-and-reactivate.webm){:target="_blank"}） | W5 | 完了 |
| 34c | [認証・監査の経路と文書と見張り（振る舞いの変わらない直し）](bolt_34c_plan.md) | [終了報告](bolt_34c_report.md) | W5 | 完了 |
| 34e | [利用者の管理のドメインとサービス（振る舞いの変わらない直し）](bolt_34e_plan.md) | [終了報告](bolt_34e_report.md) | W5 | 完了 |
| 34f | [S-20 の画面の構造（振る舞いの変わらない直し）](bolt_34f_plan.md) | [終了報告](bolt_34f_report.md) | W5 | 完了 |
| 35 | [本予約の確定の不足条件と特殊貨物（US-04 AC3・AC5）](bolt_35_plan.md) | [終了報告](bolt_35_report.md) | W5 | 完了 |
| 35b | [承認の後に失効した見積りの再見積り（D-85。ドメインと業務ルール層）](bolt_35b_plan.md) | [終了報告](bolt_35b_report.md) | W5 | 完了 |
| 35b2 | [再見積りの提示で輸送要求を荷主承認待ちへ戻す（D-85。DE-24 と順序の守り）](bolt_35b2_plan.md) | [終了報告](bolt_35b2_report.md) | W5 | 完了 |
| 35c | [承認の後に失効した見積りの再見積りを社内の画面から操作する（D-85。S-02・S-04）](bolt_35c_plan.md) | [終了報告](bolt_35c_report.md) | W5 | 完了 |
| 35d | [再見積りした見積りを荷主がもう一度承認し本予約へ進める（D-85。C-02・C-04・C-17、W5 のデモ）](bolt_35d_plan.md) | [終了報告](bolt_35d_report.md)（[動画](../../assets/demo/bolt-35d/requote-and-reapprove.webm){:target="_blank"}） | W5 | 完了 |
| 35e | [荷主の承認の後に失効した見積りの開発データのサンプル（D-85。TR-2026-0908）](bolt_35e_plan.md) | [終了報告](bolt_35e_report.md) | W5 | 完了 |
| 36 | [CI の画面の層のシナリオの揺らぎを直す（#50）](bolt_36_plan.md) | [終了報告](bolt_36_report.md) | W6 | 完了 |
| 37 | [US-06 の境界の網羅（3 区間の候補・1 つだけ不足する接続・接続時間の規則の有効期間。D-90）](bolt_37_plan.md) | [終了報告](bolt_37_report.md) | W6 | 完了 |
| 37b | [US-06 AC4 の準備（テストと開発データの航海の取得時刻。情報不足の振る舞いは 37b2）](bolt_37b_plan.md) | [終了報告](bolt_37b_report.md) | W6 | 完了 |
| 37b2 | [US-06 AC4 情報不足（算出のときに古い航海の情報を使う候補を除外する）](bolt_37b2_plan.md) | [終了報告](bolt_37b2_report.md) | W6 | 完了 |
| 37c | [US-07 AC3 確定のときの情報版の照合と鮮度](bolt_37c_plan.md) | [終了報告](bolt_37c_report.md) | W6 | 完了 |
| 37d | [US-07 AC4 の準備（再設計要の記録と保存、列の削除と部分一意インデックス。評価の操作は 37d2）](bolt_37d_plan.md) | [終了報告](bolt_37d_report.md) | W6 | 完了 |
| 37d2 | [US-07 AC4 の経路設計の側（確定した経路版の評価、再設計要と DE-06、S-06 の表示）](bolt_37d2_plan.md) | [終了報告](bolt_37d2_report.md) | W6 | 完了 |
| 37e | [US-07 AC5 の見積りの側（再設計要の印、荷主の承認と本予約の判定、見積りの公開 API）](bolt_37e_plan.md) | [終了報告](bolt_37e_report.md) | W6 | 完了 |
| 37e2 | [US-07 AC5 の経路設計の側（DE-06 の listener、DE-05 と DE-06 の届く順、本予約の確定との競合の守り）](bolt_37e2_plan.md) | [終了報告](bolt_37e2_report.md) | W6 | 完了 |
| 37e3 | [US-07 AC5 の予約と画面の側（再設計要の経路の拒否の理由と文言、S-02・S-07、予約の腐敗防止層の追従）](bolt_37e3_plan.md) | [終了報告](bolt_37e3_report.md) | W6 | 完了 |

Bolt を始めるときに行を追加します。

### デモの動画

各 Bolt のデモ項目に当たる画面の層のシナリオを、Playwright で録画した動画です（`./gradlew demoVideo`。シナリオの `@demo-<Bolt>/<名前>` の印で選ぶ）。Bolt 9 までの動画は、2026-10-05 にいまのコードで撮り直したもので、その Bolt の時点の画面ではなく、その Bolt で作った機能のいまの振る舞いを示します（例: Bolt 5 の審査の確定の後は、Bolt 10 から見積りの作成へ進む）。2026-10-06 の Bolt 14 で全部を撮り直し、どの動画もログイン（A-01）を経て画面を開きます。2026-10-13 の Bolt 31 でもう一度全部を撮り直し、どの動画もログイン（A-01）と認証コードの入力（A-02）を経て画面を開きます。

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
| W2 | 8 | 7 | 88% |
| W3 | 10 | 11 | 110% |
| W4 | 11 | 11 | 100% |
| **累計** | **35** | **35** | **100%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
| :--- | :--- | ---: | ---: | :--- |
| Release 0.1 | 最初の縦の流れ（W1〜W4） | 35 | 35 | 完了（2026-10-10） |
| Release 1.0 | パイロット準備完了（W5〜W10） | 54 | 0 | 未着手 |
| Release 1.1 | 本格展開前（W11〜W14） | 37 | 0 | 未着手 |

### リリース完了報告書

| リリース | 報告書 | 状態 |
| :--- | :--- | :--- |
| Release 0.1 | [Release 0.1 リリース完了報告書](release_report-0_1.md) | 作成済み（2026-10-10） |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
- 週の実績は [リリース計画](release_plan.md) の進捗状況を正とします。
* [Bolt 23b 終了報告 - 本予約の確定の画面（US-04 AC1・AC2、#10）](./bolt_23b_report.md) - 23b 回目の Bolt の終了報告。S-02 の予約の確定待ちの表、S-09 本予約の確定（画面そのものを確認の領域にする）、S-24 予約の詳細の最小の表示を作り、見積りの公開 API の照会を業務番号と見積り番号に改めた（ADR-016 の改訂）。/goal で止まらなかった承認ゲート、開発レビューの対応（承認の後に失効した見積りの再見積りの行き止まりほか）、既知の課題を記録する。
* [Bolt 24 終了報告 - 本予約の確定の再送と重複確定の防止（US-04 AC4、B-INV-03・B-INV-11、#10）](./bolt_24_report.md) - 24 回目の Bolt の終了報告。本予約の確定にコマンド ID と処理済みコマンドを足し、同じ要求の再送には最初の結果を、同じ見積りの別の確定には既存の追跡番号を返し、確定済みの判定を見積りの照会より前に置いた。/goal で止まらなかった承認ゲート、スキーマの承認、開発レビューの対応、人に諮る設計の判断（ADR-001 の例外、追記専用と保持期間）、既知の課題を記録する。
* [Bolt 31c 計画 - 回復コードによるログインと password の段の主体の型](./bolt_31c_plan.md)
* [Bolt 31d 計画 - 認証の部品の守りの直し（照合の 3 値、暗号のパッケージ、特性テスト、スパイクの削除）](./bolt_31d_plan.md) - W5 の 8 本目の Bolt の計画。Bolt 31・31b・31c から送った構造の直しのうち守りに関わるものを片づける。TOTP の照合の結果（一致・再利用・不一致）をドメインに寄せ、暗号の部品を identity.infrastructure.crypto に分けて層の規則 D-5 の認証の例外を入口だけに狭め、FACTOR_* の ArchUnit の空振り・全 URL の見張り・ログの平文の見張り・枠組みの特性テストを足し、スパイクを本体のテストとの対応を確かめてから消す。画面は変えない。
* [Bolt 32 計画 - ログインのロック（US-18 AC3）](./bolt_32_plan.md) - W5 の 9 本目の Bolt の計画。password・TOTP・回復コードの失敗が 5 回続いたら 15 分ロックする（US-18 AC3、IA-INV-03）。失敗の数え方と監査記録をアプリケーションのサービスで 1 つのトランザクションにまとめ（ADR-012 の見直しの 5、IA-INV-08）、ロック中も誤りと同じ応答にして存在を漏らさない（AC2）。Bolt 31d から送った例外の親の型と、実装の誤りは 500 の端から端までの確かめも入れる。AC6 と見直しの 3 は Bolt 32c。
* [Bolt 32 終了報告 - ログインのロック（US-18 AC3）](./bolt_32_report.md) - 誤りを合わせて 5 回でロックし、ロック中も誤りと同じ応答と予告にした。数え方と監査記録は LoginAttemptService の 1 つのトランザクション。入口の誤りの扱いを統合テストと特性テストで固定した。計画からの逸脱（ロックの読み出しの DB の誤りは 500）と、画面の文言・監査の書き込みの置き場所の提案を承認の議題に置く。
* [Bolt 32c 計画 - request ごとの利用者の確かめ（US-18 AC6）](./bolt_32c_plan.md) - W5 の 10 本目の Bolt の計画。利用停止・所属企業の無効化・権限の取消しを既存の session の次の request から反映する（US-18 AC6、IA-INV-04、SEC-12 の役割と利用状態の分）。request ごとに利用者・役割・企業を 1 回の SQL で読み、アプリケーションのサービスが判定して主体を作り直す（ADR-012 の見直しの 3）。止められた利用者の session は捨てて A-03 に送り SESSION_REVOKED を記録し、取り消した役割の URL は A-04 にする。31d から送った低の指摘とテストの整理は Bolt 32d。
* [Bolt 32c 終了報告 - request ごとの利用者の確かめ（US-18 AC6）](./bolt_32c_report.md) - 認証済みの request ごとに利用者・役割・企業を 1 回の SQL で読み、主体を作り直すようにした。止められた利用者は session を捨てて A-03 と SESSION_REVOKED、取り消した役割の URL は A-04。開発レビューで静的な資源でも読んでいたことを直した。A-03 の文言とロックの扱いを承認の議題に置く。
* [Bolt 32d 計画 - 認証の部品の低の指摘とテストの整理](./bolt_32d_plan.md) - W5 の 11 本目の Bolt の計画。Base32 の不正な長さを失敗へ倒し、identity の合成ルートの組み立て方を規則にそろえ、統合テストの共通の時計の設定と spy の基底で Spring のコンテキストを減らし、テストの補助の共通化・同時の待ちの判定・dev のテストの刻みの境界・8 時間の起点の値と監査の順・ロックのシナリオの axe を直す。
* [Bolt 32d 終了報告 - 認証の部品の低の指摘とテストの整理](./bolt_32d_report.md) - Base32 の不正な形を失敗へ倒し、identity の合成ルートの規則を見張りで守り、統合テストの時計と spy を共通にして Spring のコンテキストを 50 から 42 に減らした。同時の待ちを PostgreSQL のロックの待ちで判定する。合成ルートの dev の例外と開発レビューで強めたことを承認の議題に置く。
* [Bolt 32b 計画 - session の失効の警告と延長（ACC-06）](./bolt_32b_plan.md) - W5 の 12 本目の Bolt の計画。ログインした画面で無操作 30 分の失効の 5 分前に警告し、画面を移らずに延長できるようにする。8 時間の上限も 5 分前に警告し（延長はできない）、警告の時刻をタブの間でそろえる。入力の保持と開いていた画面への復帰は Bolt 32e に分ける。
* [Bolt 32b 終了報告 - session の失効の警告と延長（ACC-06）](./bolt_32b_report.md) - ログインした画面で無操作 30 分の失効の 5 分前に警告し、画面を移らずに延長できるようにした。8 時間の上限も 5 分前に警告し、警告の時刻をタブの間でそろえ、期限を過ぎたら画面を移らずに案内を出す。支援技術の手動確認と、8 時間の警告の時刻・A-03 の文言の扱いを承認の議題に置く。
* [Bolt 32e 計画 - 失効の後に開いていた画面へ戻す（ACC-06）](./bolt_32e_plan.md) - W5 の 13 本目の Bolt の計画。無操作 30 分と 8 時間の上限で失効した後、もう一度ログインすると開いていた画面へ戻す。失効を見つけたときに開いていた画面の要求を新しい session に覚え、警告の期限の到来の案内のリンクは開いていた画面を開き直す。入力の保持は Bolt 32f。
* [Bolt 32e 終了報告 - 失効の後に開いていた画面へ戻す（ACC-06）](./bolt_32e_report.md) - 無操作 30 分と 8 時間の上限で失効した後、もう一度ログインすると GET で開いていた業務の画面に戻るようにした。失効の扱いを invalidSessionStrategy に替え、認証コードの誤りと password の段の失効では覚えた要求を持ち越す。警告の案内のリンクは戻り先を開き直す。
* [Bolt 32f 計画 - 失効の時の入力の保持（ACC-06）](./bolt_32f_plan.md) - W5 の 14 本目の Bolt の計画。失効の時に送信していない入力をブラウザのそのタブに「入力の控え」として写し、同じ利用者が同じ画面に戻ったら自動で戻して知らせる。送信が公開の画面で拒まれたら、ログインし直した後の画面に案内を出す。案内のリンクの語を「画面を開き直す」にし、ADR-005 の例外として記録する。
* [Bolt 32f 終了報告 - 失効の時の入力の保持（ACC-06）](./bolt_32f_report.md) - 失効の時に送信していない入力をブラウザのそのタブ（sessionStorage）に控え、同じ利用者が同じ画面に戻ったら戻すようにした。送信が拒まれたらログインし直した後の画面に案内を出す。開発レビューで拒まれた印を画面ごとの明示に変え、文言を直した。支援技術の手動確認は人が済ませた。
* [Bolt 33 計画 - 通知モジュールの新設と password の再設定の依頼（US-18 AC8 の前半）](./bolt_33_plan.md) - W5 の 15 本目の Bolt の計画。通知モジュールを最小に新設し（送信の公開 API、送信の記録、SMTP と dev のログに出す送信）、A-01 の「password を忘れた場合」から A-05 で依頼すると、1 回だけ使える 30 分のリンクを登録メールアドレスへ送る（登録の有無を示さない）。A-06 は Bolt 33a。
* [Bolt 33 終了報告 - 通知モジュールの新設と password の再設定の依頼（US-18 AC8 の前半）](./bolt_33_report.md) - 通知モジュールを新設し、A-05 で依頼するとコミットの後に 1 回だけ使える 30 分のリンクを送るようにした（登録の有無を示さない、平文を残さない）。開発レビューで、ポートの置き場所の規則、SMTP のタイムアウトと at-least-once、仮の A-06、W10 に送る残り（#49）を人が決めた。
* [Bolt 33a 計画 - password の再設定（A-06。US-18 AC8 の後半）](./bolt_33a_plan.md) - W5 の 16 本目の Bolt の計画。リンクを開くとトークンを session に移してトークンのない URL の A-06 で新しい password を設定する（SEC-04・SEC-05）。設定したら session をすべて失効させ、A-01 に戻してログインし直す。
* [Bolt 33a 終了報告 - password の再設定（A-06。US-18 AC8 の後半）](./bolt_33a_report.md) - リンクを開くとトークンの ID だけを session に置いて A-06 へ移し、新しい password を設定するとトークンを 1 回だけ使い、コミットの後にその利用者の session をすべて失効させた。開発レビューで session の ID の変更と、今の session を捨てる順序を決めた。
* [Bolt 33b 計画 - 回復コードによる TOTP の再登録（US-18 AC9）と #13 のクローズ](./bolt_33b_plan.md) - W5 の 17 本目の Bolt の計画。回復コードでログインしたら A-08 へ導いて認証アプリを再登録し（後にもできる）、秘密を置き換えて回復コードを発行し直し、ほかの session を失効させる。マニュアル 2.1 を 2 段階のログインに書き直し、#13 をクローズする。
* [Bolt 33b 終了報告 - 回復コードによる TOTP の再登録（US-18 AC9）と #13 のクローズ](./bolt_33b_report.md) - 回復コードでログインしたら A-08 へ導き、読んだ秘密のままのときだけ TOTP の秘密を置き換えて回復コードを発行し直し、ほかの session を失効させた。開発レビューで印に 15 分の期限を付けた。
* [Bolt 33c 計画 - session の属性の名前と期間の置き場所の整理（振る舞いの変わらない直し）](./bolt_33c_plan.md) - W5 の 18 本目の Bolt の計画。認証の入口と画面に 2 つの定数で持っていた session の属性の名前と URL を application 層の SessionKeys に、期間を domain の SessionPolicy に寄せ、覚えた要求の属性の名前を共有する。TOTP の部品の整理は 33d。
* [Bolt 33c 終了報告 - session の属性の名前と期間の置き場所の整理（振る舞いの変わらない直し）](./bolt_33c_report.md) - session の属性の名前と URL を SessionKeys に、期間を SessionPolicy に寄せ、規則の例外を 1 つ足してふるまいを持たないことを見張った。覚えた要求は Bean にせず名前だけを共有した。
* [Bolt 33d 計画 - TOTP と回復コードの部品の整理（振る舞いの変わらない直し）](./bolt_33d_plan.md) - W5 の 19 本目の Bolt の計画。TOTP の桁数・アルゴリズムの定数を 1 か所にし、QR コードの SVG の組み立てを秘密の発行から分け、回復コードの形の知識を RecoveryCode に寄せ、normalize の戻り値を値オブジェクトにし、A-07 と A-08 で重複していた途中の暗号文を使い続ける処理と回復コード 10 個の発行を小さな部品に切り出す。振る舞いは変えない。認証の入口と期限の直しは 33e に分ける。
* [Bolt 33d 終了報告 - TOTP と回復コードの部品の整理（振る舞いの変わらない直し）](./bolt_33d_report.md) - TOTP の桁数とアルゴリズムを 1 か所にし、QR の SVG を分け、回復コードの形の知識を NormalizedRecoveryCode に寄せ、A-07 と A-08 の重複を小さな部品で共有した。振る舞いは変えていない。
* [Bolt 33e 計画 - 認証の入口と session の型と期限の整理（振る舞いの変わらない直し）](./bolt_33e_plan.md) - W5 の 20 本目の Bolt の計画。認証の結果のトークンに経路を持たせ、残す session の ID を値オブジェクトにし、期限の判定を SessionPolicy に寄せ、画面だけが使う属性の名前の値を固定する。画面と回復コードと文書の直しは 33f。
* [Bolt 33e 終了報告 - 認証の入口と session の型と期限の整理（振る舞いの変わらない直し）](./bolt_33e_report.md) - 期限の判定を SessionPolicy に寄せ、残す session の ID を SessionId にし、回復コードの経路を認証の結果のトークンが transient の欄で持つようにした。振る舞いは変えていない。
* [Bolt 33f 計画 - 期間の表示と回復コードの型と session の URL の整理（振る舞いの変わらない直し）](./bolt_33f_plan.md) - W5 の 21 本目の Bolt の計画。期限の時刻を SessionPolicy.deadline にまとめ、テンプレートの「15 分」「8 時間」を SessionPolicy から描き、回復コードの生成のポートを NormalizedRecoveryCode で受け、SessionKeys から URL を別のクラスに分ける。
* [Bolt 33f 終了報告 - 期間の表示と回復コードの型と session の URL の整理（振る舞いの変わらない直し）](./bolt_33f_report.md) - 期限の時刻を SessionPolicy.deadline に寄せ、テンプレートの期間を SessionPolicy から描き、URL を SessionPaths に分け、回復コードの生成のポートを値で受けるようにした。振る舞いと表示は変えていない。
* [Bolt 33e 計画 - 認証の入口と session の型と期限の整理（振る舞いの変わらない直し）](./bolt_33e_plan.md) - W5 の 20 本目の Bolt の計画。認証の結果のトークンに経路（TOTP か回復コードか）を持たせて request の属性での受け渡しをやめ、revokeOthers が残す session の ID を値オブジェクトにし、「ちょうどの時刻は期限切れ」の判定を SessionPolicy に寄せ、画面だけが使う属性の名前 2 つの値をテストで固定する。振る舞いは変えない。テンプレートの期間の数値、回復コードの生成のポートの型、ADR-012 の URL の記述は 33f に分ける。
* [Bolt 33f 計画 - 期間の表示と回復コードの型と session の URL の整理（振る舞いの変わらない直し）](./bolt_33f_plan.md) - W5 の 21 本目の Bolt の計画。期限の時刻を SessionPolicy.deadline にまとめ、テンプレートに直書きした「15 分」「8 時間」を SessionPolicy から描き、回復コードの生成のポートと RecoveryCode.issueSet を NormalizedRecoveryCode で受け、SessionKeys から URL 2 つを同じパッケージの別のクラスに分けて ADR-012 を直す。振る舞いと表示は変えない。
* [Bolt 33f 終了報告 - 期間の表示と回復コードの型と session の URL の整理（振る舞いの変わらない直し）](./bolt_33f_report.md) - W5 の 21 本目の Bolt の終了報告。期限の時刻を SessionPolicy.deadline に寄せ、期間の汎用の書式 DurationDisplay.length を足し、テンプレート 6 か所の「15 分」「8 時間」を SessionPolicy から描き、URL を SessionKeys から SessionPaths に分け（password の再設定の URL も寄せた）、回復コードの生成のポートと issueSet を NormalizedRecoveryCode で受けるようにした。振る舞いと表示は変えていない。
* [Bolt 34b 終了報告 - システム管理者の管理範囲と範囲外の試行の記録（US-16 の一部）](./bolt_34b_report.md) - W5 の 23 本目の Bolt の終了報告。システム管理者は user_assignment（COMPANY）で割り当てた企業の利用者だけを一覧・詳細・役割の変更で扱え、範囲外は存在しない利用者と同じ 404 になる。役割の変更の試行は確定の POST でだけ ADMIN_SCOPE_DENIED に記録する（開発レビューで GET の記録をやめた）。共通の 404 の画面を足した。
* [Bolt 34f 終了報告 - S-20 の画面の構造（振る舞いの変わらない直し）](./bolt_34f_report.md)
* [Bolt 35 終了報告 - 本予約の確定の不足条件と特殊貨物（US-04 AC3・AC5）](./bolt_35_report.md) - W5 の 28 本目の Bolt の終了報告。本予約の確定（S-09）で不足条件をまとめて示し（AC3）、特殊貨物を含む見積りの確定を予約の規則 BookableCargoPolicy で拒んで、S-09 を開いたときから MVP の対象外と手動の窓口を示す（AC5、B-INV-04）。特殊貨物の見積りは見積りの側の受入テストのステップで提出の規則を通さずに置き、予約は本物の公開 API を通して確かめた。開発レビューの後に、拒んだ結果を画面に渡す形、S-09 のエラー要約へのフォーカス、表の文言を直した。
* [Bolt 35b 終了報告 - 承認の後に失効した見積りの再見積り（D-85。ドメインと業務ルール層）](./bolt_35b_report.md) - W5 の 29 本目の Bolt の終了報告。荷主の承認の後に有効期限を過ぎた見積りを再見積りできるようにし、新しい見積りは旧版の経路版・荷主の詳細経路設計の回答・経路方針を引き継いで、社内承認して提示すると荷主承認待ちに直行する。荷主が再承認すれば新しい見積りで本予約を確定できる。開発レビューの後に、引き継ぐ経路方針を旧版の値にし、判定・照合・引き継ぎ・置き換えを旧版の 1 つの操作にまとめた。スキーマは変えていない。
* [Bolt 35b2 終了報告 - 再見積りの提示で輸送要求を荷主承認待ちへ戻す（D-85。DE-24 と順序の守り）](./bolt_35b2_report.md) - W5 の 30 本目の Bolt の終了報告。荷主の承認の後に失効した見積りを再見積りして提示したら、DE-24 で輸送要求を予約待ちから荷主承認待ちへ戻す。届く順の入れ替わりは、輸送要求を読んでから見積りを読む順と、DE-04・DE-24 の listener が進めなくても書く楽観ロックで守り、置換済み・失効の見積りの DE-21・DE-04 は進めない（ADR-018）。差し込みのテストをメモリの実装と PostgreSQL で確かめた。スキーマは変えていない。
* [Bolt 35c 終了報告 - 承認の後に失効した見積りの再見積りを社内の画面から操作する（D-85。S-02・S-04）](./bolt_35c_report.md) - W5 の 31 本目の Bolt の終了報告。荷主の承認の後に有効期限を過ぎた見積りを、営業担当者が受付一覧（S-02）の予約の確定待ちの表から再見積りし、引き継ぐ経路方針と経路版を読み取り専用で確かめ、算出した見積り 2 を同じ表から開いて社内承認して提示できるようにした。再見積りできるかの判定はドメインの規則 RequotePolicy にまとめ、ボタン・GET・POST が同じ理由を示す。マニュアル 6・7 章・付録 B を直し、キャプチャを撮った。スキーマは変えていない。
