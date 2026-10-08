---
type: Plan
title: "Bolt 22 計画 - 日時表示・期間表示を platform の Web の部品に集める（#41）"
description: "22 回目の Bolt の計画。見積り・経路設計・アクセス監査の 3 つのモジュールに写されている日時表示と期間表示を、振る舞いを変えずに platform の Web の部品 1 か所に集める技術タスク（SP 0）を、ステップ 1〜5 で定義する。"
tags: [development,bolt-plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-08T05:44:03Z }
---

# Bolt 22 計画 - 日時表示・期間表示を platform の Web の部品に集める（#41）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 22 回 |
| 予定 | W4（2026-10-26 の週。前倒しで 2026-10-08 から）、2〜2.5 時間 |
| 対象 | `platform`（新しい Web の部品）と、`quotation`・`routing`・`identity` の `interfaces.web` |
| GitHub | [#41 [技術] 日時表示・期間表示を platform の Web の部品に集める](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/41)（SP 0） |
| 承認ゲート | 計画の承認（確認ポイント 1〜8）、終了報告。W4 の計画どおり `/goal` で止めずに進め、止まらなかったゲートごとに根拠を書いて終了報告の承認の議題に置く（T-36） |
| アプローチ | 技術タスク（開発戦略の「Bolt ごとのアプローチの決め方」）。テストの入口は検証の手段に置く。いまの 3 つの写しの振る舞いを、部品の表のテストに写して固定し（特性テスト）、部品を作り、写しを置き換える。振る舞いは変えない（リファクタリング） |
| 範囲の決定 | W4 の計画（2026-10-08 に human:kakimomokuri が承認）。Bolt 21 終了報告の議題 5、開発レビューの D-84 |
| 前の Bolt | [Bolt 21 終了報告](bolt_21_report.md) |

## Bolt ゴール

社内・荷主の画面の日時表示（利用者のタイムゾーンを主にし、社内の画面では UTC を併記する形）と期間表示が `platform` の Web の部品 1 か所にあり、見積り・経路設計・アクセス監査の画面はその部品を使う。画面に出る文字列は 1 文字も変わらない。利用者ごとのタイムゾーンを入れるときに直す場所が 1 つになる。

### 確かめたい仮説

| # | 仮説 | この Bolt で分かること |
| :--- | :--- | :--- |
| H1 | 表示の部品を `platform` の名前付きインターフェース（`platform :: web`）として公開すれば、共有カーネル（`shared`）を表示の関心で変えずに、3 つのモジュールから使える | ModularityTest（AT-03）と ArchUnit が、`allowedDependencies` に `platform :: web` を足すだけで通ること。`shared` に差分がないこと |
| H2 | 3 つの写しの違い（期間の形が 3 通り）は、部品に名前の付いた 3 つの形として持てば、振る舞いを変えずに 1 か所に集められる | 特性テストと、既存の単体・受入・画面の層のテストがすべて変更なしで通ること |

## ふりかえりと前の Bolt からの引き継ぎ

| 入力 | 内容 | この Bolt での扱い |
| :--- | :--- | :--- |
| Bolt 21 終了報告 議題 5、レビュー D-84 | 日時表示・期間表示の写しが 3 つになった。次の Bolt の最初のリファクタリングで `platform` の Web の部品に集める（共有カーネルを表示の関心で変えない） | スコープ |
| W4 の計画（Bolt 22 の確認ポイント） | 「コンテキストのコードから `platform` を参照しない」の規則（architecture_backend.md）と `allowedDependencies` の改訂、または設定経由（Thymeleaf の式オブジェクトなど）に限る案との比較 | 確認ポイント 1・2 |
| Try T-63（Bolt 21） | 「N 個目で集める」の規則は N 個目の計画に入れる | この Bolt がその「集める」Bolt。以後、新しい画面は部品を使う（確認ポイント 7） |
| Try T-55（Bolt 19） | 置換は `spotlessApply` の後のファイルに当てる | ステップ 3 |
| Try T-61（Bolt 21） | メモリのリポジトリは写しを返す | この Bolt はリポジトリを触らない |
| Try T-54・T-57・T-58・T-62 | イベント・状態・listener | この Bolt はイベント・状態・listener を足さない |
| Try T-36 ほか、これまでの Try | これまでのとおり | 全ステップ |

## スコープ

### いまの 3 つの写し

| モジュール | 場所 | 日時 | 期間の形 |
| :--- | :--- | :--- | :--- |
| `quotation` | `interfaces/web/TransportRequestLabels` | `customerDateTime`（荷主: 「2026-10-05 10:00 Asia/Tokyo（UTC+09:00）」）、`staffDateTime`（社内: 荷主の形 +「（UTC 2026-10-05 01:00）」） | `elapsed`: 受付一覧の待ち時間。「N 日 H 時間」、1 日未満は「H 時間 M 分」、1 時間未満は「M 分」。負は 0 に丸める |
| `routing` | `interfaces/web/RoutingCaseViews` | `dateTime`（社内の形） | `duration`: 接続時間。日・時間・分のうち 0 でない部分を並べる |
| `identity` | `interfaces/web/KpiObservationView` | `staffDateTime`（社内の形） | `formatDuration`: KPI-01 リードタイム。「H 時間 M 分」。日に繰り上げない、分未満は切り捨て |

表示のタイムゾーンは 3 つとも `Asia/Tokyo` に固定している。

### 入れるもの・入れないもの

| 入れる | 入れない |
| :--- | :--- |
| `platform` の Web の部品（日時の 2 つの形、期間の 3 つの形）と、表のテスト | 期間の形を 1 つにそろえること（画面の文字列が変わる。日時の短い形とあわせて US-21 の残り（W9）で決める。D-84） |
| `platform :: web` の名前付きインターフェースと、3 つのモジュールの `allowedDependencies` | 利用者ごとのタイムゾーン（直す場所を 1 つにするところまで） |
| 3 つの写しの置き換えと、重複した単体テストの整理 | テンプレートの変更（文字列は Java の表示の値で作っており、テンプレートは変えない） |
| architecture_backend.md の規則の改訂、ui_design.md の共通部品「日時表示」への期間表示の形と画面の対応 | 新しい画面・受入条件 |

## 設計（この Bolt の範囲）

### モジュールの依存

```plantuml
@startuml
package "platform" {
  package "web（@NamedInterface）" as pw {
    class DateTimeDisplay <<表示の部品>> {
      + {static} customer(UtcInstant): String
      + {static} staff(UtcInstant): String
    }
    class DurationDisplay <<表示の部品>> {
      + {static} waiting(Duration): String
      + {static} connection(Duration): String
      + {static} hoursAndMinutes(Duration): String
    }
  }
  package "mybatis" {
  }
}
package "shared" {
  class UtcInstant
}
package "quotation" as q
package "routing" as r
package "identity" as i

pw ..> UtcInstant
q ..> pw : interfaces.web
r ..> pw : interfaces.web
i ..> pw : interfaces.web
@enduml
```

部品の名前は案（確認ポイント 3）。`platform` は `shared` だけに依存し、業務の型（見積り・経路など）を参照しない。

### 状態遷移・データモデル・画面遷移

この Bolt は状態・表・画面を変えないため、状態遷移図・ER 図・画面遷移図は載せない。

## ステップ計画

| # | ステップ | 内容 | 承認ゲート |
| :--- | :--- | :--- | :--- |
| 1 | 特性テスト（Red） | `platform` に部品の表のテストを書く。3 つの写しの既存の単体テストと画面の層のシナリオから、日時（荷主・社内）と期間（3 つの形）の入力と期待の文字列を写す。境界（0 分、59 分、1 時間ちょうど、23 時間 59 分、1 日ちょうど、負の期間、日付をまたぐ UTC）を足す。期待の値は、いまの写しの実装を実行して得る値に合わせる（振る舞いを変えない）。部品がないのでコンパイルで失敗する | `/goal`（議題に置く） |
| 2 | 部品と依存の宣言（Green） | `platform.web` に部品を作り、`@NamedInterface("web")` で公開する。`quotation`・`routing`・`identity` の `allowedDependencies` に `platform :: web` を足す。ModularityTest・ArchUnit（AT-01〜06）を通す | `/goal`（議題に置く。構造の変更） |
| 3 | 写しの置き換え（Refactor） | 3 つの写しを部品の呼び出しに置き換え、写しの定数（`DISPLAY_ZONE`・`DATE_TIME`・`UTC_TIME`）とメソッドを消す。写しの単体テストのうち部品の表に移った行は消し、表示の値を組み立てる側のテスト（行・詳細）は残す。`spotlessApply` の後に置換する（T-55）。全テスト（単体・統合・受入・`uiTest`）を通す | `/goal` |
| 4 | 設計文書 | architecture_backend.md の「コンテキストのコードから `platform` を参照しない」を、確認ポイント 1 の決定に合わせて改訂する。ui_design.md の共通部品「日時表示」に、期間表示の 3 つの形と、どの画面がどの形を使うか（S-02 の待ち時間、S-06 の接続時間、S-22 のリードタイム・経過時間）を書く。用語集の整合テストと `documentationTest` を通す | `/goal` |
| 5 | 開発レビューと終了報告 | `developing-review`（プログラマー・アーキテクト）、SonarQube、終了報告、#41 のクローズ | 終了報告の承認 |

### 時間の配分と打ち切り

| # | 目安 | 打ち切り |
| :--- | :--- | :--- |
| 1 | 30 分 | 境界の行が 30 行を超えたら、写しの既存のテストにある行と境界だけにする |
| 2 | 30 分 | Modulith の名前付きインターフェースが `platform` で使えない場合は、確認ポイント 1 の次の案に切り替え、計画の変更として議題に置く |
| 3 | 40 分 | 画面の層のテストが文字列の差で落ちたら、置き換えを戻して原因の写しを特性テストに足す（振る舞いを変えない） |
| 4 | 20 分 | — |
| 5 | 30 分 | — |

## 確認ポイント（計画の承認の場でまとめて確認する。Try T-6）

| # | 確認すること | 推奨 |
| :--- | :--- | :--- |
| 1 | **部品の置き場所と、`platform` を参照しない規則の扱い** | 推奨は `platform.web` を名前付きインターフェース（`platform :: web`）で公開し、architecture_backend.md の規則を「コンテキストのコードは `platform` のうち `web` の表示の部品だけを参照できる。業務の型と規則は置かない」に改訂する。ほかの案: (b) Thymeleaf の式オブジェクトにして設定経由に限る。経路設計の除外の理由の文（接続時間を文の中に入れる）は Java で作るので、Java の写しが残り、集めきれない。(c) `shared` に置く。D-84 で退けた（共有カーネルを表示の関心で変えない） |
| 2 | **ADR を書くか** | 推奨は書かない。モジュールの依存の規則の小さな改訂で、決定と理由は architecture_backend.md の改訂と本計画・終了報告に残す。H1 が外れて別の案に切り替えるなら ADR にする |
| 3 | 部品の名前 | 推奨は `DateTimeDisplay`（`customer`・`staff`）と `DurationDisplay`（`waiting`・`connection`・`hoursAndMinutes`）。UI 設計の共通部品「日時表示」と、各形を使う画面の言葉に合わせる |
| 4 | 期間の 3 つの形をそろえるか | 推奨はそろえない（画面の文字列を変えない）。そろえるかは日時の短い形とあわせて US-21 の残り（W9）で決める（D-84） |
| 5 | 表示のタイムゾーン | 推奨は部品の中の `Asia/Tokyo` 1 か所に固定したまま。利用者ごとのタイムゾーンは入れない |
| 6 | 写しの単体テストの扱い | 推奨は、部品の表に移った行は消し、表示の値を組み立てる側のテストは残す（同じ規則を 2 か所でテストしない） |
| 7 | 以後の画面 | 推奨は、W4 の予約・追跡の画面（S-09・S-24・S-13・C-10）から部品を使い、写しを作らない。ArchUnit で `java.time.format.DateTimeFormatter` をコンテキストの `interfaces.web` から使えなくする規則を足すかは、この Bolt で足す（写しの再発を止める）を推奨 |
| 8 | 承認ゲート | W4 の計画どおり `/goal`。業務のルール・スキーマ・セキュリティに関わらない。構造の変更（`allowedDependencies`）は終了報告の議題に置く |

## 完了条件

- [ ] 部品の表のテスト（特性テスト）が通り、境界の行がある
- [ ] 3 つのモジュールに、日時表示・期間表示の写し（`DateTimeFormatter`・`ZoneId` の定数と書式のメソッド）が残っていない
- [ ] 既存の単体・統合・受入・`uiTest`（axe-core を含む）が、期待の文字列を変えずに通る
- [ ] ModularityTest・ArchUnit（AT-01〜06、確認ポイント 7 で足す規則を含む）が通り、`shared` に差分がない
- [ ] architecture_backend.md と ui_design.md を更新し、`documentationTest` と `okf:check` が通る
- [ ] SonarQube の品質ゲートが PASS
- [ ] 開発レビューの対応と終了報告。#41 をクローズする

### デモ項目

画面の振る舞いは変わらない。デモは、S-02・S-06・S-22 の日時と期間の表示が前と同じであること（画面の層のシナリオの通過）と、部品のテストの表で示す。受入動画は撮らない。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-08 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [リリース計画](release_plan.md)（W4）
- [開発戦略](development_strategy.md)
- [Bolt 21 終了報告](bolt_21_report.md)
- [バックエンドアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)
- [UI 設計](../../design/cargo-tracker/ui_design.md)
