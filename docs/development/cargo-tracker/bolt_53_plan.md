---
type: Plan
title: "Bolt 53 計画と中間報告 - 監視 1（ヘルスチェック OBS-10。Actuator の導入）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-16T03:49:24Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-16T03:49:25Z }
---

# Bolt 53 計画と中間報告 - 監視 1（ヘルスチェック OBS-10。Actuator の導入）

[W10 計画](iteration_plan-W10.md)の Bolt 53（技術、SP 0、見積り 200 分）のうち、**OBS-10 のヘルスチェックだけ**を終えた。ほかの OBS（取込がない日の通知 FRS-04、隔離の未判断の警告 OBS-06、DE-12・DE-30・DE-13 の未完了の検知、拒否の件数）は未着手。

**状態**: 承認済み（2026-11-16、human:kakimomokuri。Actuator の導入と、認可の設定の変更 `SecurityConfiguration.actuatorFilterChain` を含むコードの承認）。業務の連鎖は変えていない。検証済み: `validating-design`（A 終盤の技術 Bolt で TDD。B `operation.md` の OBS-10 と ALB のヘルスチェックの定め。C Bolt 51 の Terraform のヘルスチェックのパスと一致させた）。

## 決定

| # | 決定 | 理由 |
| :--- | :--- | :--- |
| 1 | ヘルスは管理用のポート 8081（`management.server.port`）に分け、`/actuator/health` だけを出す。詳細は出さない（`show-details=never`） | 業務のポートに Actuator を出さない。ALB は認証なしで読める |
| 2 | `/actuator/**` 専用の連鎖を `@Order(1)` で足し、ヘルス以外は拒否（403）。業務の連鎖は変えない | 管理用のポートにも既存の連鎖が掛かって 302（ログインへ）になることを、先に失敗するテストで確かめた |
| 3 | `SessionRevalidationSecurityIntegrationTest` の「フィルターの順序」は、連鎖が 2 つになったので「期限のフィルターを持つ連鎖」を探す形に直した | 「最初の連鎖が業務の連鎖」という前提がなくなったため。テストの意図（順序の見張り）は変えていない |
| 4 | Terraform の ALB のヘルスチェックを 8081 に合わせ、セキュリティグループとコンテナのポートに 8081 を足した | `fmt`・`validate` は成功 |

## 承認の議題

| # | 議題 | 推奨 |
| :--- | :--- | :--- |
| 1 | 認可の設定の追加（`actuatorFilterChain`）。管理用のポートは VPC 内（ALB とタスクの間）だけに届き、インターネットには出さない前提。ヘルスは認証なし | 承認。本番の前に、管理用のポートが ALB 以外から届かないこと（セキュリティグループ）を `plan` で確かめる |
| 2 | 全体のゲートは、最初の流しで 1 件失敗（上の決定 3）。直したあとは `spotlessCheck check documentationTest uiTest` を全体で流し直して**成功**した（2026-11-16、30 分）。Sonar は未実施 | Sonar は W10 の締め（Bolt 56）で流す |
| 3 | 残りの OBS は未着手（FRS-04・OBS-06・DE-12/30/13 の検知・拒否の件数） | 次の Bolt（53 の続き）で、運用の通知の宛先（チャット・メール）の決定が要る（運用設計「要確認」） |

## 計画

- [x] (1) 管理用のポートのヘルス（Red → Green）
- [x] (2) Terraform の ALB のヘルスチェックを合わせる
- [ ] (3) FRS-04・OBS-06・DE-12/30/13 の検知（通知の宛先が未決）
- [ ] (4) 全体のゲート・Sonar

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-16 | 初版（draft。中間報告つき） | anthropic/claude-sonnet-5-5 |
