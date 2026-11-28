---
name: project-bolt6-8-review-tests
description: Bolt 6〜8（荷主の再提出・必要書類の添付・共通レイアウト）のテストレビューで見つけた未解消点（2026-10-03）
metadata:
  type: project
---

Bolt 6〜8 のテストレビュー（2026-10-03）で見つけた、次の Bolt で確かめるべき点。

- UploadLimitAdvice（multipart 55MB 超で 413）にテストがない
- 出し直しで書類の違反があるとき storage.count() が 0 のままかを確かめていない（提出の側だけある）
- SHA-256 は長さ 64 しか見ておらず、値の誤りを検出できない
- DB の CHECK 制約は 10485761 の拒否だけで、10485760 の受理（境界ちょうど）がない
- UI テストの書類は java.io.tmpdir/cargo-tracker/documents に溜まり続ける（片付けなし）
- 書類取得は現在の版だけ（documentOf）。前の版の URL が 404 になることのテストがない
- 荷主一覧の同時刻の並び（request_number DESC）は SQL の統合テストで確かめていない
- コミットはテストと実装が同じコミット。受入シナリオ先行の順序は見えるが Red の痕跡は残らない

**Why:** 境界値（直前・同時刻・直後）と否定テストの抜けは前回 [[project-bolt5-review-tests]] でも指摘しており、同じ型の抜けが続いている。
**How to apply:** 次のレビューでこれらが解消されたかを最初に確かめる。
