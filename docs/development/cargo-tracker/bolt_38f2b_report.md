---
type: Plan
title: "Bolt 38f2b 終了報告 - 担当範囲の動画と #14 への添付"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-29T01:17:32Z }
---

# Bolt 38f2b 終了報告 - 担当範囲の動画と #14 への添付

## 承認の議題（T-36。先に確かめてほしいこと）

担当範囲の割り当て（38e）と、見積り・予約の側の担当範囲の絞り込み（38f・38f2）を 3 本の動画に撮りました。コードは変えていません。#14 への添付は、この報告の承認の後に行います。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **動画の中身**: 3 本の動画に各場面の期待が映っている。割り当ての動画は確認の領域と「担当範囲を変更しました。」、見積りの側は受付一覧に範囲の中の 1 件だけが出て範囲の外の URL が「画面が見つかりません」、予約の側は予約一覧に 1 件だけが出て同じ 404 | `docs/assets/demo/bolt-38f2b/` の 3 本 |
| 2 | **動画の限界（計画の 3b に 2 点を足した）**: 次の 5 点を受け入れる。根拠は `uiTest` と統合テストに置く | 下の「動画の限界」 |
| 3 | **dry run で添付済みを飛ばすことは確かめられない**: `issue:attach-demo:all` の dry run は、添付済みかどうかを見る前に戻る（`issue_demo.js` の dry run の分岐）。そのため 44 件すべてが `dry-run` と出た。計画の行 5 と AC-4 の「添付済みの Bolt は飛ばされる」は、ここでは「表から bolt-38f2b → #14 が引ける」までを確かめたと読み替える。飛ばすことは、承認後の本番の添付（`issue:attach-demo` の単独の実行）で目印を見て確かめる | `ops/scripts/issue_demo.js` |
| 4 | **GitHub**: 承認の後に `DEMO_BOLT=bolt-38f2b DEMO_ISSUE=14 npx gulp issue:attach-demo` で #14 に添付する | #14 |

### 動画の限界

- 範囲の外の見積依頼と予約は背景の準備で作るので、動画に映らない（範囲の中の見積りの提出は画面の操作として映る）
- 録画にはアドレスバーがないので、範囲の外の URL を開いたことは 404 の画面でしか分からない
- 利用者と荷主の名前は UI テストのもので、dev の営業担当者 0306 ではない
- 割り当ての動画の終わりには、知らせは映るが担当企業の印（担当範囲の領域）は画面の下にあって映らない。印は `uiTest` のシナリオが確かめている
- 3 本とも 2.4〜4 秒と短く、一覧が映るのは一瞬（予約一覧は約 0.1 秒）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38f2b 回（W6。38f2 から動画と添付を分けた Bolt） |
| 期間 | 2026-10-29（計画の承認の記録 `e126da0f2` から、終了報告の承認の依頼まで） |
| 対象 | 3 つの受入シナリオのタグ、`ops/scripts/issue_demo.js` の `BOLT_ISSUES`、運用の索引の添付先の表、動画 3 本 |
| GitHub | [#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14)（動画の添付先。クローズは 38g） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 38f2b 計画](bolt_38f2b_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. タグを足す | `manage_user_assignments_ui.feature` に `@US-16-AC1 @demo @demo-bolt-38f2b/assign`、`restrict_staff_by_assigned_companies_ui.feature` のシナリオの行に `@US-16-AC4 @demo @demo-bolt-38f2b/quotation-scope`、`restrict_bookings_by_assigned_companies_ui.feature` のシナリオの行に `@US-16-AC4 @demo @demo-bolt-38f2b/booking-scope` | `b91e77ec1` |
| 2. 動画を撮る | `assign.webm`（260,307 B、約 2.8 秒）、`quotation-scope.webm`（336,545 B、約 4.0 秒）、`booking-scope.webm`（111,297 B、約 2.4 秒）。撮った後にほかの Bolt の 68 本が変更として出たので `git checkout -- docs/assets/demo` で戻し、残りが bolt-38f2b の 3 本だけであることを確かめた | `b91e77ec1` |
| 3. 添付先の表 | `BOLT_ISSUES` に `'bolt-38f2b': 14`、運用の索引の #14 の行に bolt-38f2b。コメントと索引の文を「新しい Bolt は Bolt の中で足し、動画の添付は終了報告の承認の後に行う」にそろえた。dry run を 2 つ流した | `b91e77ec1` |
| 4. ゲートと報告 | この報告 | この報告のコミット |
| 5. 添付 | 承認の後 | — |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` の `test` 3492 件と `documentationTest` 5 件（`AcceptanceCriteriaCoverageTest` 4 件、`LivingGlossaryConsistencyTest` 1 件）、失敗 0。`uiTest` 340 件、失敗 0（3 つのシナリオを含む。feature で絞る仕組みがないので全件で流した）。`demoVideo` 132 件、失敗 0 |
| dry run | `issue:attach-demo`（bolt-38f2b → #14）は 3 本を添付するコメントを示し、本文に各シナリオの見出しと受入条件（AC1 が 1 本、AC4 が 2 本）と目印が入る。`issue:attach-demo:all` は `bolt-38f2b → #14: dry-run` を出した（議題 3） |
| `okf:check` | ERROR 0 |
| CI | `b91e77ec1` で `check`・`ui`・`deploy-demo` が成功した（デモ環境は v502 で動いている） |

変異と SonarQube は流していない（本体のコードを変えていないため）。

## デモ項目

- 動画: `docs/assets/demo/bolt-38f2b/assign.webm`・`quotation-scope.webm`・`booking-scope.webm`
- マニュアルとキャプチャは変わらない

## 判断と学び

- 動画で見せられるのは画面の操作だけで、背景の準備や画面の下の部分は映らない。動画の限界は計画で受け入れ、根拠をテストに置くと、撮り直しを繰り返さずに済む
- dry run の分岐が判定より前にあると、dry run で確かめられることは限られる。計画の受入条件は、dry run が実際に通る分岐を読んでから書く

### ふりかえり（KPT）

- Keep: 2 つの整合性検証で、タグの置き場所によって issue のコメントから AC が落ちる穴を計画の段階で見つけた。撮り直しで出るほかの動画の差分を計画で予想しておき、戻すところで迷わなかった
- Problem: 計画の AC-4 に、dry run では確かめられないこと（添付済みを飛ばす）を書いた
- Try: T-223: 運用のスクリプトの dry run を受入条件に書くときは、dry run の分岐がどこで戻るかを読んでから書く

### 既知の課題（後に持ち込む）

- 支援技術による手動確認は W10 に送る（38g で #14 を閉じるときの決定）

## 次の Bolt

Bolt 38g（経路設計と追跡の担当範囲の絞り込みと、US-16 の締め。計画は承認済み）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-29 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38f2b 計画](bolt_38f2b_plan.md)、[リリース計画](release_plan.md)（W6 節の 38f2b の行）
- [Bolt 38f2 終了報告](bolt_38f2_report.md)、[運用の索引](../../operation/cargo-tracker/index.md)（動画の添付の手順と添付先の表）
