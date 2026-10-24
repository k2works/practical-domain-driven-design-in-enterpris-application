---
type: Plan
title: "Bolt 37f2 計画 - 受入シナリオの背景の写しの整理（詳細経路設計の依頼の配信まで）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-24T09:08:26Z }
---

# Bolt 37f2 計画 - 受入シナリオの背景の写しの整理（詳細経路設計の依頼の配信まで）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 37f2 回（W6 の 16 本目） |
| 予定 | W6（2026-11-09 の週。前倒しで着手できる）。作業は約 2.0 時間（`check`・`uiTest`・SonarQube・CI の待ちを除く） |
| アプローチ | 技術タスク（振る舞いを変えない。開発戦略 :120）。今の受入シナリオを特性テストとして使う（写しを部品にまとめた前例は Bolt 22） |
| 対象 | 業務ルールの層の 8 つの feature の背景の先頭 10 ステップ（詳細経路設計の依頼の配信まで）を、見積りの受入ステップの 1 つの複合ステップにまとめる。#50 の「受入シナリオの背景の写し（35b:115）」を閉じる |
| GitHub | [#50](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/50)（項目にチェックを入れる） |
| SP | 0 |
| 承認ゲート | 計画と終了報告。新しいファイル（複合ステップのクラス）。構造（業務ルールの層で、別のステップのクラスを入れて呼ぶ形は初めて）。本体のコード・画面・マニュアル・スキーマ・CI は変えない |
| 開始準備で決めたこと | 2026-10-24、human:kakimomokuri: (1) まとめるのは詳細経路設計の依頼の配信までの 10 ステップだけ（経路設計の段は航海の表が feature ごとに違うので背景に残す。画面の層は入れない）、(2) 複合ステップは `quotation.acceptance` に新しいクラスで置き、見積りのステップと共有の時計・配信のステップを入れて呼ぶ |

## Bolt ゴール

8 つの feature が同じに持つ背景の写し（10 ステップ）を 1 つの宣言的な前提にまとめ、振る舞いを変えずに、背景の変更を 1 か所で済むようにする。

### 開始準備で確かめたこと

- **写し**: 先頭の 10 ステップは 8 つの feature で一字一句同じ（時刻も値も同じ）。S1 現在時刻 2026-10-05T01:00Z、S2 輸送条件の入力（`TransportRequestSteps.java:113-124`。JPTYO → NLRTM、期限 2026-11-02T00:00Z、一般貨物の決め打ち）、S3 提出（:144）、S4 03:00Z、S5 審査の確定（`TransportRequestReviewSteps.java:89`。結果を確かめない）、S6 04:00Z、S7 有効期限 2026-10-08T09:00Z の見積りの算出・社内承認・提示（`QuotationSteps.java:144`）、S8 2026-10-06T02:00Z、S9 詳細経路設計へ進む回答（`QuotationResponseSteps.java:78`。結果を確かめない）、S10 配信（`EventDeliverySteps.java:37`）
- **8 つの feature**: `routing/route_candidates.feature`（:10-22）、`routing/golden/route_candidates_golden_v0.feature`（:11-21）、`quotation/approve_quotation_and_route.feature`（:9-28）、`routing/confirm_route.feature`（:11-31）、`routing/evaluate_confirmed_route.feature`（:10-32）、`quotation/route_redesign_required.feature`（:9-31）、`booking/confirm_booking.feature`（:16-38）、`quotation/requote_after_approval.feature`（:10-39）。S10 の後は feature ごとに違う（経路設計の規則・航海・候補の算出・確定など）ので背景に残す
- **範囲の外**: 先頭だけ同じもの（`request_route_design`・`present_quotation`・`expire_and_replace_quotation` は S1〜S6、`review_transport_request`・`resubmit_transport_request` は S1〜S4、`identity/kpi_01_lead_time` は S1〜S5 と配信）は、まとめる 10 ステップと終わりが違うので入れない。画面の層（`features/ui`）は別のグルーで、背景はすでに宣言的な部品
- **Cucumber の組み立て**: cucumber-spring 8.0.3（`build.gradle:30`・`:77-79`）。グルーの設定は 1 つ（`RunCucumberTest.java:22-27`。業務ルールの層の 7 つのパッケージ）で、どのパッケージのステップも Gherkin の文で呼べる。ステップのクラスはコンストラクターで部品を受け取り、状態を持つ（`TransportRequestSteps` の `input`・`lastOutcome` など）。同じシナリオの中では同じインスタンスが注入される（cucumber-spring のシナリオの範囲。`QuotationSteps` は背景の後も `lines`・`expiresAt`・`via` などの状態を持ち越すので、同じインスタンスであることが効く。呼ぶメソッドは public で、`DataTable` を取らない）
- **層の規則**（`AcceptanceStepArchitectureTest.java:79-102`）: `<ctx>.acceptance` は他のコンテキストの `interfaces.api` 以外に依存できない。`shared.acceptance` への依存は許される。10 ステップは見積りのクラスと共有のステップだけで書けるので、`quotation.acceptance` に置けば規則に当たらない
- **前例**: 同じクラスの中の複合ステップは `QuotationSteps.java:139-159`。別のステップのクラスを入れて呼ぶ形は画面の層の `ui/StepwiseTransportRequestUiSteps.java:23-27` だけ
- **読みやすさ**: 見積りの有効期限 2026-10-08T09:00Z は、シナリオの時刻と比べて読む（`confirm_booking.feature:45・67-68・129-132・165-167`、`approve_quotation_and_route.feature:72-74`、`requote_after_approval.feature:43・94`、`route_redesign_required.feature:127`）。最後の時刻 2026-10-06T02:00Z は、Golden の「現在時刻を案件の判定時刻にする」（`RoutingSteps.java:133-137`）と、シナリオの最初の時刻の比べに要る。どちらも複合ステップの文に引数で残す。テスト戦略 :475「背景でデータを準備し、シナリオの中で業務の言葉で表す」、:165-169 の宣言的な 1 行の前提の例と合う
- **確かめ方**: 今の `cucumber.json` は 20 feature、174 シナリオ、2598 ステップ。対象 8 つの feature のシナリオ（例の行を含む）は 81。10 ステップを 1 つにすると、ステップは 81 × 9 = 729 減る見込み（2598 → 1869。背景の要素 170 は変わらない）。依頼の時刻 2026-10-06T02:00Z そのものを確かめるシナリオは 8 つの feature にない（確かめているのは範囲の外の `request_route_design.feature:23` だけ）。確かめは足さない
- **引き継ぎ**: Bolt 37f Try T-196（シナリオアウトラインの変異は `cucumber.json` で判定）は、背景を縮めると行番号がずれるので、feature・シナリオ名・例の値で突き合わせる形に読み替える

## 設計の決定（案）

| 項目 | 決定 | 根拠 |
| :--- | :--- | :--- |
| 複合ステップの文 | 「荷主担当者が {string} に、有効期限 {string} の見積り 1 について詳細経路設計へ進むと回答し、依頼が配信されている」（1 つ目が依頼の時刻、2 つ目が有効期限）。背景では「前提 荷主担当者が "2026-10-06T02:00:00Z" に、有効期限 "2026-10-08T09:00:00Z" の見積り 1 について詳細経路設計へ進むと回答し、依頼が配信されている」と書く | テスト戦略 :165-169・:475、開始準備で確かめたこと（読みやすさ） |
| 中で決める値 | 提出 2026-10-05T01:00Z、審査 03:00Z（根拠「契約条件を確認した」）、提示 04:00Z、輸送条件は `TransportRequestSteps` の決め打ちのまま。引数は依頼の時刻と有効期限。中の値は複合ステップの Javadoc に書く（T-194） | 振る舞いを変えない |
| 置き場 | `quotation/acceptance/RouteDesignRequestedSteps`（新しいファイル）。`TransportRequestSteps`・`TransportRequestReviewSteps`・`QuotationSteps`・`QuotationResponseSteps` と共有の `ClockSteps`・`EventDeliverySteps` をコンストラクターで受け取り、今の 10 ステップのメソッドを同じ順で呼ぶ | 開始準備の決定 (2)、層の規則 |
| 確かめを足すか | 足さない（S5・S9 は今も結果を確かめていない。振る舞いを変えない） | 技術タスク |
| 文書 | test_strategy の置き場とステップ定義の節（:144・:152 の近く）に、複合ステップの規則（同じコンテキストの受入ステップに置き、共有の時計・配信を使ってよい。中で決める値は Javadoc に書き、シナリオと比べる時刻と期限は引数で見せる）を書く。Golden の feature の説明の「（Bolt 37f2 でまとめる）」を直す | 前例を規則にする |

## スコープ

### 入れるもの

- 8 つの feature の背景の先頭 10 ステップをまとめる複合ステップ（新しいクラス）と、8 つの feature の背景の書き換え
- test_strategy の複合ステップの規則、Golden の feature の説明、release_plan（37f2 の行の置き場の言い方と承認ゲート、W6 節の状態、更新履歴）、#50 の項目のチェック

### 入れないもの

- 先頭だけ同じ 6 つの feature（`request_route_design`・`present_quotation`・`expire_and_replace_quotation`・`review_transport_request`・`resubmit_transport_request`・`identity/kpi_01_lead_time`）
- 画面の層（`features/ui`。別のグルーで、背景はすでに宣言的な部品）
- 経路設計の段（規則・航海・候補の算出・確定。航海の表が feature ごとに違う）
- 本体のコード・画面・マニュアル・スキーマ・CI の変更、確かめを足すこと

## ふりかえりと前の Bolt からの引き継ぎ

| 出典 | 内容 | この Bolt での扱い |
| :--- | :--- | :--- |
| Bolt 37f Try T-195 | 境界の代表案件は両側を同じ案件に入れる | 当てはまらない |
| Bolt 37f Try T-196 | シナリオアウトラインの変異は `cucumber.json` で判定する | feature・シナリオ名・例の値で突き合わせる（行番号は背景を縮めるとずれる） |
| Bolt 37e4c Try T-194 | 確かめる値を決める部品と前提のデータを書く | 複合ステップが中で決める値を Javadoc と計画に書いた |
| W5 の Try T-148・T-152 | 写しを数えてから変える | 8 つの feature、81 シナリオ、729 ステップと数えた |
| Bolt 35b の開発レビュー（:86） | 3 つ目の写しが出たら上位のステップにまとめる | この Bolt でまとめる |
| T-174 | レビューで足す変更もテストを先に書く | 開発レビューの直しも、受入シナリオで確かめてから入れる |

## ステップ計画

- [ ] **1. 前の結果を控える**: `RunCucumberTest` を流し、`cucumber.json` から feature ごとのシナリオ名と例の値の組、ステップの数、すべて passed を控える（完了の判定: 控え）
- [ ] **2. 複合ステップ（Red → Green）**: 新しいクラスを空の操作で作り、1 つの feature（`route_candidates.feature`）の背景をまとめて、空の操作で落ちることを確かめてから中身を作る（完了の判定: その feature が緑）
- [ ] **3. 残りの 7 つの feature の背景をまとめる**（完了の判定: `RunCucumberTest` が緑）
- [ ] **4. 前後を比べる**: シナリオの数と名前と例の値の組が前と同じ、すべて passed、ステップは 729 減る（完了の判定: 比べの結果）
- [ ] **5. 品質ゲートと開発レビュー**: 変異（下の表）、`check`・`uiTest`（件数が前後で減らない）、その後に SonarQube、開発レビューとその直し
- [ ] **6. 文書と終了報告**: test_strategy の複合ステップの規則、Golden の feature の説明、release_plan（:771 の「共有のステップ」を「見積りの受入ステップ（`quotation/acceptance/RouteDesignRequestedSteps`）」に直し、承認ゲートに新しいファイル・構造を足す、:703 の「37f2・37f3 は計画前」、更新履歴）。push して CI と MkDocs を確かめる。終了報告の承認の後に #50 の項目にチェックを入れてコメントを残し、開発の索引を完了に直す

### 時間の配分と打ち切り

| # | 目安 | 打ち切り |
| :--- | :--- | :--- |
| 1 | 10 分 | — |
| 2 | 30 分 | 同じシナリオの中でステップのクラスが同じインスタンスで注入されないと分かったら（状態が引き継がれない）、止めて諮る |
| 3 | 25 分 | — |
| 4 | 15 分 | 数が見込みと合わなければ止めて原因を確かめる |
| 5 | 25 分（待ちを除く） | — |
| 6 | 15 分（CI の待ちを除く） | — |

合計 120 分（約 2.0 時間）。

## 確認ポイント（計画の承認の場でまとめて確認する。Try T-6）

| # | 確認すること | 場所 | 推奨 |
| :--- | :--- | :--- | :--- |
| 1 | **範囲**（開始準備の決定 (1)） | ステップ 2・3 | 8 つの feature の先頭 10 ステップだけ。経路設計の段、先頭だけ同じ feature、画面の層は入れない |
| 2 | **置き場（新しいファイル・構造。開始準備の決定 (2)）** | ステップ 2 | `quotation/acceptance/RouteDesignRequestedSteps`。別のステップのクラスを入れて呼ぶ形を業務ルールの層で初めて使う（画面の層の前例 `StepwiseTransportRequestUiSteps`）。test_strategy に規則を書く |
| 3 | **ステップの文と引数** | ステップ 2 | 依頼の時刻と見積りの有効期限を引数で見せる。提出・審査・提示の時刻と輸送条件は中で決め、Javadoc に書く |
| 4 | **確かめを足さない** | ステップ 2 | 審査と回答の結果は今も確かめていないので足さない（振る舞いを変えない） |
| 5 | **完了の数え方** | ステップ 4 | シナリオの数・名前・例の値は同じ、すべて passed、ステップは 729 減る。`uiTest` の件数は変わらない |
| 6 | **変異** | ステップ 5 | 下の 4 つ。複合ステップが本当に効いていることを確かめる。スクリプトは gradlew.bat を絶対パスで呼び、結果を消してから流す（T-164・T-178）。V1・V2・V4 は落ちたシナリオの件数で、V3 は `cucumber.json` の feature・シナリオ名・例の値で判定する（T-187・T-196 の読み替え） |

### 変異と落ちる見込みのテスト

| # | 変異 | 落ちる見込み |
| :--- | :--- | :--- |
| V1 | 複合ステップの中の配信を外す | 8 つの feature の 81 シナリオすべて（経路設計の案件ができない） |
| V2 | 審査を外す | 81 シナリオすべて（見積りを算出できず、背景のうちに落ちる） |
| V3 | 有効期限の引数を無視して 2026-10-09T09:00:00Z にする | 有効期限の後を確かめるシナリオ（`approve_quotation_and_route.feature:73-74`、`confirm_booking.feature:67-68`、`requote_after_approval.feature:42`） |
| V4 | 依頼の時刻の引数を無視して有効期限と同じ時刻（2026-10-08T09:00:00Z）にする | 81 シナリオすべて（回答が失効で拒まれ、経路設計の案件ができない）。提示の時刻にずらす形は、依頼の時刻を確かめるシナリオがないので落ちない（開始準備で確かめたこと） |

## 完了条件

- [ ] 上のステップ 1〜6 の完了の判定を満たす
- [ ] 8 つの feature の背景が複合ステップを使い、シナリオの数・名前・例の値が前と同じで、すべて passed
- [ ] `check`・`uiTest` が緑（件数が減らない）、SonarQube が PASS、CI が緑
- [ ] 終了報告が承認される

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-24 | 整合性検証の指摘を反映した（承認待ち）。V4 を落ちる形に替え、V3 の定数と落ちる行、スコープの節、release_plan と索引の直し、数の見込み（2598 → 1869）を足した | anthropic/claude-opus-5-5 |
| 2026-10-24 | 初版作成（承認待ち）。開始準備で、範囲を詳細経路設計の依頼の配信までの 10 ステップに、置き場を見積りの受入ステップの新しいクラスにすると決めた（human:kakimomokuri） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [リリース計画](release_plan.md)（W6 節の 37f2 の行）、[Bolt 37f 計画](bolt_37f_plan.md)・[終了報告](bolt_37f_report.md)
- [Bolt 35b 終了報告](bolt_35b_report.md)（背景の写しの出どころ）
- [テスト戦略](../../design/cargo-tracker/test_strategy.md)（置き場とステップ定義）
