---
type: Plan
title: "Bolt 40-14 終了報告 - 候補なし・外部データの欠落の理由の表示と有人案件への引き継ぎ（UC-06 3a）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-14T11:00:00Z }
---

# Bolt 40-14 終了報告 - 候補なし・外部データの欠落の理由の表示と有人案件への引き継ぎ（UC-06 3a）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **構造の変更**: 経路設計から追跡への新しい通知をイベント（DE-32 `RouteCandidatesNotFound`）にした。追跡のモジュールの依存に `routing :: events`（公表された言語）を足した。**経路設計は追跡に依存しない**（ADR-014 の方向）。モジュールの構造の検証（ArchUnit・Modulith）は緑 | 承認 | 計画 決定 (1) |
| 2 | **`kind` の割り当ては仮（台帳 D-161）**: 候補なし → 見積り相談（優先度 通常）、外部データの欠落 → 外部停止（優先度 高）。`kind` の CHECK は変えない。**外部停止は復旧照合が要る案件になる**ので、終結には復旧照合（W11 の画面）が要る。業務責任者に確かめる | 承認（仮） | release_plan D-161 |
| 3 | **発行の条件**: 候補が 0 件、または適合が 1 件もない。**すべて貨物種別だけで除外された案件は引き継がない**（Bolt 39i の手動の窓口のまま） | 承認 | 計画 決定 (2) |
| 4 | **S-06 の表示**: 理由の文は変えず、引き継いだ旨（種類つき）を足した。受付番号は非同期の起票なので S-06 には出さず、「有人案件」の一覧で確かめる。マニュアル 8 章・ui_design・system_usecase・domain_model を同じ Bolt で更新した | 承認 | ui_design S-06 |
| 5 | **画面の層のシナリオは足していない**: 起票は非同期のイベント経由で、画面の層（実ブラウザ）の準備に航海なしの案件の流れが要るため、40-16 の受入条件の照合で確かめる。単体（案件・コマンドサービス・購読）で縛った | 承認 | 申し送り |
| 6 | **作業の流儀（私の側）**: 今回、標準入力につながる形は使っていない | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-14 回（W8 の 15 本目） |
| 期間 | 2026-11-14 |
| 種別・SP | 機能・SP 0 |
| 見積り／実績 | 見積り 180 分。**実績は約 130 分（概算）、差は約 −50 分**（40-13 の起票の経路が再利用できた） |
| 計画 | [Bolt 40-14 計画](bolt_40-14_plan.md) |

## 成果

`RouteCandidatesNotFound`（DE-32）、`CandidatesNotFoundReason`、`RoutingCase.candidatesNotFoundReason`・`candidatesNotFound`、`RoutingCaseCommandService` の発行、追跡の `RouteCandidatesNotFoundEventHandler` と配線・依存、S-06 の引き継ぎの文。テスト: `RoutingCaseCandidatesNotFoundTest` 4、`RoutingCaseCommandServiceHandoffTest` 2、`RouteCandidatesNotFoundEventHandlerTest` 5。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check documentationTest uiTest` | （結果は更新履歴） |
| SonarQube | （結果は更新履歴） |

## 完了条件

- [x] 上のテストが緑
- [ ] `check documentationTest uiTest` と SonarQube が緑
- [x] 設計文書とマニュアルを同じ Bolt で更新した
- [x] 見積り／実績／差を書いた

## 申し送り

- Bolt 40-15: 画面 S-23 照会の記録と有人案件への引き継ぎ（US-11 AC2）。**スキーマの確認（表 `inquiry_record`）が要る**。
- 40-16 の受入条件の照合で、UC-06 3a の画面の層のシナリオ（航海なしの案件の算出 → S-06 の引き継ぎの文 → 有人案件の一覧）を足す。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-14 | 初版作成。推奨のとおり承認として記録（human:kakimomokuri。「以降 Recommended で自律実行して」の指示） | anthropic/claude-sonnet-5-5、承認 human:kakimomokuri |
