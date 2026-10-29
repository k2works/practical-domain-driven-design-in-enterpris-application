---
type: Plan
title: "Bolt 38f2b 計画 - 担当範囲の動画と #14 への添付"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-29T00:04:44Z }
---

# Bolt 38f2b 計画 - 担当範囲の動画と #14 への添付

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38f2b 回（W6。38f2 から動画と添付を分けた Bolt） |
| 予定 | 約 2 時間（`demoVideo` と CI の待ちを除く）。コードは変えない |
| アプローチ | アウトサイドイン（既存の受入シナリオにデモのタグを足す。release_plan の 38f2b の行） |
| 対象 | (1) 既存の 3 つの受入シナリオに、受入条件のタグと同じ行で `@demo @demo-bolt-38f2b/…` を足し、動画を 3 本撮る、(2) `ops/scripts/issue_demo.js` の `BOLT_ISSUES` に bolt-38f2b → #14 を足し、コメントを統一の文に直す、(3) 運用の索引（`docs/operation/cargo-tracker/index.md`）の添付先の表の #14 の行に bolt-38f2b を足し、:55 の文を同じ統一の文に直す、(4) 終了報告の承認の後に #14 へ添付する |
| トレース | US-16 AC1（担当企業の割り当て）・AC4（担当範囲の外の拒否。見積りの側のシナリオの AC4 のタグは 38f2b が受け持つ）、#14 |
| GitHub | [#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14)（動画の添付先。クローズは 38g） |
| SP | 0 |
| 承認ゲート | 計画と終了報告。GitHub（#14 への動画の添付。終了報告の承認の後） |
| ブランチ | develop |
| 開始準備で決めたこと | 2026-10-29、human:kakimomokuri: (1) 38e・38f・38f2 の 3 場面を、既存のシナリオにタグを足して 3 本で撮る（`assign`・`quotation-scope`・`booking-scope`）、(2) 範囲の外の予約は今の準備 `booked(CompanyId)`（予約と予約サガを直接保存）を使う、(3) 添付先は #14。運用の索引の #14 の行に bolt-38f2b を足す、(4) `BOLT_ISSUES` は Bolt の中で足す（38c の前例）。`issue_demo.js` のコメントと運用の索引の「終了報告の承認の後に足す」の文を、前例に合わせて直す、(5) 約 2 時間で、コードは変えない |

新しいファイル: なし（動画 3 本は資産。`docs/assets/demo/bolt-38f2b/assign.webm`・`quotation-scope.webm`・`booking-scope.webm`）。

## Bolt ゴール

担当範囲の割り当て（38e）と、見積りと予約の側の担当範囲の絞り込み（38f・38f2）を、動画で見せて #14 に添付する。38e から 2 回送ってきた動画を、ここでまとめて撮る。

### 開始準備で確かめたこと

- **release_plan の行**: 38f2b は「荷主 A だけを担当する営業担当者で、受付一覧と予約の一覧に荷主 A だけが出て、範囲の外の URL は共通の 404 になる場面（範囲の外の予約の準備を含む）。#14 への添付、`BOLT_ISSUES` と運用の索引の添付先の表の行」。承認ゲートは GitHub（`release_plan.md:788`）
- **動画の仕組み**: `demoVideo` は `@demo` のタグのシナリオを録画し、`docs/assets/demo` に置く。流す前にディレクトリを丸ごと消すので（`doFirst` の `deleteDir`）、計画の外の追跡済みの動画もすべて撮り直される。前例では `git checkout` で戻した（`apps/cargo-tracker/build.gradle:154-176`、Bolt 38b4 の報告で 65 本を戻した）
- **動画の名前**: タグ `@demo-bolt-<Bolt>/<名前>` から付く（例: `@demo-bolt-38c/unlock` → `docs/assets/demo/bolt-38c/unlock.webm`）。前例のタグは受入条件のタグと同じ行に書く（`manage_user_login_credentials_ui.feature:15` の `@US-18-AC3 @demo @demo-bolt-38c/unlock`、`register_user_ui.feature:20`）
- **`BOLT_ISSUES`**: bolt-38c → #47 までで、bolt-38f2b の行はない。コメントは「新しい Bolt は終了報告の承認の後に足す」だが、前例の 38c は Bolt の中で足した（`72c779bcc`。`ops/scripts/issue_demo.js:24-72`）
- **運用の索引**: 添付先の表の #14 の行は `bolt-34・bolt-34d・bolt-38b4`（`docs/operation/cargo-tracker/index.md:67`）。:55 の文も「新しい Bolt は終了報告の承認の後に両方に足す」。手順は `DEMO_BOLT=… DEMO_ISSUE=… npx gulp issue:attach-demo` で、先に `DEMO_DRY_RUN=1` で確かめる（:37・:49）。表のとおりにまとめて確かめる `DEMO_DRY_RUN=1 npx gulp issue:attach-demo:all` もある（:38・:51。`issue_demo.js:261-270`）
- **動画にするシナリオ**（どれも `@demo` はまだない。パスは `apps/cargo-tracker/src/test/resources/features/ui/`）:
  - `manage_user_assignments_ui.feature:14-15`「営業担当者に担当企業を割り当てると確認の領域を経て詳細に知らせと印が出る」（:14 に `@US-16-AC1`）
  - `restrict_staff_by_assigned_companies_ui.feature:14`「担当企業の外の見積依頼は受付一覧に出ず URL を開いても画面が見つからない」（シナリオにも feature にも AC のタグがない。feature は `@ui @US-16 @must`）
  - `restrict_bookings_by_assigned_companies_ui.feature:13`「担当企業の外の予約は予約一覧に出ず URL を開いても画面が見つからない」（feature の :2 に `@US-16-AC4`。シナリオの行にはタグがない）
- **範囲の外の予約の準備**: `StaffAssignedScopeUiSteps.booked(CompanyId)` が予約と予約サガを直接保存する。範囲の外の荷主は `UiUsers.outOfScopeShipper()`（「荷主 範囲外」）。新しい準備は要らない
- **利用者**: 動画は UI テストの利用者で撮る（38f2 の決定 (5)）。dev の 0306（荷主 A だけの営業担当者）とは別の利用者なので、動画に出る荷主は UI テストの名前になる
- **前例**: 38c は計画の中で動画 2 本（2 つのタグ）を撮り、終了報告の承認の後に #47 へ添付した。dry run → 添付 → 報告の更新履歴に記録の順

## 設計の決定

| 項目 | 決定 | 根拠 |
| :--- | :--- | :--- |
| 場面とタグ | 既存の 3 つのシナリオの直前の行に、受入条件のタグと同じ行で書く（前例 38c の形）。`manage_user_assignments_ui.feature:14` を `@US-16-AC1 @demo @demo-bolt-38f2b/assign`、`restrict_staff_by_assigned_companies_ui.feature` のシナリオの直前に `@US-16-AC4 @demo @demo-bolt-38f2b/quotation-scope`、`restrict_bookings_by_assigned_companies_ui.feature` のシナリオの直前に `@US-16-AC4 @demo @demo-bolt-38f2b/booking-scope`。名前は `assign`（38e の割り当て）・`quotation-scope`（38f の受付一覧）・`booking-scope`（38f2 の予約一覧）。シナリオの手順は変えない | 開始準備の決定 (1)、前例 38c |
| 見積りの側の AC4 | 見積りの側のシナリオの `@US-16-AC4` は 38f2b が受け持つ（上の行のとおり、デモのタグと同じ行に足す）。予約の側は feature に付いているが、3 本とも同じ形にそろえるため、シナリオの行にも書く（重なっても照合は変わらない）。38g の整合性検証 L6 で、見積りの側にタグがないと照合の表が実態と合わないと指摘された。38g はこのタグがある前提で照合の表を書く | #14 の完了の定義（AC ごとのタグ付きシナリオ） |
| 範囲の外の準備 | 今の `booked(CompanyId)` と、範囲の外の見積依頼の提出の準備を使う。画面で荷主 B の承認と確定を進めない | 開始準備の決定 (2) |
| 動画の限界 | 受け入れる。(a) 範囲の外の見積依頼と予約は背景の準備（直接の保存・提出）で作るので、動画には映らない、(b) 録画にはアドレスバーがないので、範囲の外の URL を開いたことは画面の遷移と 404 の画面でしか見えない、(c) 利用者と荷主の名前は UI テストのもの（dev の 0306 ではない）。この 3 点を終了報告のデモ項目に書く | 開始準備の決定 (1)・(2)、38f2 の決定 (5) |
| 撮り直しの戻し | `demoVideo` は `docs/assets/demo` を丸ごと消して撮り直すので、流した後は追跡済みの動画（ほかの Bolt の全部）が変更として出るのが期待どおり。`git checkout -- docs/assets/demo` で戻す。`bolt-38f2b` は未追跡なので戻しの対象にならず残る。戻した後の `git status` で、`docs/assets/demo` の差分が未追跡の `bolt-38f2b/` の 3 本だけであることを確かめる。コミットの後に撮り直すとき（終了報告の後の直しなど）は、`bolt-38f2b` も追跡済みになるので、`git restore -- docs/assets/demo ':!docs/assets/demo/bolt-38f2b'` で bolt-38f2b を除いて戻す | 前例 38b4・38c |
| 添付先と時期 | `BOLT_ISSUES` に `'bolt-38f2b': 14` を足し、運用の索引の #14 の行を `bolt-34・bolt-34d・bolt-38b4・bolt-38f2b` にする（Bolt の中で足す）。`issue_demo.js:25` のコメントと索引 :55 の文を、統一の文「新しい Bolt は Bolt の中で足し、動画の添付は終了報告の承認の後に行う」にそろえる（索引は「両方に足し」）。dry run は終了報告の前に、`DEMO_BOLT=bolt-38f2b DEMO_ISSUE=14 DEMO_DRY_RUN=1 npx gulp issue:attach-demo` と `DEMO_DRY_RUN=1 npx gulp issue:attach-demo:all`（表から bolt-38f2b → #14 が引け、添付済みの Bolt は飛ばされる）の 2 つで確かめる。添付は終了報告の承認の後 | 開始準備の決定 (3)・(4)、前例 38c |
| マニュアル | 変わらない。画面と振る舞いを変えないため（キャプチャは 38e・38f・38f2 で撮り済み） | コードを変えない |
| 変異 | 対象がない。本体のコードを変えず、足すのはデモのタグとスクリプトの表の行だけのため。タグを付けたシナリオは既存のもので、38e・38f・38f2 の変異で守られている | 開始準備の決定 (5) |

## 状態の組み合わせ（動画ごとに 1 本。T-182）

| 行 | 動画 | 場面 | 期待 | 出口 | 確かめ方 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `assign` | システム管理者が営業担当者に担当企業を割り当てる | 確認の領域を経て、詳細に知らせと担当企業の印が出る | S-20 の詳細 | `manage_user_assignments_ui.feature`（`@demo-bolt-38f2b/assign`）、動画を見て確かめる |
| 2 | `quotation-scope` | 担当企業の外の見積依頼 | 受付一覧に出ず、その URL を開くと共通の 404（「画面が見つかりません」） | 共通の 404 | `restrict_staff_by_assigned_companies_ui.feature`（`@demo-bolt-38f2b/quotation-scope`） |
| 3 | `booking-scope` | 担当企業の外の予約 | 予約一覧に出ず、その URL を開くと共通の 404 | 共通の 404 | `restrict_bookings_by_assigned_companies_ui.feature`（`@demo-bolt-38f2b/booking-scope`） |
| 4 | — | 計画の外の動画 | `demoVideo` の直後はほかの Bolt の追跡済みの動画が変更として出る。`git checkout -- docs/assets/demo` で戻した後、残る差分は未追跡の `bolt-38f2b/` の 3 本だけ | — | `git -c core.quotepath=false status --porcelain docs/assets/demo` |
| 5 | — | 添付の dry run | `DEMO_BOLT=bolt-38f2b DEMO_ISSUE=14 DEMO_DRY_RUN=1 npx gulp issue:attach-demo` が 3 本を #14 に添付する形を示す。`DEMO_DRY_RUN=1 npx gulp issue:attach-demo:all` の出力に `bolt-38f2b → #14` があり、添付済みの Bolt は飛ばされる | — | 2 つの dry run の出力 |

## 受入条件

| # | 条件 | 行 |
| :--- | :--- | :--- |
| AC-1 | 3 本の動画が撮れ、各場面の期待（割り当て・一覧の絞り込みと 404）が映っている。動画の限界（準備が映らない・アドレスバーがない・UI テストの名前）を終了報告に書く | 1〜3 |
| AC-2 | タグを足した 3 つのシナリオが、`uiTest` で今のまま緑（axe を含む）。`check` の照合のテスト（`AcceptanceCriteriaCoverageTest` など）も緑 | 1〜3 |
| AC-3 | 計画の外の動画の差分が残らない | 4 |
| AC-4 | `BOLT_ISSUES` と運用の索引に bolt-38f2b → #14 があり、コメントと索引の文が統一の文になり、2 つの dry run が通る | 5 |
| AC-5 | 終了報告の承認の後に #14 へ添付し、報告の更新履歴に記録する | — |

## スコープ

### 入れるもの

- 3 つの feature のタグ（受入条件のタグと同じ行の `@US-16-AC1`・`@US-16-AC4` と `@demo`・`@demo-bolt-38f2b/…`）
- `ops/scripts/issue_demo.js` の `BOLT_ISSUES` の行とコメント
- `docs/operation/cargo-tracker/index.md` の添付先の表の #14 の行と :55 の文
- 動画 3 本
- release_plan の 38f2b の行と更新履歴、開発の索引（`docs/development/cargo-tracker/index.md`）の行とデモの一覧、`mkdocs.yml` の nav、OKF の log（親が同期する）

### 入れないもの

- 本体のコード、マニュアル、キャプチャ（変わらない）
- 経路設計と追跡の担当範囲（38g）

## ステップ計画

- [ ] **1. タグを足す**: 3 つのシナリオの直前の行に、受入条件のタグと同じ行で `@US-16-AC1 @demo @demo-bolt-38f2b/assign`・`@US-16-AC4 @demo @demo-bolt-38f2b/quotation-scope`・`@US-16-AC4 @demo @demo-bolt-38f2b/booking-scope` を書く。`uiTest` の対象の 3 つの feature が緑のままであることを確かめる
- [ ] **2. 動画を撮る**: `demoVideo` を流す（利用者が IDE から uiTest を流していないことを確かめてから。T-215）。3 本を目で見て、行 1〜3 の期待が映っていることと、動画の限界の 3 点を確かめる。ほかの Bolt の動画が変更として出るのは期待どおりで、`git checkout -- docs/assets/demo` で戻す（未追跡の bolt-38f2b は残る）。行 4 の `git status` で確かめる
- [ ] **3. 添付先の表**: `BOLT_ISSUES` と運用の索引の #14 の行に bolt-38f2b を足し、コメントと :55 の文を統一の文にそろえる。行 5 の 2 つの dry run を流す
- [ ] **4. ゲートと報告**: `check`（タグの変更で `documentationTest` などに影響がないか）、`okf:check`、終了報告、push、CI と配備の確かめ
- [ ] **5. 添付**: 終了報告の承認の後に `DEMO_BOLT=bolt-38f2b DEMO_ISSUE=14 npx gulp issue:attach-demo` で #14 に添付し、報告の更新履歴に記録する

### 時間

| # | 目安 |
| :--- | :--- |
| 1 | 15 分 |
| 2 | 40 分（`demoVideo` の待ちを除く。戻しを含む） |
| 3 | 25 分（dry run を含む） |
| 4 | 30 分（CI の待ちを除く） |
| 5 | 10 分 |

合計 120 分（約 2 時間）。打ち切りは置かない（コードを変えない小さな Bolt のため）。

## 確認ポイント（計画の承認の場でまとめて確認する）

| # | 確認すること | 推奨 |
| :--- | :--- | :--- |
| 1 | **場面と本数** | 38e・38f・38f2 の 3 場面を 3 本で撮る（開始準備の決定 (1)） |
| 2 | **見積りの側の `@US-16-AC4`** | 38f2b が受け持ち、デモのタグと同じ行に足す（38g の照合の表を実態に合わせる） |
| 3 | **`BOLT_ISSUES` に足す時期** | Bolt の中で足し、コメントと索引の文を統一の文「新しい Bolt は Bolt の中で足し、動画の添付は終了報告の承認の後に行う」にそろえる（前例 38c） |
| 3b | **動画の限界** | 受け入れて終了報告に書く（準備が映らない・アドレスバーがない・UI テストの名前） |
| 4 | **GitHub（#14 への添付）** | 終了報告の承認の後に添付する |

## 完了条件

- [ ] ステップ 1〜4 を満たす
- [ ] 行 1〜5 と AC-1〜AC-4 を満たす
- [ ] `check`・`uiTest` が緑、`okf:check` が ERROR 0、CI が緑、デモ環境が新しい版で動く
- [ ] 終了報告が承認され、#14 に添付する（AC-5）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-29 | 2 つの整合性検証（xp-tester・xp-architect）の指摘を反映した（承認待ち）。タグを受入条件のタグと同じ行に書く、見積りの側の AC4 を 38f2b が受け持つ、動画の限界を受け入れて報告に書く、`issue:attach-demo:all` の dry run を足す、撮り直しの戻しの期待と除外の pathspec を書く、コメントと索引の文を統一の文にそろえる、出典の行とパスを直す、スコープに開発の索引・mkdocs・log を足す | anthropic/claude-opus-5-5 |
| 2026-10-29 | 初版作成（承認待ち）。開始準備で、3 場面を既存のシナリオに 3 本で撮る、範囲の外の予約は今の準備を使う、添付先は #14、`BOLT_ISSUES` は Bolt の中で足す、コードは変えないと決めた（human:kakimomokuri） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [リリース計画](release_plan.md)（W6 節の 38f2b の行）、[Bolt 38f2 計画](bolt_38f2_plan.md)・[終了報告](bolt_38f2_report.md)、[Bolt 38f 終了報告](bolt_38f_report.md)、[Bolt 38e 終了報告](bolt_38e_report.md)
- [Bolt 38c 計画](bolt_38c_plan.md)（動画と添付の前例）、[運用の索引](../../operation/cargo-tracker/index.md)（動画の添付の手順と添付先の表）
