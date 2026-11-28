---
name: project-bolt34-review
description: Bolt 34（US-16 役割の付与・取消し）のアーキテクト レビューで出した指摘。最後のシステム管理者、二重読み出し、監督の役割の写し
metadata:
  type: project
---

Bolt 34（2026-10-16、差分 0ca36cbed..681cd4b25）のアーキテクト レビューの指摘。

- 中: 最後のシステム管理者を外す変更を止めていない。自分の変更は拒むが、2 人の管理者が互いに SYSTEM_ADMIN を外すと（別の行なので版で衝突しない）0 人になる。計画・設計文書に記述なし
- 中: UserRoleChangeService の refusal と changeInTransaction で findById を 2 回。判定は 1 回目、監査の変更前は 2 回目。正しさは「期待する版」を repository に別に渡すことに頼っている（changed.version() を使うと未判定の状態で書ける）。1 回の読み出しを判定と変更で共有する形を勧めた
- 中: UserAdministrationController.businessRoles が SYSTEM_ADMIN・AUDITOR の集合を写している（RoleAssignmentPolicy の OVERSIGHT_ROLES の写し）。architecture_backend の「controller は 2 つのサービスだけを呼ぶ」とも食い違い（RoleAssignmentPolicy.unfitRoles を直接呼ぶ）
- 低: User.changeRoles が判定を通さずに作れる（不変条件が呼ぶ側頼み）。計画は roleChangeRejection(UserId, CompanyKind, …) だが実装は Company を受け IAE
- 低: app_user.version は password の再設定でも上がるので、再設定と役割の変更が重なると Stale になる（受け入れ可、34b の利用停止も同じ）
- 良: 版の条件付き更新・差分・監査を 1 tx、同時の 2 変更の統合テスト、監査失敗のロールバックテスト、password_hash を読まない射影

**Why:** 34b（管理範囲・利用停止）・34c（@Service/@Bean 二重定義）・W6 で同じ箇所に触るため。
**How to apply:** 34b のレビューで最後の管理者の扱いと、利用停止が version を上げることを確かめる。[[project-bolt33f-review]]
