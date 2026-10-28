---
type: Plan
title: "Bolt 38f2 終了報告 - 予約の側の担当範囲の絞り込み"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-28T23:42:01Z }
---

# Bolt 38f2 終了報告 - 予約の側の担当範囲の絞り込み

## 承認の議題（T-36。先に確かめてほしいこと）

営業担当者の社内の予約の 4 つの入口（一覧・new・確定・詳細）を、担当企業で絞りました。範囲の外は 404 にし、確定の POST のときだけ「担当範囲の外の試行」を記録します。動画と #14 への添付は 38f2b です。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **認可（範囲の判定の順）**: new GET は見積りの照会を予約済みの判定より先に流し、範囲の外なら 404（記録しない）。確定 POST は controller が先にロックしない照会で範囲を確かめ、範囲の外なら記録して 404（決着済み・確定条件・追跡番号を示さない）。範囲の判定は見積りの期限の判定と `lockForBooking` より前。コマンドの `OutOfScope` は、割り当てが途中で変わったときの守り | `BookingQueryService`、`BookingController`、`BookableQuotationQueryService`、`BookingAssignedScopeSecurityIntegrationTest` |
| 2 | **公開 API（ADR-016 の改訂）**: 見積りの公開 API の要求 `BookableQuotationRequest` に操作者を載せ、結果に `OutOfScope`（正規の業務番号と見積り番号だけを持つ）を足した。ADR-016 に「公開 API に主体を載せるのは、呼び手の担当範囲で絞るときだけ」と書いた | ADR-016、`BookableQuotationRequest`・`BookableQuotationResult` |
| 3 | **記録**: 記録の ID は見積りの公開 API が返す正規の業務番号 `TR-…/n`（生の入力を ID にしない。開発レビューで直した）。種類は `QUOTATION`。`BookingOutOfScopeResponder` を booking に置き、ArchUnit の 4 規則を 2 つの部品に広げ、実在の見張りは部品ごとに置いた | `BookingOutOfScopeResponder`、`LayerArchitectureTest` |
| 4 | **画面の文言（開発レビューで変えた）**: 担当企業がない営業担当者の予約一覧は、案内「担当企業がまだないので、表示できる予約はありません。システム管理者に担当企業の割り当てを頼んでください。」だけを出し、今の空の文（「確定した予約はまだありません。…」）を出さない。予約があるのに「まだありません」と示さないため | キャプチャ `07-booking-list-no-assignment.png`、ui_design S-10 |

計画は 2026-10-28 に承認を受けた（`cf61323a9`。約 6 時間を分割の規則の例外として受け入れる）。計画から変えたのは、上の議題 3・4 と次の点です。

- **確定 POST の先の確かめの口**: `BookingQueryService.outOfScopeNumber`（`Optional<String>`。範囲の外なら正規の業務番号）。営業担当者でないときは範囲を見ず空を返すので、役割の判定が範囲より先になる。
- **ACL の `default`**: 理由が `String` の switch なので網羅にできない。`NOT_APPROVED` を明示の case にし、`default` は「見積りが理由を足して予約が知らない場合」（Bolt 37e3 の行 13）の守りとして残した。範囲の外は別の record にしたので、`default` に化けない。
- **届かない分岐**: `confirmation` を 1 つの switch にし、ACL の結果に `Refusal`（`Unavailable` と `OutOfScope`）を足して `refusedByQuotation` の引数を狭めた（開発レビュー）。
- **一覧の空の守り**: 照会のサービスに加え、リポジトリの頭でも空の集合なら問い合わせない（開発レビュー）。
- **文書**: ui_design に S-10 の salt を新しく足した（もともとなかった）。用語集の「担当企業」と付録 B の Q3-6 を新しく足した。

### Red の確かめ（T-217）

| テスト | 落ちた理由（中身を作る前） |
| :--- | :--- |
| セキュリティの統合テスト（9 件） | 一覧に範囲の外の予約が出た、確定の POST が 302、new と詳細の GET が 200、案内が出ない |
| UI の 320 px のシナリオ | 予約の詳細が出た（期待は「画面が見つかりません」）。1 本目はステップ名の重なりで想定外の理由で落ち、名前を直した後の Red は流し直していない（後の `uiTest` で緑） |
| ステップ 3a・3b・4 | 操作者の引数・`OutOfScope`・新しい口・responder がなく、コンパイルの誤り（約 200 件。シグネチャが連鎖するので 1 回にまとめた） |
| 開発レビューで足したテスト | M1 は `outOfScopeNumber` がなくコンパイルの誤り、M2 は空の文が出た。L4 の守りは Red を見ずに書いたので変異 R3 で補った |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38f2 回（W6 の 32 本目） |
| 期間 | 2026-10-28〜29（計画の承認の記録 `cf61323a9` から、終了報告の承認の依頼まで） |
| 対象 | `BookableQuotationRequest`・`BookableQuotationResult`・`BookableQuotationQueryService`、ACL `QuotationBookability`、`BookingQueryService`・`BookingCommandService`・`BookingController`・`BookingOutOfScopeResponder`、`BookingRepository` と mapper、予約一覧のテンプレート、ArchUnit、テスト、受入シナリオ、マニュアル 7 章・付録 B・用語集、ADR-016・ADR-019 ほかの文書 |
| GitHub | [#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14)（AC4 の予約の側。クローズは 38g） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 38f2 計画](bolt_38f2_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | ADR-016・ADR-019、architecture_backend、domain_model、data_model、non_functional（PERF・SEC-12）、test_strategy、ui_design（S-09・S-10・S-24）、release_plan と development_strategy の文 | `2d0dccd2f` |
| 2. 受入の Red | セキュリティの統合テスト、`restrict_bookings_by_assigned_companies_ui.feature` | `2d0dccd2f` |
| 3a・3b. 公開 API と new・確定 | 操作者と `OutOfScope`、ACL、controller の先の確かめ、responder、ArchUnit | `2d0dccd2f` |
| 4. 一覧と詳細 | `findRecentSummaries(Set, int)`（全件の口を消した）、詳細の範囲 | `2d0dccd2f` |
| 5〜6. 既存のテストとマニュアル | 担当企業のない主体で確定していた 5 本ほか、7 章の注・付録 B・用語集、キャプチャ | `2d0dccd2f` |
| 品質ゲートと開発レビュー | 変異 15 本と 4 本、レビューの指摘の反映 | `2d0dccd2f` |
| 終了報告と配備 | この報告、CI と配備 | この報告のコミット |

打ち切り（ステップ 3b の後で 230 分）には当たらなかった。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` 3492 件、失敗 0（前の 3461 件から 31 件増えた）。`uiTest` 340 件、失敗 0（前の 336 件から 4 件増えた）。`manualScreenshots` 成功 |
| SpotBugs | 0 件（直しの途中で使わない局所変数の指摘が出たので書き換えた。除外は足していない） |
| 変異（計画） | 15 本すべてが落ちた（`test` だけで流した。T-220）。公開 API が範囲を見ない、範囲の判定を期限とロックの後にする、ACL が範囲外を「使えない」に写す、確定で記録しない、GET でも記録する、一覧の `IN` と空の守りを外す、詳細で担当企業を見ない、予約済みを範囲より先に見る、ArchUnit の部品とトランザクションの規則、部品がポートを呼ばない |
| 変異（レビューの直し） | 生の入力で記録する、空の文を出す、リポジトリの空の守りを外す、記録の種類を誤る。4 本とも落ちた |
| SonarQube | PASS（網羅率 95.6%、重複 0.1%、違反 0。1 回目で通った） |
| `okf:check` | ERROR 0 |
| CI | `2d0dccd2f` で `check`・`ui`・`deploy-demo` が成功した（デモ環境は v496 で動いている） |

### 開発レビューの指摘と扱い

| 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- |
| 中 | 確定 POST の先の確かめが生の入力を記録の ID にしている | 正規の業務番号を返す口に変えた（議題 3） |
| 中 | 担当企業がない予約一覧に 2 つの文が出る | 案内だけにした（議題 4） |
| 低 | ACL の `default` | この報告に記録した（網羅にできない理由） |
| 低 | 届かない分岐、リポジトリの空の守り、記録の種類での絞り込みと担当企業のない主体の new・確定、UUID の直書き、宣言の順と Javadoc | 直した |
| 低 | 役割の判定が 2 か所に写されている | 38g の開始準備で、記録の部品の共通化とあわせて諮る |

## デモ項目

- マニュアル: 7 章（7.1〜7.4 の注、担当企業がないときの予約一覧）、付録 B Q3-6、用語集「担当企業」
- 動画は 38f2b で撮る

## 判断と学び

- 範囲の判定は「存在を漏らす判定」（予約済み・期限切れ・決着済み）より先に置く。順序を図に書き、範囲の外 × それぞれの判定の行を状態の表に置くと、整合性検証で漏れを見つけられる
- 記録の ID は、検証済みの正規の値から作る。受け手の厳しさに黙って頼ると、相手の変更で ID が化ける
- 空の文と案内を同じ画面に出すときは、どちらかが事実と違わないかを確かめる

### ふりかえり（KPT）

- Keep: 2 つの整合性検証で、予約済みの判定から追跡番号が漏れる穴を計画の段階で見つけた。変異 19 本がすべて落ち、SonarQube も 1 回で通った。T-220（変異は `test` だけで流す）を守り、止まらずに済んだ
- Problem:
  - OKF の `apply docs --changed` が、別の担当が書いていた計画の文書を巻き込んだ（38d2 でも起きた）
  - 実装の担当が T-204（heredoc）をまた 1 回違反した
- Try:
  - T-222: 計画の初稿を書く担当と実装の担当を並べるときは、初稿を先にコミットしてから実装に入る（未追跡の文書を `--changed` が巻き込まないように）

### 既知の課題（後に持ち込む）

- 役割の判定の写し（38g で諮る）
- 記録の部品の写しは 2 つ。38g で 4 つになるので名前の規則にする（38g の計画）
- ui_design :181（S-20 の行）に 38e の前の語が残っている（38g のステップ 1 で直す）
- 支援技術による手動確認は W10 に送る（38g で #14 を閉じるときの決定）

## 次の Bolt

Bolt 38f2b（担当範囲の動画と #14 への添付）と Bolt 38g（経路設計と追跡の担当範囲の絞り込みと、US-16 の締め。計画は承認待ち）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-29 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38f2 計画](bolt_38f2_plan.md)、[リリース計画](release_plan.md)（W6 節の 38f2・38f2b の行）
- [Bolt 38f 終了報告](bolt_38f_report.md)、[ADR-016](../../adr/cargo-tracker/016-booking-checks-quotation-at-commit-time.md)、[ADR-019](../../adr/cargo-tracker/019-out-of-scope-attempt-recorder.md)
- [ユーザーマニュアル 7 章](../../manual/07-本予約の確定と予約一覧.md)
