---
name: project-bolt34d-review
description: Bolt 34d（US-16 AC5 利用停止と再開）のアーキテクト レビューの指摘。停止→再開で旧 session が残る、NO_CHANGE の古い版の再表示、管理の前置きの 2 写し、controller の分割の切れ目
metadata:
  type: project
---

Bolt 34d（2026-10-17、差分 73f824317..HEAD）のアーキテクト レビューの指摘。

- 中（要判断）: session を確定の後に消さない決定（決定 2）の副作用。SessionRevalidationService は status だけを見るので、相手が request しないうちに停止→再開すると旧 session が生き残り SESSION_REVOKED も残らない。確認の画面の再開の影響「相手は次のログインから使えます」はこの場合に不正確。緊急停止で問題になる（30 分の無操作の失効が窓を抑える）
- 中: 2 つのサービスの judge の前置き（操作者 → 存在 → 管理範囲）が写し。W6 の 3 つ目（登録・TOTP の解除）の前に、34c の確認ポイント 8 と一緒に抜き出すことを勧めた
- 低: Rejected(NO_CHANGE) の再表示が要求の古い版を持つので、次の操作が必ず Stale になる
- 低: AuditRecord.userSuspended が変更前後を ACTIVE/SUSPENDED に固定（before.status() を渡さない）。newVersion = version + 1 は SQL の知識の写し（役割と同じ）
- 低: controller は 579 行・4 サービス。分ける切れ目は詳細の描画（showDetail/DetailForm）を部品にすること。3 つ目の操作で分ける。architecture_backend:296 の「2 つのサービスを呼ぶ」が古い
- 良: 版の共有で役割の並行変更が規則の判定（監督の役割）の TOCTOU を塞ぐ。監督の役割の停止拒否で「最後のシステム管理者」の問題は利用状態の側では起きない

**Why:** 34c（判定を通さずに呼べる形の直し・二重定義）と W6（登録・TOTP の解除）で同じ controller とサービスに触るため。
**How to apply:** 34c・W6 のレビューで、前置きの抜き出しと controller の分割、session の決定の見直しの有無を確かめる。[[project-bolt34-review]]
