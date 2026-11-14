---
type: Plan
title: "Bolt 43-2 終了報告 - 見積りの有効期限の 24 時間前の通知（US-22 AC2）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T13:00:00Z }
---

# Bolt 43-2 終了報告 - 見積りの有効期限の 24 時間前の通知（US-22 AC2）

## 承認の議題（先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **定期実行の導入**: アプリに初めて `@Scheduled`（毎時 0 分）と `@EnableScheduling` を入れた。外部ライブラリの追加・スキーマの変更はない。排他（複数インスタンスの二重検出）は宛先の一意制約が 2 通を防ぐ。ShedLock と監視は W10 | 承認 | [計画](bolt_43-2_plan.md) |
| 2 | **窓の方式の弱点**: 検出が動かなかった時間の見積りは通知されない（期限の印の列を足さない選択）。気にするなら、印の列（スキーマ）を足して「窓に入ったのに未通知」を拾い直す案がある。W10 の運用の監視とあわせて決める | 承認（弱点を了承） | 計画の決定 |
| 3 | 新しいイベント `QuotationExpiring`（見積りの公表されたイベント）を足した。宛先の解決は Bolt 43 で人が決めた方式のまま identity が行う | 承認 | 計画 (3) |
| 4 | **作業の流儀（私の側）**: 標準入力につながる形（空の heredoc）をまた 1 回書いた（何も読まず何も変えていない。43 に続いて 2 回目）。以後は Edit/Write だけを使う | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 43-2 回（W9。機能。US-22 AC2） |
| 期間 | 2026-11-14 |
| SP | 0（US-22 の 3 SP は 43-3 で数える） |
| 見積り／実績 | 見積り 180 分。実績は概算 90 分 |
| 計画 | [Bolt 43-2 計画](bolt_43-2_plan.md) |

## 成果

`QuotationExpiring`、`QuotationExpiryNotificationService`（窓の検出と発行）、`QuotationExpiryScheduler`（毎時 0 分）、`findAwaitingResponseExpiringBetween`（MyBatis と見本のリポジトリ）、identity のハンドラーの 1 つ。テスト: サービス 3（窓の内・外・境界、輸送要求なし）、起動 3、ハンドラー 1、`MyBatisQuotationIntegrationTest` に 1（窓の境界）。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `spotlessCheck check documentationTest uiTest` | BUILD SUCCESSFUL（31 分 15 秒） |
| SonarQube | **PASS**（新しい違反 0、カバレッジ 95.9%。1 回目は 1 件を指摘され、直して流し直した） |

## 申し送り

43-3: 通知一覧（C-16。AC4 の「画面からも同じ内容を確認できる」。通知の失敗の宛先を含む）、マニュアル、US-22 を締める。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版 | anthropic/claude-sonnet-5-5 |
