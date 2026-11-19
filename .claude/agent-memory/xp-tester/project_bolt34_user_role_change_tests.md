---
name: project-bolt34-user-role-change-tests
description: Bolt 34（US-16 役割の付与・取消し）のテストレビューで残した指摘。リポジトリの一覧の束ね未テスト、コントローラーの 400/404 未検証、職務分離の知識の重複
metadata:
  type: project
---

Bolt 34 のレビュー（2026-10-16）で出した主な指摘。

- MyBatisUserRepository の一覧・詳細（行を利用者ごとに束ねる group/roles、役割 NULL の行）にリポジトリの統合テストがない。MockMvc の文字列 contains だけ
- UserAdministrationController の境界（知らない役割名 400、不正な UUID 404、version の欠落・非数 400、POST の不正 UUID）を確かめるテストがない
- 役割を「外すだけ」（SALES+ROUTE → SALES）の User 単体テストがない
- controller の businessRoles が RoleAssignmentPolicy の OVERSIGHT_ROLES を重複して持つ
- RoleChangeResult が check と change で共用され、届かない枝を IllegalStateException で埋めている
- UI の Stale シナリオは開き直した後の再変更の成功を確かめない。自分の変更の拒否シナリオは DB 不変を確かめない
- AC2 の試行の記録・利用停止は 34b に送られている（確認ポイント 7）。指摘の対象外

**Why:** 34b・34c で同じ画面とサービスを広げるときに、抜けがそのまま広がるため。
**How to apply:** 34b・34c のレビューで上の抜けが埋まったかを先に確かめる。[[project-bolt33c-session-keys-refactor-tests]]
