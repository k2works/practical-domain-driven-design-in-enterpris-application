---
name: project-bolt20-shipper-approval-tests
description: Bolt 20（経路版の割当てと荷主の承認、US-24 AC4・AC5、ADR-014）のテストレビューで見つけた抜け（2026-10-08）
metadata:
  type: project
---

Bolt 20 のテストレビュー（git diff d13853a..e1184c7）で指摘した未解消点。

- 新しい listener 3 つ（QuotationRouteAssignedEventHandler・QuotationApprovedByShipperEventHandler・routing の QuotationRouteAssignmentEventHandler）と RouteAssignmentService・ACL に単体テストがない。警告のログの経路（見つからない・版の食い違い・業務の拒否）は未検証。既存の RouteDesignRequestedEventHandlerTest の ListAppender 形が手本
- 既存の DE-03・DE-16 の handler の isAlready* が AWAITING_APPROVAL・READY_TO_BOOK を含まず、遅れて届いた・再配信の DE-03・DE-16 で偽の警告が出る
- QuotationResponseService.approve の Conflict・NotFound の写像はサービスの単体テストなし（QuotationResponseServiceTest 未更新）
- 承認済みの見積りも有効期限の後は isExpiredAt=true で、画面の状態が「失効」になる（予約待ちと矛盾し得る）。設計判断とテストが要る
- RouteConfirmedAssignmentIntegrationTest はコミットし、2099 年の JPTYO→NLRTM 直行の航海を共有コンテナに残す
- 受入の「置換済み」の写像はシナリオで未使用。DE-21・DE-04 の再配信は受入で未検証（ドメインの単体だけ）

**Why:** US-04（本予約）・W6（DE-06 再設計）で同じ listener の形と失効判定を積み重ねるため。
**How to apply:** 次の見積り・経路設計連携の Bolt では、listener の単体テスト（ログの捕捉）と、承認済みの見積りの失効の扱いを最初に確認する。[[project-bolt11-expire-replace-tests]]

**対応（同じ Bolt のレビュー対応 D-73〜D-76、`497c150`・`28c9e74`）:** listener 3 つ・RouteAssignmentService・荷主の承認の入力ポートの単体テスト、DE-03・DE-16 の偽の警告（`TransportRequest.hasReached`）、承認の後の失効の C-04 の案内と S-02 の「失効（荷主承認済み）」は直した。残りは、受入での DE-21・DE-04 の再配信、「置換済み」の写像、コミットする統合テストの航海の後始末（D-78）。
