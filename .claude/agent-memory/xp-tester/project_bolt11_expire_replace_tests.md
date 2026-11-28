---
name: project-bolt11-expire-replace-tests
description: Bolt 11（見積りの失効と置換、US-03 AC4・AC5）のテストレビューで見つけた抜け（2026-10-05）
metadata:
  type: project
---

Bolt 11 のテストレビュー（git diff ffeec0d..HEAD）で指摘した未解消点。

- 荷主側（C-04）で「提示済みのまま有効期限を過ぎた見積り」を失効・読み取り専用と示すテストがない（TransportRequestControllerTest は期限前の固定時計だけ）
- 「承認待ちのまま置換した見積りは荷主に見えない」（findVisible の presentedAt フィルタ）を確かめるシナリオがない
- 画面の層は実時計で動き、失効の準備を DB の CURRENT_TIMESTAMP で直接書き換える（JVM と DB の時計差、ui.base-url 指定時に別 DB）
- 再見積りの save(replacement) が例外のときの旧版のロールバックは、メモリ上のリポジトリ（ロールバックなし）でも統合テストでも未検証
- 同時の再見積りは楽観ロックのスタブだけ。実 DB の 2 トランザクションは未検証（Bolt 5 からの持ち越し）
- 再見積りの TRANSPORT_REQUEST_NOT_QUOTING（状態・版の不一致）が未テスト
- 業務ルール層の Red は再びコンパイルの失敗だけ（R-38）

**Why:** US-24（荷主の承認）・US-04（予約確定）で同じ失効判定を使うため、判定時刻の扱いと荷主側の表示が積み重なる。
**How to apply:** 次の見積り関連 Bolt では、荷主側の失効表示と、画面の層で時計を差し替えられるか（MutableClock は業務ルール層と StaffQuotationControllerTest だけ）を最初に見る。[[project-bolt9-10-quotation-tests]]
