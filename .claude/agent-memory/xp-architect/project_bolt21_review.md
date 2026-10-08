---
name: project-bolt21-review
description: cargo-tracker Bolt 21（KPI-01 の DE-03 購読）のアーキテクトレビューの指摘。listener の例外で未完了を残す判断の毒メッセージ化、日時書式の 3 重の写し、開発戦略のアプローチ規則
metadata:
  type: project
---

2026-10-08 に Bolt 21（9fc50d0f..7097c244）をレビューした。H1（identity → quotation :: events だけ）と H2（条件付き UPDATE で冪等・順序を吸収）は成立と評価。主な指摘:

- KpiObservationEventHandler が「提示時刻が提出時刻より前」でも例外を投げ未完了を残す。再配信で直らない不変条件違反なので毒メッセージになり、ADR-014 の「再配信しても変わらない不正は例外にしない」と食い違う。W10 の定期再配信で増幅する
- 例外で未完了を残すことの統合テスト（IncompleteEventPublications に残り、再配信で記録される）がない。単体で例外を確かめるだけ
- 社内の日時表示の書式が quotation・routing・identity の 3 か所に写された。計画の確認ポイント 7 自身が「3 つ目で shared に移す」と書いていた。shared は OPEN の共有カーネル（値オブジェクト）なので、表示の部品は platform 側か shared の別パッケージに置く案を提示
- 期間の表示も 3 種類（elapsed・duration・leadTime）で書式が揃っていない
- 新しい listener は過去の完了済み DE-03 を受けないため、デモ環境の既存の提示済み行は「未提示」のまま（seed の 3 件だけ補正）
- 確認ポイント 12: 規則表は「対象の新旧」の 1 軸で、Bolt 19・20 は「列を足す」を新スキーマ寄りに読んだ。不確実性の所在の軸を足すか、逸脱を計画に理由付きで書けるようにする案

**Why:** Bolt 1 で指摘した「未完了が残り続ける」リスク（[[project-bolt1-review]]）と同じ型が、意図的な例外として戻ってきた。
**How to apply:** 次の Bolt で listener を足す・W10 で定期再配信を入れるときに、恒久的な失敗と一時的な失敗を分けたかを最初に確認する。
