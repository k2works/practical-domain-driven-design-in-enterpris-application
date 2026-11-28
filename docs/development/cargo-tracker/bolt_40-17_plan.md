---
type: Plan
title: "Bolt 40-17 計画 - W8 の週全体レビューの指摘 A・B・C・D を直す（起票元キー、カレンダー未登録、置き場所、認可の一致）"
description: "W8 の追加の Bolt（技術、SP 0）。人が選んだ決定（2026-11-14）に従い、システム起票の冪等を起票元キーと一意制約にし、カレンダー未登録でも既定のカレンダーで起票し、RejectionCaseOpener をアプリケーション層に置き直し、認可の集合の一致を統合テストで縛る。"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T24:00:00Z }
---

# Bolt 40-17 計画 - W8 の週全体レビューの指摘 A・B・C・D を直す

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-17 回（W8 の追加。[W8 週全体レビュー](../../review/cargo-tracker/w8_review_20261114.md)の「人に諮る指摘」の実行） |
| 状態 | 計画（**承認済み**。2026-11-14 に人（human:kakimomokuri）が選んだ決定: A=W8 で Bolt 40-17 としてすぐやる、B=W8 で期限を既定値で仮置きして起票する、C=アプリケーション層に置き直す。D は推奨のとおり） |
| 種別・SP | 技術・SP 0 |
| 見積り | **150 分**（作業のみ） |
| 承認ゲート | **スキーマ**（`service_case.origin_ref`。人が選んで承認済み）。認可・外部連携の変更はない |

## 決定

(A) **起票元キー**: `service_case` に `origin_ref VARCHAR(200)` と UNIQUE 索引を足す（移行 `V20261120100000`）。システム起票（DE-13 の拒否・DE-32）は起票元キーで冪等にする（`ServiceCaseCommandService.openBySystem(draft, 起票者, 起票元キー)`）。同じキーなら案件を終結した後でも新しく起票せず、起票済みの受付番号を返す。並行して同じキーが記録されたら一意制約が守り（違反はトランザクションを戻して再配信 → 次は既存を返す）、再算出は別のキー。ポート `ServiceCaseRepository` に `findByOriginRef`・`recordOrigin`。

(B) **営業カレンダー未登録**: システム起票は既定のカレンダー（平日 9:00〜18:00 の日本時間、土日休み、祝日なし）で期限を計算して起票する（イベントが毒メッセージにならない）。利用者の起票は従来どおり「営業カレンダーが未登録」で起票しない。祝日の分は期限が実際より短くなる（台帳 D-163。業務責任者に確かめる）。

(C) **置き場所**: `RejectionCaseOpener` を `domain.model.aggregates` から `application.internal.commandservices` へ移す（外部の技術のポートではないので）。ADR-017 の例外は作らない。

(D) **認可の一致**: URL の規則と `ServiceCaseAuthorization.canOperate` の集合が、すべての役割で一致することの統合テストを足す。

## ステップ

| ステップ | 内容 | 分 |
| :--- | :--- | ---: |
| 1 | テスト（起票元キー、既定のカレンダー、実 DB の一意制約、認可の一致） | 50 |
| 2 | 移行、ポート・MyBatis・サービス、呼び出し側、置き直し | 60 |
| 3 | 設計文書・台帳 D-163、品質ゲート、終了報告 | 40 |

## 完了条件

- [ ] 上のテストが緑
- [ ] `check documentationTest uiTest` と SonarQube が緑
- [ ] 設計文書を同じ Bolt で更新した
- [ ] 終了報告に見積り／実績／差を書いた

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。人が選んだ決定を計画にした（human:kakimomokuri） | anthropic/claude-sonnet-5-5、決定 human:kakimomokuri |
