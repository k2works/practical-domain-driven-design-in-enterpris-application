---
type: Plan
title: "Bolt 38f 終了報告 - 担当企業の受け渡しと範囲外の試行の記録、見積りの側の担当範囲の絞り込み"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-28T10:41:03Z }
---

# Bolt 38f 終了報告 - 担当企業の受け渡しと範囲外の試行の記録、見積りの側の担当範囲の絞り込み

## 承認の議題（T-36。先に確かめてほしいこと）

営業担当者の社内の見積りの側（輸送要求の審査と社内の見積りの 10 の入口）を、担当企業で絞るようにしました。担当企業はログイン中の主体（`AuthenticatedActor`）に載せ、範囲の外は 404 にして、確定の POST のときだけ「担当範囲の外の試行」を監査に記録します。予約の側は 38f2、経路設計と追跡は 38g で絞ります。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **認可**: 担当企業は `CargoUserDetails` の `transient` の項目で持ち、request ごとの確かめで載せ直す。復元の直後と公開の URL では null で、`actor()` が空として渡す。`handlesCompany` は空なら偽で、失敗に倒す（担当企業がない営業担当者は何も見えない）。載せるのは対象の役割のときだけ（38e の決定 (9)） | `CargoUserDetails`、`AuthenticatedActor`、`SessionRevalidationService`、`OldSessionPrincipalTest` |
| 2 | **範囲外の扱い**: 範囲の判定はサービスが行い `OutOfScope` を返す。controller では `StaffOutOfScopeResponder` だけが記録して 404 にする。GET の 6 入口は 404 だけ、確定の POST の 4 入口は記録して 404（SEC-12）。範囲の判定は値の解釈より先（経路の番号が数でないときは範囲によらず 400 で記録しない）。記録は `ASSIGNED_SCOPE_DENIED`・`FAILURE`・`OUT_OF_SCOPE`、外側のトランザクションの中では拒み自分のトランザクションで書く | `StaffAssignedScopeSecurityIntegrationTest`、`AssignedScopeDenialService` |
| 3 | **構造（共有カーネルの例外）**: ポート `OutOfScopeAttemptRecorder` を `shared.domain` に置いた。共有カーネルに置く振る舞いの型はこの 1 つだけ（ADR-019）。architecture_backend・ADR-001・ADR-014 に改訂の経緯を書いた。ArchUnit で、ポートを呼ぶ部品、記録を作るサービス、イベントと公開 API の型が依存しないこと、共有カーネルのインターフェースはこれだけ、を守る | ADR-019、`LayerArchitectureTest` ほか |
| 4 | **決めてほしいこと: 404 の画面のヘッダー**。計画の行 16b と AC-6 は「範囲外の URL の 404 の画面で、ヘッダーに表示名が出る」としていた。しかし共通の 404（`error/404.html`）は、業務データを出さないために認証の前のレイアウトを使う（Bolt 34b、IA-INV-14）。実装では本体を変えず、「`/error` のディスパッチでも担当企業の null で 500 にならず、共通の 404 が出る」ことで確かめた。<br>推奨: 今の設計に合わせ、計画の行 16b と AC-6 を「404 の画面が 500 にならず、共通の文言を出す（表示名は出さない）」と読み替える | `error/404.html`、`StaffAssignedScopeUiSteps` |
| 5 | **開発データ（データベース）**: dev だけのマイグレーション `V20261028110900` で、荷主 A だけを担当する営業担当者（末尾 0306）を足した。デモの画面で絞り込みが見える | マイグレーション、`DevSampleDataSmokeTest` |

計画は 2026-10-28 に承認を受けた（約 8.5 時間を分割の規則の例外として受け入れる）。計画から変えたのは、上の議題 4 と次の点です。

- **一覧の契約テスト**: `StaffListingQueriesContract`（H2 と PostgreSQL の子）は作らなかった。見積りの側の永続化には H2 の準備がないため。5 つの一覧の `IN` の絞り込みは、既存の PostgreSQL の統合テスト 2 つに、荷主 2 社の場合として足した。
- **新しいファイル**: identity の `interfaces/api/package-info.java` は作らなかった（公開 API の型がなく、`internal` だけ）。`StaffTransportRequestLookup`・`AuthenticatedActorFixture`・`StaffAssignedScopeUiSteps`・`StaffOutOfScopeResponderTest` を足した。名前は確認ポイント 6 のとおり `AssignedScopeDenialService`。
- **背景を置いた feature**: 計画の 15 本の候補に、見積りの側を通っていた 3 本（`kpi_observations_ui`・`query_tracking_ui`・`register_milestone_ui`）を足して 18 本。
- **POST の前の確かめ**: 確定の POST は、controller が照会のサービスで範囲を先に確かめ、範囲の外なら記録して 404 にする（値の解釈の前に止めるため）。コマンドのサービスが返す `OutOfScope` は、その間に割り当てが変わったときに通る。2 つの controller の書き方は `StaffTransportRequestLookup.inScope()` にそろえた（開発レビュー）。
- **SpotBugs の除外**: `SE_TRANSIENT_FIELD_NOT_RESTORED`（担当企業の項目。毎回載せ直す）と、`StaffTransportRequestLookup$Found` の `EI_EXPOSE_REP`・`EI_EXPOSE_REP2`（集約を渡す入れ物）の 2 つを、クラスと項目に限って足した。
- **ステップ 5 と 6**: 打ち切りに当たらなかったので、分けずに 1 回で入れた。

### Red の確かめ（T-217）

| テスト | 落ちた理由・確かめ方 |
| :--- | :--- |
| ステップ 5 の新しいテストと書き換えたテスト | 新しい型と引数がなく、コンパイルの誤り（約 50 件） |
| UI の背景を足す前 | 範囲の外で一覧が空になり、11 件のシナリオとマニュアルの 5 件が時間切れで落ちた |
| Red を見ずに本体を直したもの（`StaffAssignedScopeSecurityIntegrationTest`、ArchUnit の 9 規則、`SessionRevalidationServiceTest` の担当企業、`DevTotpCredentialsDevProfileTest`） | 変異で落ちることを確かめて補った（下の品質ゲート） |
| 開発レビューで足したテスト | 守りのテストで、変異で落ちることを確かめた |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38f 回（W6 の 31 本目） |
| 期間 | 2026-10-28（計画の承認から、終了報告の承認の依頼まで） |
| 対象 | `CargoUserDetails`・`AuthenticatedActor`・`UserStanding` と照会、`OutOfScopeAttemptRecorder`・`ScopedResourceKind`・アダプター・`AssignedScopeDenialService`、`StaffTransportRequestScope`・`StaffTransportRequestLookup`・`StaffOutOfScopeResponder`、照会とコマンドのサービス、2 つの controller、5 つの一覧の SQL、ArchUnit、既存の UI シナリオ 18 本の背景、開発データ、マニュアル 6 章、ADR-019 と設計の文書 |
| GitHub | [#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14)（AC4 の見積りの側。クローズは 38g） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 38f 計画](bolt_38f_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | ADR-019（新規）、ADR-001・011・012・014、architecture_backend、domain_model（用語「担当範囲の外の試行」・IA-INV-08）、data_model、non_functional、ui_design、test_strategy、release_plan の文 | `0da4d3bd7` |
| 2. 受け渡し | transient の項目、1 回の SQL（ON 句の `scope_kind`）、古い session のバイト列の確かめ | `0da4d3bd7` |
| 3. ポート | `shared.domain` のポート、identity のアダプターとサービス | `0da4d3bd7` |
| 4. 既存のシナリオの準備 | `UiUsers` の明示の補助と、18 本の背景の共通の前提 | `0da4d3bd7` |
| 5〜6. 10 の入口 | 照会とコマンドのサービスの範囲の判定、responder、一覧の案内 | `0da4d3bd7` |
| 8. マニュアル | 6 章の注 2 つと、担当企業がないときの一覧のキャプチャ | `0da4d3bd7` |
| 品質ゲートと開発レビュー | 変異 28 本と 4 本、レビューの指摘の反映 | `0da4d3bd7` |
| 終了報告と配備 | この報告、CI と配備 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` 3461 件、失敗 0（前の 3398 件から 63 件増えた）。`uiTest` 336 件、失敗 0（前の 332 件から 4 件増えた）。`manualScreenshots` 26 件、失敗 0 |
| SpotBugs | 0 件（除外を 2 つ足した） |
| 変異（計画と追加） | `test` で 28 本のうち 27 本が落ちた（V1〜V13・V3a〜f、ArchUnit の A1・A3・A5〜A9、開発データの D1）。生き残った D2（荷主 A だけの営業担当者の割り当てを消す）は、`DevSampleDataSmokeTest` にテストを足して落ちることを確かめた。ArchUnit の「ポートを呼ぶ部品」の規則は、部品がほかのメソッドを呼ばないと空のまま通る穴があったので、`StaffOutOfScopeResponderTest` で補った。uiTest では V1 が落ちた。V12 は主体を作れずに止まり、結果が出なかった（`test` では落ちている） |
| 変異（レビューの直し） | D2、部品がポートを呼ばない、範囲の判定を外す（GET の 5 入口と POST の 6 つ）、サービスの範囲外を記録しない。4 つとも落ちた |
| SonarQube | PASS（網羅率 95.6%、重複 0.1%、違反 0。1 回目は使わない引数・record pattern・同じ実装の補助の 6 件で落ちたので直した） |
| `okf:check` | ERROR 0 |
| CI | `0da4d3bd7` で `check`・`ui`・`deploy-demo` が成功した（デモ環境は v490 で動いている） |

### 開発レビューの指摘と扱い

| 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- |
| 中 | GET の 3 入口は範囲の判定を外しても 404 で空振りする | 書類と見積りを持つ輸送要求を用意し、5 入口と範囲の中の対照をパラメーター化した |
| 中 | POST の判定の形が計画の決定 (10) からずれ、2 つの controller で書き方が違う | `inScope()` にそろえ、この報告に記録した。サービスの `OutOfScope` の分岐を controller の単体で確かめた |
| 中 | `findByNumberForStaff` を誰が呼べるかの規則がない | ArchUnit で限った（予約の側は 38f2 で外す印） |
| 中 | ADR-019 の書き漏れ | ADR-003 :58 との関係、`platform :: api` の案、実行時の Bean の依存、package-info を足した |
| 中 | 中身の変わらない 6 章のキャプチャまで差し替わった | 7 枚を戻した |
| 低 | テストの主体の担当企業、監査の操作者の列、範囲より先の 400、見積りの 3 つの SQL の 2 社、割り当てを足した後、`<foreach>` の写し、ADR-011 :307、記録のサービスを呼べるのをアダプターに限る規則、変数名 `record`、feature の「前提」の重なり | 直した |
| 低 | 新しいファイルの一覧とのずれ、契約テストを作らない判断 | この報告に記録した |

開発レビューの外で、範囲外のセキュリティの統合テストが流す順番で結果が変わる不具合を見つけて直した（時計を置かず、TOTP の刻みが 0 になっていた。前例どおり `@BeforeEach` で時刻を置いた）。

## デモ項目

- マニュアル: 6 章（担当企業で絞ることの注と、担当企業がないときの一覧）
- 開発の営業担当者（末尾 0306）は荷主 A だけを担当するので、デモ環境で絞り込みが見える
- 動画は 38f2 で撮る（拒否が見える予約の側と一緒に）

## 判断と学び

- 横断の部品（受け渡し・ポート）は、最初に使う側と同じ Bolt で作ると観測でき、先回りにならない（38e の決定 (1)）
- 共有カーネルの決まりに例外を作るときは、例外を 1 つに限ると ADR に書き、ArchUnit で「これだけ」を守る
- uiTest で変異を流すと、主体が作れない変異では画面ごとに時間切れを待って止まり、元に戻せなくなる。変異は `test` で流し、uiTest は振る舞いの確かめに使う

### ふりかえり（KPT）

- Keep: 変異で Red を見なかった 4 つの確かめを補い、ArchUnit の規則の穴も見つけられた
- Problem:
  - 実装の担当がターンの上限で一度止まった（作業の量が大きい Bolt）
  - 変異の担当が uiTest で止まり、変異がソースに残った。利用者にプロセスを止めてもらった
  - コミットのファイルの一覧で、日本語と空白を含む名前が引用符つきになり、除外と追加がずれた
  - T-204 の違反（標準入力を待つコマンド）がまた出た
- Try:
  - T-220: 変異は uiTest で流さない。uiTest で確かめたい振る舞いは、`test` の統合テストで落とせる形にする
  - T-221: コミットのファイルの一覧は `git -c core.quotepath=false status --porcelain` で作り、除外は引用符の有無によらない条件で書く

### 既知の課題（後に持ち込む）

- 予約の側（`/staff/bookings`・`/staff/bookings/new`）は 38f2 まで絞られない（確認ポイント 8 で受け入れた）
- 「業務の画面で効くのは、今後の更新からです」の文言は 38g で外す
- テストの Spring の文脈の数、業務の役割どうしの付け替え（#50）
- カスタマーサポートに社内の見積り・予約の画面を開くかは、別に諮る

## 次の Bolt

Bolt 38f2（予約の側の担当範囲の絞り込みと動画。開始準備は `opening-iteration` で）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-28 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38f 計画](bolt_38f_plan.md)、[リリース計画](release_plan.md)（W6 節の 38f・38f2・38g の行）
- [Bolt 38e 終了報告](bolt_38e_report.md)、[ADR-019](../../adr/cargo-tracker/019-out-of-scope-attempt-recorder.md)、[ADR-012](../../adr/cargo-tracker/012-authentication-principal-and-session.md)
- [ユーザーマニュアル 6 章](../../manual/06-受付一覧と審査と見積り.md)
