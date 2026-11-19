---
name: project-bolt34f-s20-controller-split-tests
description: Bolt 34f（S-20 の controller の 3 分割と UserDetailPage）の WebMvc 特性テストのレビューで見つけた抜け（2026-10-18）
metadata:
  type: project
---

Bolt 34f のテスターのレビュー（2026-10-18、読むだけ）で見つけた未解消点。

- 拒否の Rejected の画面の版（送られた版のまま）は、どのテストも区別しない。WebMvc のテストは再読の利用者の版と送る版が同じ 3 なので、`version` を `user.version()` にする変異が生きる（役割・利用状態の両方）。NO_CHANGE だけは最新の版を確かめている
- 利用状態の Rejected の「選んだ理由のまま」（`request.reason()`）は、理由のある拒否のテストがなく生きる
- 役割の Stale のテストは見出し「役割を変更できませんでした」を見ない（利用状態の Stale は見る）。`section` を STATUS に固定する変異が役割側で生きる
- 役割の拒否の文言 `UserRoleChangeController.errorMessage` に単体テストがない。NO_CHANGE・UNFIT_FOR_COMPANY の文言はどこにも断言がない（34f で企業の種類の名前を Labels に替えた枝）
- 詳細の GET の範囲外は記録しない守りは WebMvc になく、セキュリティの統合テスト（詳細 GET と確認 GET の後に 0 件）だけ
- 役割の確定には「値の誤りより先に範囲外を記録する」テストがない（利用状態にはある）。`@RequestParam long version` の束縛が先なので、範囲外への数でない版は記録なしで 400（34f 前からの非対称）
- 否定の文字列の断言（`not(containsString("value=\"SALES\" checked"))` など）は属性の順・書式が変わると空振りする
- 分けた 3 つのテストクラスに使わない定数と `suspend()` などの写しが残る
- uiTest の XML は Cucumber の報告が -1・-2 の 2 本に出るので、シナリオ 1 つで件数が 2 増える（272→274）

**Why:** 34f は DetailForm の誤りを 1 つにまとめて値の流れ（版・理由・領域）を組み替えたが、変異は記録の向きと判定の順に寄っていた。
**How to apply:** W6 で 4 つ目の controller（登録・TOTP の解除）を足すとき、UserDetailPage.refuse の値の流れを確かめるテストを先に足すよう勧める。[[project-bolt34e-user-admin-refactor-tests]] [[project-bolt34b-admin-scope-tests]]
