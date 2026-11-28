---
name: project-bolt34e-review
description: Bolt 34e（User の承認の値・結果の型の分割・ManagedUserLookup・IdentityLogins）の開発レビューで出した指摘と、次に見るべき残り
metadata:
  type: project
---

Bolt 34e（2026-10-17）の開発レビューの要点。

- 承認の値は id と版だけで縛るので、`User.of` で同じ id・版の別の状態の利用者を作って判定すれば、規則を外した承認が作れる（コンパイラの守りは「判定を通す」までで「同じ状態を判定した」までは守らない）。判定した実体への参照で縛る案（`approval.decidedOn == this`）を出した（要判断）。
- 版の出どころは承認の 1 つにしたが、利用状態の `to`・`reason` と役割の `newRoles` は引数から監査・副作用に使っており、`StatusChangeApproval.reason` は本体で使われていない。
- architecture_backend に、承認の値を `valueobjects` でなく `User` の入れ子に置く理由が書かれていない（計画の確認ポイント 4 の約束。CLAUDE.md の置き場所の例外の記録）。
- `IdentityLogins.PASSWORD` と各クラスの `PASSWORD` の 12 の写し。totpCodes は spy の基底の spy なので「spy を持たない」は利用者のリポジトリについてだけ正しい。

**Why:** 34e は「判定を通さずに変えられない」を型で守る Bolt。守りの強さの言い方と文書の置き場所の記録が次の Bolt（34f・W6 の 3 つ目の操作）の前提になる。
**How to apply:** W6 で管理の操作を足すときは、同じ承認の形を写すか、実体の参照で縛る形に寄せるかを先に確かめる。[[project-bolt34d-review]] [[project-bolt34-review]]
