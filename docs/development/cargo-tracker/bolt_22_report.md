---
type: Report
title: "Bolt 22 終了報告 - 日時表示・期間表示を platform の Web の部品に集める（#41）"
description: "22 回目の Bolt の終了報告。見積り・経路設計・アクセス監査の日時表示・期間表示の写しを、画面の文字列を変えずに platform :: web の部品に集め、写しの再発を ArchUnit で止めた。開発レビューの対応、品質ゲート、計画からの変更、既知の課題（DE-03 と DE-16 の listener の競合）と承認の議題を記録する。"
tags: [development,bolt-report]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-08T06:24:11Z }
---

# Bolt 22 終了報告 - 日時表示・期間表示を platform の Web の部品に集める（#41）

## 承認の議題（T-36。先に確かめてほしいこと）

W4 の計画どおり `/goal` で、計画の承認から終了報告の前まで、承認ゲートで止まらずに進めた。止まらなかったゲートごとの判断を次に置く。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | ステップ 1〜4 を計画どおり進めた（特性テスト → 部品 → 置き換え → 設計文書） | 画面の文字列は変えていない。`test`・`uiTest`・`documentationTest` は期待の文字列を変えずに通った | ステップの結果 |
| 2 | **計画からの変更 1**: 部品は `UtcInstant` でなく `java.time.Instant` を受ける | 既存の ArchUnit の規則「platform は `java..` だけに依存する」を保ち、`platform` を `shared` に依存させないため。各モジュールに `UtcInstant` を `Instant` に直す 1 行のメソッドを残した | `platform/web/DateTimeDisplay.java` |
| 3 | **計画からの変更 2**: 確認ポイント 7 の規則を「`DateTimeFormatter` を使えない」から「書式はフォームの変換（`*FormConverter`）だけ、タイムゾーンは部品の `ZONE` だけ」に絞った | 見積りのフォームの変換が入力の解釈に `DateTimeFormatter` を使っており、計画の形では入力の解釈まで禁じてしまう。フォームの変換のタイムゾーンも部品の `ZONE` に寄せた（利用者ごとのタイムゾーンを入れるときに直す場所が 1 つになる） | `LayerArchitectureTest` の `画面の層は日時表示の写しを持たない` |
| 4 | **構造の変更**: `routing`・`identity` の `allowedDependencies` に `platform :: web` を足し、`quotation` に `allowedDependencies = {"shared", "platform :: web"}` を新しく宣言した | `quotation` は宣言がなく、どのモジュールの公開部分にも暗黙に依存できた（開発レビュー A-2）。上流なので、ADR-014 の「上流は下流に依存しない」を宣言でも固定した。ModularityTest は通る | 各 `package-info.java` |
| 5 | **ADR を書かず、ADR-001 のコンプライアンスに例外を 1 行足した**（確認ポイント 2 は「書かない」。開発レビュー A-1） | ADR-001 は「モジュール間の依存は公開 API とイベントだけ」と書いており、`platform :: web` はどちらでもない。新しい ADR にするほどの決定ではないが、W4 以降のすべてのモジュールが宣言する依存先なので、ADR-001 に記録した | [ADR-001](../../adr/cargo-tracker/001-modular-monolith.md) のコンプライアンスと改訂の経緯 |
| 6 | 開発レビューの対応の範囲（下の「開発レビュー」）。直したもの 10 件、後に回したもの 3 件 | 写しの再発を止める規則の穴（`ZoneId.systemDefault` など）と、特性テストの境界の不足は、この Bolt の目的そのものなので直した。振る舞いを変えるもの（負の期間の扱い）と、まだ使わない拡張（`ZoneId` を受けるオーバーロード）は後に回した | 開発レビュー |
| 7 | **既知の課題（この Bolt の変更とは関係ない）**: 見積りの DE-03 と DE-16 の listener が同じ輸送要求を並行して更新し、DE-16 の側が楽観ロックの競合で失敗することがある。失敗した発行は起動し直すまで再配信されない | SonarQube の走査で `RouteDesignRequestedRoutingIntegrationTest` が 1 回失敗し、ログから原因を特定した（下の「既知の課題」）。Bolt 12 からある競合で、この Bolt の範囲外。別のタスクとして提案した | 既知の課題 |
| 8 | ステップ 1 の特性テスト（`ba3cf57`。部品がないのでコンパイルで失敗）とステップ 2（`780d6a5`。写しを禁じる規則が Red）を Red のまま push したので、CI の `check` が 2 回赤になった（`deploy-demo` は走らず、デモ環境は変わっていない）。次の push（`ad20122`）で緑に戻した | ローカルでは、それぞれの失敗は想定した Red だけだった。CI のログの末尾からは原因を確かめられていない。Try T-65 | CI の実行 37734340317 ほか |
| 9 | #41 をクローズする | 完了条件をすべて満たした | [#41](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/41) |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 22 回 |
| 期間 | 2026-10-08 14:45 JST（計画の承認 `75f681a`）〜 15:30 JST（本報告） |
| 対象 | `platform.web`（新設）、`quotation`・`routing`・`identity` の `interfaces.web` |
| GitHub | [#41](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/41)（SP 0） |
| 計画 | [Bolt 22 計画](bolt_22_plan.md) |

## 成果

### ステップの結果

| ステップ | 結果 | コミット | 備考 |
| :--- | :--- | :--- | :--- |
| 1. 特性テスト（Red） | 完了 | `22847b2`、`ba3cf57` | 部品がないのでコンパイルで失敗することを確かめた |
| 2. 部品と依存の宣言（Green） | 完了 | `780d6a5` | 部品の表のテストは Green。写しを禁じる規則は、3 つの写し（各 4 件）を検出して Red |
| 3. 写しの置き換え（Refactor） | 完了 | `a0db349` | 画面の文字列は変えていない |
| 4. 設計文書 | 完了 | `ad20122` | architecture_backend.md の `platform` の規則、ui_design.md の日時表示に期間の 3 つの形 |
| 5. 開発レビューと終了報告 | 完了 | `32a53af`、本報告 | 開発レビュー（プログラマー・アーキテクト）、SonarQube |

### 作ったもの

- `platform.web.DateTimeDisplay`（`ZONE`、`customer`、`staff`）と `platform.web.DurationDisplay`（`waiting`、`connection`、`hoursAndMinutes`）。名前付きインターフェース `platform :: web`
- 部品の表のテスト（`DateTimeDisplayTest`、`DurationDisplayTest`。境界と、負の期間のいまの値を特性として固定）
- ArchUnit の規則: `platformの表示の部品は画面の層からだけ参照される`、`画面の層は日時表示の写しを持たない`（`DateTimeFormatter` はフォームの変換だけ、`ZoneId.of`・`ZoneId.systemDefault`・`ZoneOffset.of*` を画面の層で呼ばない）。規則が `ZoneOffset.ofHours` と `ZoneId.systemDefault` を検出することを、一時的に写しを入れて確かめた
- `KpiObservationViewTest` を、書式のテストから「リードタイムと未提示の経過時間はどちらか一方だけを示す」のテストに書き直した

### デモ項目

画面の振る舞いは変わらない。S-02・S-06・S-22 の日時と期間の表示が前と同じであることは、画面の層のシナリオ（`uiTest`）の通過で示した。受入動画は撮らない（計画どおり）。

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | `test` 1,210 件（Bolt 21 の 1,174 件から +36。部品の表の行と規則）、`uiTest` 59 本（変わらず）、`documentationTest` 1 件 |
| 承認ゲートの通過 | 計画の承認 1 回、終了報告（本報告） |
| 人の変更依頼 | 0（本報告の時点） |
| リードタイム | 約 45 分（計画の承認から本報告まで。テストと SonarQube の待ち時間を含む） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `test`・`documentationTest` | 通過（1,210 件・1 件） |
| `uiTest`（axe-core を含む） | 通過（59 本） |
| ModularityTest・ArchUnit | 通過 |
| Spotless | 通過 |
| SonarQube（ローカル） | 品質ゲート PASS。指摘 1 件（`DateTimeDisplayTest` の S3415、引数の順）を直した。走査は 3 回目で通った（1 回目は上の既知の課題のテストの失敗、2 回目は `jacocoTestReport` の失敗で、2 回目の原因は確かめられていない。単独で動かした `jacocoTestReport` と 3 回目の走査は通った） |
| `okf:check` | ERROR 0 |

## 仮説の結論

| # | 仮説 | 結論 |
| :--- | :--- | :--- |
| H1 | `platform :: web` を名前付きインターフェースにすれば、`shared` を変えずに 3 つのモジュールから使える | 成り立った。`allowedDependencies` に足すだけで ModularityTest が通り、`shared` に差分はない |
| H2 | 3 つの期間の形を部品に名前の付いた形として持てば、振る舞いを変えずに集められる | 成り立った。既存のテストの期待の文字列を 1 つも変えずに通った |

## 開発レビュー

プログラマー（テスターの観点を含む）とアーキテクトの 2 つの観点で行った。重大度「高」はなかった。

| # | 指摘 | 重大度 | 対応 |
| :--- | :--- | :--- | :--- |
| P-1 / A-4 | 写しを禁じる規則が `ZoneId.systemDefault()`・`ZoneOffset.of*`・`ZoneId.of` のほかの形を見逃す。Javadoc が期間まで守らせるように読める | 中 | 直した（規則を広げ、Javadoc を「日時の書式とタイムゾーンを守らせる。期間の写しはレビューで見る」に） |
| P-2 | 負の期間の境界が特性テストにない（`connection(-PT1H30M)` は「-30 分」、`hoursAndMinutes(-PT90M)` は「-1 時間 -30 分」） | 中 | いまの値を特性として表に固定した。負を拒む・0 に丸めるは振る舞いの変更なので後に回す（W9 の期間の形の決定とあわせる） |
| P-3 | 表の境界の不足（1 時間以上で秒がある、1 日以上で分が消える、48 時間など） | 低 | 直した |
| P-4 | `KpiObservationViewTest` を消すと、リードタイムと経過時間の排他を単体で確かめられない | 低 | 直した（`from` のテスト 2 本） |
| P-5 / P-6 | 呼び出しが 1 か所だけのアダプタ（`elapsed`、Kpi の `staffDateTime`）と `RoutingCaseViews.duration` は不要 | 低 | 直した（インライン化）。`customerDateTime` はメソッド参照で 4 か所から使うので残した |
| P-7 | `DurationDisplay` の Javadoc の言葉 | 低 | 直した |
| A-1 | ADR-001 のコンプライアンスと食い違う | 中 | 直した（議題 5） |
| A-2 | `quotation` が依存を宣言していない | 中 | 直した（議題 4） |
| A-3 | タイムゾーンが static 定数で、利用者ごとのタイムゾーンを入れるときに呼び出し元すべてが変わる。`QuotationFormConverter` は別名の `CUSTOMER_ZONE` を経由している | 中 | 別名の経由は直した（`DateTimeDisplay.ZONE` を直接使う）。`ZoneId` を受けるオーバーロードは、まだ使う所がないので作らない（利用者ごとのタイムゾーンを入れる Bolt で、画面の層が認証の主体から `ZoneId` を解決して渡す形にする） |
| A-5 | 採番のタイムゾーン（業務番号の年の区切り）と表示のタイムゾーンの区別が書かれていない | 低 | 直した（architecture_backend.md） |
| A-6 | `platform/package-info.java` の Javadoc が古い | 低 | 直した |
| A-7 | Spring Modulith の生成図に、3 つのコンテキストから `platform` への矢印が新しく出る | 低 | 想定どおり（本報告に記録） |

## 判断と学び

### 既知の課題（後の Bolt に持ち込む）

- **DE-03 と DE-16 の listener の競合**: `QuotationPresentedEventHandler`（見積提示済みにする）と `RouteDesignRequestedEventHandler`（経路設計中にする）は、どちらもコミット後に非同期・新しいトランザクションで同じ輸送要求を更新する。提示の直後に詳細経路設計を依頼すると、DE-16 の側が `ConcurrentTransportRequestUpdateException` で失敗し、発行の記録は未完了のまま残る。いまは起動し直したときだけ再配信される（定期の再配信は W10。ADR-014）。SonarQube のコンテナで CPU が混んだ走査の中で、`RouteDesignRequestedRoutingIntegrationTest` が 1 回失敗して分かった。Bolt 12 からある競合で、この Bolt の変更とは関係ない。直し方（競合したときの読み直しと再試行、受理の条件の見直し、定期の再配信の前倒し）を決めるタスクを提案した。US-04 の DE-07 の listener（Bolt 23）も同じ輸送要求を更新するので、Bolt 23 の計画の確認ポイントにする
- W4 の予約・追跡のモジュール（Bolt 23・25）は、`package-info` で最初から `allowedDependencies` に `"shared"`・`"platform :: web"` と上流の `api`・`events` を明示する。荷主の画面（C-10 など）は `DateTimeDisplay.customer`、社内の画面（S-09・S-13・S-24）は `staff` を使い、新しい期間の形は ui_design.md の 3 つの形のどれかに当てはめる（開発レビュー A-3 の影響の欄）

### ふりかえり（KPT）

- Keep
  - 特性テストを先に書き、部品を作ってから置き換えたので、画面の文字列を変えずに集められた
  - 写しを禁じる規則を Red で先に入れ、3 つの写しを検出することを確かめてから置き換えた。規則を広げたあとも、一時的に写しを入れて検出を確かめた
- Problem
  - 計画の確認ポイント 7 の規則を、既存のコード（フォームの変換）を grep せずに書いた。実装で初めて食い違いに気づいた
  - Red のコミットを 2 回 push し、CI の `check` を 2 回赤にした
  - 置き換えで `ArrayList` の import まで消し、1 回コンパイルに失敗した
- Try
  - T-64: ArchUnit の禁止の規則を計画に書くときは、禁じる型・呼び出しをコードで grep し、正当な使い道（入力の解釈など）がないか確かめてから書く（AI、次の Bolt から）
  - T-65: CI を赤にする Red（テストのコンパイルの失敗、規則・アーキテクチャテスト）のコミットは、ローカルにためて Green と同じ push にまとめる（AI、次の Bolt から）

## 次の Bolt

Bolt 23: US-04 AC1 確定・AC2 失効（#10）。W4 の計画のとおり、最初のステップで 2 つの ADR（予約サガと追跡の開始の依存の向き、予約から見積りの確定可否の問い合わせ）を書き、architecture_backend.md の予約サガの図を直す。インサイドアウトで、確定は Red・Green ごと、スキーマはステップごとに止める。上の既知の課題（listener の競合）を確認ポイントにする。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-08 | 初版（ステップ 1〜5 の結果、開発レビュー、品質ゲート、既知の課題、承認の議題 1〜9、Try T-64・T-65） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 22 計画](bolt_22_plan.md)
- [リリース計画](release_plan.md)（W4）
- [ADR-001](../../adr/cargo-tracker/001-modular-monolith.md)
- [バックエンドアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)
- [UI 設計](../../design/cargo-tracker/ui_design.md)
