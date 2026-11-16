---
type: ADR
title: "ADR-021: 荷主への通知の再送の手段と購読の置き場所（提案）"
description: "W9 の持ち越し C-3（通知の失敗の再送）と C-4（通知の購読の置き場所）の選択肢と推奨。人が選ぶまで実装しない。"
tags: [adr, notification]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T02:29:51Z }
---

# ADR-021: 荷主への通知の再送の手段と購読の置き場所（提案）

日付: 2026-11-16

## ステータス

**提案（人の判断待ち）。** W10 の Bolt 49-2。選ぶまで実装の Bolt は作らない（W10 計画のリスク R-4）。

## コンテキスト（確かめた事実）

- 通知の送信は `ShipperNotificationMailService.send` が `MailDelivery.deliver` に任せ、失敗は `pending.failed(reason)` で記録して `LOG.warn` を残す。**再送しない**。リスナーは正常終了するので、発行記録（Spring Modulith の `event_publication`）は完了になる（W9 レビュー M-4）。
- 同じ契機と宛先の通知がすでにあれば送らない（N-INV-02）。再配信されても二重に送らない。
- 4 つの契機（見積りの提示・経路の承認・本予約の確定・追跡が確認中）は `identity` の `ShipperNotificationEventHandler`（126 行）が購読し、宛先の解決と画面のパスの組み立てを行い、`NotificationShipperMails`（ACL）で `notification` の公開 API を呼ぶ。`ShipperNotificationKind` は共有カーネルにある。契機が増えるたびに `identity` が変わる（W9 レビュー M-5。凝集が低い）。
- US-22 AC4（失敗と宛先を記録し、通知一覧から確認できる）は今の実装で満たしている。

## 決定したいこと

### C-3: 失敗した通知の再送

| 案 | 内容 | 利点 | 欠点 |
| :--- | :--- | :--- | :--- |
| A（推奨） | 失敗を `FAILED`（理由と試行回数つき）で記録し、**定期のジョブ**で `FAILED` のうち一時的な理由のものを上限 3 回まで再送する。恒久的な理由（宛先の不備）は再送せず、通知一覧に残す | 発行記録は完了のまま（毒メッセージにならない）。再送の上限が見える。N-INV-02 で二重送信しない | 定期のジョブと ShedLock・監視（W10 の Bolt 53-1）が要る。理由の分類（一時・恒久）を決める |
| B | 失敗でリスナーを例外終了にし、Modulith の再配信（未完了の発行記録）に任せる | 実装が小さい | 恒久的な失敗が何度も再配信され、毒メッセージになる。再配信の間隔と上限を持たない |
| C | 再送しない（今のまま）。失敗の件数を監視し、人が対応する | 変更なし | パイロットの件数では足りるが、メールの一時障害で通知が失われたままになる |

### C-4: 通知の購読の置き場所

| 案 | 内容 | 利点 | 欠点 |
| :--- | :--- | :--- | :--- |
| A | 今のまま `identity` が購読する | 変更なし | 契機が増えるたびに `identity` が変わる。画面のパスの知識が `identity` にある |
| B（推奨） | `notification` が購読し、宛先だけ `identity` の公開 API で引く（ADR-017 の補足の向き） | 契機の追加が `notification` に閉じる。`ShipperNotificationKind` を共有カーネルから `notification` に戻せる | `identity` → `notification` の依存を `notification` → `identity` の公開 API に反転する。購読のテストと配線を直す（見積り 1 Bolt 以上）。モジュールの依存の変更で確認必須 |
| C | 契機を持つ側（quotation・booking・tracking）が通知の公開 API を直接呼ぶ | 購読が不要 | 各コンテキストが通知を知る。疎結合のイベントの利点を捨てる |

## 推奨

**C-3 は A、C-4 は B。** C-3 の実装は W10 の Bolt 53-1（ShedLock・監視）と合わせて行うのが効率的だが、W10 の稼働（99%）に入らないので、**Release 1.1 の頭の返済**に送る。C-4 の反転はモジュールの依存の変更なので、実装の Bolt の計画で人が承認する。

## 影響

- どの案でも、W10 では実装しない。人が決めた案を、次の計画（Release 1.1 の頭）に載せる。
- C-3 を C（再送しない）にするなら、W10 の Bolt 53 で `FAILED` の件数の警告を監視に入れる（確認する）。

## 備考

- 著者: anthropic/claude-sonnet-5-5（AI の提案。**人は未選択**）
- 関連文書: [W9 週全体レビュー](../../review/cargo-tracker/w9_review_20261115.md)（M-4、M-5）、ADR-017
