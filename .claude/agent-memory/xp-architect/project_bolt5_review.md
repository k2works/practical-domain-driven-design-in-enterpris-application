---
name: project-bolt5-review
description: Bolt 5（US-02 審査）のアーキテクトレビューで指摘した未解決の論点。審査記録の DB 一意制約、update の全件 INSERT、N+1、社内照会の取り違え防止、CSRF
metadata:
  type: project
---

Bolt 5 レビュー（2026-10-02）で指摘した論点。次の Bolt で直ったか確かめる。

- review_record に (transport_request_id, version_no) の UNIQUE がない。Q-INV-04/14（1 版 1 判断）が楽観ロックだけで守られている
- MyBatisTransportRequestRepository.update が審査記録を毎回全件 INSERT ... WHERE NOT EXISTS する（履歴に比例して往復が増える）。集約は不変条件に審査記録を使っていない
- findUnderReview・findByNumberForStaff が行ごとに selectReviewRecords（N+1、報告書で既知）。status 単独の索引もない
- findByNumberForStaff が荷主用と同じ TransportRequestQueryService にあり、ArchUnit で顧客側コントローラーからの呼び出しを禁じていない
- Spring Security がなく POST /staff/** に CSRF 防御がない。decision が APPROVED 以外なら何でも差戻し扱い
- 集約の aggregateVersion が final で、update 後のインスタンスは古い版を持つ

**Why:** 審査は業務の証跡で、二重判断や他社参照の取り違えは監査（US-17）と US-18 で問題になる。
**How to apply:** US-18（認証）、US-17（監査）、W10（性能）の Bolt のレビューで再確認する。[[project-bolt4-review]] の R-25〜27 と合わせて追う。
