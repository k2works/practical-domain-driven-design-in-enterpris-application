---
type: Plan
title: "Bolt 40-17 終了報告 - W8 の週全体レビューの指摘 A・B・C・D を直す"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T24:30:00Z }
---

# Bolt 40-17 終了報告 - W8 の週全体レビューの指摘 A・B・C・D を直す

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **スキーマの変更**: `service_case.origin_ref VARCHAR(200)` と UNIQUE 索引（移行 `V20261120100000`）。人が「W8 でやる」と選んで承認済み。利用者の起票は NULL（NULL の重なりは許す）。列の注釈・実 PostgreSQL の一意制約の統合テストは緑 | 承認（人の決定） | 計画 (A) |
| 2 | **カレンダー未登録のときの動き**: システム起票は既定のカレンダー（平日 9:00〜18:00、土日休み、**祝日なし**）で期限を計算して起票する（人が選んだ）。**祝日の分は期限が実際より短くなる**。利用者の起票は従来どおり起票しない。業務責任者に確かめる（台帳 D-163。仮） | 承認（仮） | release_plan D-163 |
| 3 | **`RejectionCaseOpener` を `commandservices` へ移した**（人が選んだ）。Bolt 40-13 で人に諮らず置いた規律の違反を解消した | 承認（人の決定） | 計画 (C) |
| 4 | **認可の一致の統合テスト**を足した（レビュー D の一部）。詳細の `canAccess` の統一とシステム起票の分離は W9（US-23 の前）に残す | 承認 | 計画 (D) |
| 5 | **作業の流儀（私の側）**: 標準入力につながる形（空の heredoc）をまた 1 回書いた（何も読まず何も変えていない）。以後は Edit/Write だけを使う | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-17 回（W8 の追加。週全体レビューの指摘の実施） |
| 期間 | 2026-11-14 |
| 種別・SP | 技術・SP 0 |
| 見積り／実績 | 見積り 150 分。**実績は約 110 分（概算）、差は約 −40 分** |
| 計画 | [Bolt 40-17 計画](bolt_40-17_plan.md) |

## 成果

起票元キー（移行・`findByOriginRef`・`recordOrigin`・`openBySystem` の起票元キーと冪等）、既定のカレンダー、`RejectionCaseOpener` の置き直し、認可の一致の統合テスト。テスト: `ServiceCaseCommandServiceTest` に 5（行 15〜19）、`RejectedMilestoneCaseOpenerTest`・`RouteCandidatesNotFoundEventHandlerTest` を新しい仕様に合わせて更新（終結後の再配信・再算出）、`MyBatisServiceCaseRepositoryIntegrationTest` に 2（一意制約）、`TrackingAssignedScopeSecurityIntegrationTest` に 1（すべての役割で一致）。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest uiTest` | BUILD SUCCESSFUL（28 分 33 秒） |
| SonarQube | **PASS**（新しい違反 0） |

## 完了条件

- [x] 上のテストが緑
- [x] `check documentationTest uiTest` と SonarQube が緑
- [x] 設計文書を同じ Bolt で更新した
- [x] 見積り／実績／差を書いた

## 申し送り

- W9（US-23 の前）: 詳細の `canAccess` の統一、システム起票の分離（ArchUnit で interfaces からの呼び出しを禁じる）、AC1 の同時採番の統合テスト、`inquiry_record.case_number` の索引。
- 業務責任者に確かめる: D-163（既定のカレンダーで期限を計算してよいか）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。人が選んだ決定（A・B・C）の実施。議題を推奨のとおり承認として記録（human:kakimomokuri） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
