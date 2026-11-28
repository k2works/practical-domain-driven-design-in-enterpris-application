---
name: project-bolt34e-user-admin-refactor-tests
description: Bolt 34e（承認の値・結果の型・ManagedUserLookup・IdentityLogins）のテストレビューで残した未解消点（2026-10-17）
metadata:
  type: project
---

Bolt 34e は振る舞いを変えないリファクタリング。件数は UserTest だけ 32→37、ほかは前後同じ。

未解消点（テスターのレビュー、2026-10-17）:
- 利用状態の承認の「別の版」のテストがない（requireOwn を共有しているので今は役割の版のテストで落ちる。分かれたら守りがない）
- StatusChangeApproval.reason() は本体で使われずテストだけが読む。decideStatusChange は再開でも reason を入れる（Javadoc の「再開なら null」と食い違う。controller の 400 で今は届かない）
- 承認の値の防御の複製（EnumSet.copyOf・Set.copyOf）を外す変異が生き残る
- 所属企業がない拒否は利用状態のサービスのテストだけが通す（役割のサービスにない）。役割のサービスに「規則 → 版」の順のテストがない（利用状態にはある）
- IdentityLogins.login の TOTP の後の確かめは否定形（error・expired を含まない）。/login への戻りは素通りする
- IdentityLogins.PASSWORD を定義したが、10 クラスが同じ password の定数を写したまま
- controller の OutOfScope・NotPermitted の枝は 34b・34d から未検証のまま（[[project-bolt34b-admin-scope-tests]]、[[project-bolt34d-user-status-change-tests]]）

**Why:** 次の 34f（controller の分割）で同じ枝を触るので、守りの抜けを先に知っておく。
**How to apply:** 34f のレビューで上の点が直ったかを最初に確かめる。
