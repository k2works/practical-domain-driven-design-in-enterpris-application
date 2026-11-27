---
type: Plan
title: "Bolt 61 計画と報告 - 失敗した荷主への通知の再送"
description: "W10-1 計画の Bolt 61（技術、SP 0。"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T11:14:30Z }
---

# Bolt 61 計画と報告 - 失敗した荷主への通知の再送

[W10-1 計画](iteration_plan-W10-1.md)の Bolt 61（技術、SP 0。ADR-021 の C-3）。

**状態**: スキーマ、再送の範囲、残るリスクの受け入れは W10-1 計画の CP-5 で承認済み（2026-11-27、human:kakimomokuri）。この報告は 56-1 でまとめて承認を受ける。検証（`validating-design`）: A 終盤の技術 Bolt。B N-INV-02 は同じ行の再送で守る（`domain_model.md` と `data_model.md` を直した）。N-INV-01・03 は変えない。C 定期処理は Bolt 53-1 の ShedLock、H2 と PostgreSQL の共通の構文（ADR-007）。

## 計画と結果

- [x] (1) 状態遷移、再送のサービス、SMTP の失敗の分類、定期処理、再送の列と引き取り（H2・PostgreSQL 共通の契約）のテストを先に書き、コンパイルの失敗を確かめた
- [x] (2) `V20261127100000__add_notification_retry.sql`（`link`・`retry_count`・`retryable` と CHECK 3 つ）
- [x] (3) 集約の `retry`、`ShipperNotificationRetryService`、`ShipperNotificationRetryScheduler`（10 分ごと）、`MailSender.isTransientFailure`
- [x] (4) `data_model.md`、`domain_model.md`、マニュアル 15 章

## 守り（CP-5）

| 項目 | 守り方 |
| :--- | :--- |
| トークンを含むリンクを保存しない | 集約が再送しない種類のリンクを拒否し、表の CHECK も拒否する（両方にテストがある） |
| 二重に引き取らない | 失敗のままで回数が 1 つ前の行だけを更新し、更新できたときだけ送る |
| 打ち切り | 3 回、または記録の作成から 24 時間 |
| 失敗の理由 | 最初の送信と同じ処理で、メールアドレスを伏せる |

## 承認の議題

| # | 議題 | 内容と推奨 |
| :--- | :--- | :--- |
| 1 | **一時の失敗は接続と通信の失敗だけ。SMTP の 4xx の応答は再送しない** | 応答コードを読む型がコンパイルの依存にない（`jakarta.mail-api` だけ）。「分類できない失敗は再送しない」（CP-5）のとおりにした。SES の一時的な制限（4xx）を再送したい場合は、実環境で失敗の形を確かめてから分類を足す。推奨: このまま |
| 2 | 受け入れ済みの残るリスク | 失敗の後に利用停止になった人へ届きうる。応答の時間切れで二重に届きうる。引き取った後にアプリが止まると送信待ちのまま残る（最初の送信と同じ） |

## 実装の中で分かったこと

最初の `check` で 2 件失敗した（`Notification` のコンストラクターの循環的複雑度、Bolt 57-2 の `EventBacklog` が用語集にない）。57-2 では `documentationTest` を流していなかった。直して全件を流し直し、成功した。また、置換のスクリプトの一部が一致せずに黙って通り過ぎ、コンパイルで気づいた。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check`（単体・統合・アーキテクチャ・カバレッジ・静的検査）と `documentationTest` | BUILD SUCCESSFUL |

## 未確認

- 実際の SMTP（SES）の失敗がどの例外になるか。分類のテストは例外を組み立てて確かめた。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-27 | 初版 | anthropic/claude-sonnet-5-5 |
