---
name: bolt3-e2e-review
description: cargo-tracker Bolt 3（@ui の Playwright・axe、用語集の整合テスト、追記専用の権限テスト、CI の ui ジョブ）をテスター視点でレビューした時点（2026-10-02）の未解消の空振り・抜け
metadata:
  type: project
---

2026-10-02 に Bolt 3（170eaf1..f09659d）をレビューした時点の論点。

- axe の違反の検証は「表示したすべての画面にアクセシビリティの違反がない」ステップを書いたシナリオだけ。320px のシナリオは検査してためるだけで検証しない。@After で全 @ui シナリオに効かせる案を出した
- axe はタグ未指定（WCAG 2.2 AA を明示していない）、incomplete を捨てている
- キー操作の提出は完了画面で出発地・目的地を確かめず、Tab 順の入れ違いを見逃す
- 用語集の整合テストは @AggregateRoot・@ValueObject だけが対象（エンティティ TransportRequestVersion と enum は対象外）、対象 0 件でも通る
- 追記専用の権限テストは transport_request_version の 1 表だけで、表の一覧との照合が無い。アプリと他のテストは superuser の test 利用者で動き、GRANT の漏れ（event_publication の UPDATE など）を検出できない。callback は role が無いと黙って何もしない
- uiTest に業務ルール層のスイートと ArchUnit が混ざる（報告書の既知の課題）。CI の ui ジョブは失敗時の trace・スクリーンショットを残さない

**Why:** 画面と権限のテストはこれから 15 フロー・複数の追記専用の表に広がるため、今の型がそのまま複製される。
**How to apply:** 次の Bolt のレビューでまずこれらの解消を確認し、解消済みなら消す。関連: [[bolt2-quality-gate-review]]
