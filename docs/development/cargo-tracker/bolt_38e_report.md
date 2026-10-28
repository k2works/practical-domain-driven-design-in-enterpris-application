---
type: Plan
title: "Bolt 38e 終了報告 - 業務担当者の担当範囲（担当企業）の割り当て"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-28T04:26:05Z }
---

# Bolt 38e 終了報告 - 業務担当者の担当範囲（担当企業）の割り当て

## 承認の議題（T-36。先に確かめてほしいこと）

システム管理者が S-20 の詳細の「担当範囲」の領域で、業務担当者（営業担当者・経路設計者・追跡管理者・カスタマーサポート）に担当企業を割り当て、解除できるようにしました。選べるのは自分の管理範囲の中の有効な荷主の企業だけで、確定は確認の画面を経て、変更前・変更後の 1 行の監査 `ASSIGNMENTS_CHANGED` を書きます。業務の画面を担当企業で絞るのは 38f・38f2・38g です（画面に「業務の画面で効くのは、今後の更新からです」と示しています）。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **認可**: 同じ `user_assignment` の `COMPANY` の行を、業務の 4 つの役割のときは担当企業、システム管理者のときは管理範囲として読み分ける（スキーマは変えない。決定 (9)）。役割の一覧は `RoleAssignmentPolicy.assignmentScopedRoles()` の 1 か所から来る。監督の役割と業務の役割を付け替えるときに先に行を消す手順は、W10 の運用の宿題にした（`operation.md`） | `IdentityMapper.xml`、`RoleAssignmentPolicy`、契約テスト |
| 2 | **同時の変更と差の書き込み**: 確定は利用者の版の条件付きの更新を先に流し（役割の変更と同じ版）、範囲の中の差だけを書く。管理範囲の外の割り当てと BOOKING の行は残す。確定の POST は範囲の判定（記録）→ 値の解釈 → サービスの再判定の順 | `MyBatisUserAssignmentRepository`、`UserAssignmentController`、`UserAssignmentIntegrationTest` |
| 3 | **画面の文言（開発レビューで変えた。2026-10-28、human:kakimomokuri）**: <br>・変えられない企業は「この画面で変えられない企業 {N} 社も担当しています。」（計画の「管理範囲の外の企業 N 社」は、管理範囲の中で無効になった荷主も数えて事実と違ったため）。マニュアル 11.8 に依頼先（管理範囲の外はその企業を管理するシステム管理者、無効になった企業は運用の窓口）を書いた<br>・担当範囲のボタンは「担当範囲の変更内容を確認する」（役割の領域と同じ名前のボタンが 2 つあり、読み上げで区別できなかったため）<br>・選べる荷主が 0 社なら「あなたの管理範囲には、担当企業にできる荷主の企業がありません。」とだけ示し、選ぶ欄もボタンも出さない | キャプチャ `11-user-detail.png`・`11-assignment-confirmation.png` ほか |
| 4 | **開発データ（データベース）**: dev だけのマイグレーション `db/dev-data/V20261028100900__seed_dev_business_user_assignments.sql` で、業務担当者 4 人に荷主 A・B を割り当てた（8 行、`granted_by` は空）。読むのは `application-dev.properties` だけ | マイグレーション |
| 5 | **テストのヒープ（設定。2026-10-28、human:kakimomokuri が決定）**: `check` の `test` が既定のヒープ（512 MB）で Java heap space で止まるようになったので、`build.gradle` の `tasks.named('test')` に `maxHeapSize = '1g'` を足した。テストの Spring の文脈がキャッシュに積み上がったためとみている。文脈の数を減らす直しは既知の課題に送る | `build.gradle` |
| 6 | **計画から変えたこと（承認を受けたい）**: <br>・#50 (a): 計画は「`UiUsers` も明示の割り当てに置き換える」だったが、`UiUsers` は作る順によらずシナリオの企業を割り当てていて、#50 (a) の本題（作る順への依存）はもうなかった。変えるとすべての UI シナリオに響くので、`UserAdministrationSecurityIntegrationTest` だけを明示の補助 `admin()` にし、`UiUsers` は写しを `addScenarioCompany` にまとめるだけにした<br>・#50 (b): 一覧系の照会は、システム管理者でなければ値（`AdministrationListing.NotPermitted`）で返し、画面で 403 にする。詳細系と同じ値の形にそろえ、application 層から Spring Security の例外をなくした（開発レビュー）<br>・#50 (c)（範囲外のときだけ応答時間が違う）は受け入れとして記録する | `UserAdministrationQueryService`、`UiUsers` |

計画は 2026-10-28 に承認を受けた（`b3e183237`）。計画から変えたのは、上の議題 3・5・6 と次の点です。

- **`UserDetail`**: 11 引数のコンストラクタを残し、呼び出し元 16 か所は変えていない（作り直しには使わないと Javadoc に書いた）。
- **選べる企業の口**: `AssignableCompanyQuery` の 1 つにし、照会とサービスの両方が呼ぶ（開発レビューで、2 か所の写しを寄せた）。変えられない企業の数は `AssignedCompanies.outsideCountOf` で数え、文言の組み立ては `UserDetailPage` の 1 か所にした。
- **すべて解除して戻る**: 確認の画面の「戻る」で、選んだ企業が空でも印を外したまま詳細に戻るよう、`companiesChosen=true` を渡す（開発レビュー）。
- **確認の画面の要約**: 共通の部品 `ConfirmationSummary` に寄せた（ステップ 3b）。新しい担当範囲の確認の画面は入力欄を持たず拒否を詳細のエラー要約に返すので、使っていない。
- **置き換えの前の読み出し**: 管理範囲の SQL を使わず、意味を持たない名前の `selectCompanyRows` で読む（開発レビュー）。

### Red の確かめ（T-217）

| テスト | 落ちた理由（中身を作る前） |
| :--- | :--- |
| 受入シナリオ（行 16） | 新しい 4 シナリオが「担当範囲」の領域を見つけられない。監査担当者に領域が出ないシナリオは最初から緑（守り） |
| 値・リポジトリ・サービス・画面 | 型と口がないコンパイルの誤り |
| #50 (a)・(b) | `DuplicateKeyException` 20 件、例外が投げられない 3 件 |
| 開発レビューで足したテスト | 0 社の案内がない、戻ると印が残る、文言とボタン名が古い（6 件）。V4・V13 のテストは守りのテストで、変異で落ちることを確かめた |
| `UserAssignmentSecurityIntegrationTest` | 本体の後に書いたので Red を見ていない。変異 V1・V5・V7・V14 で落ちることを確かめて補った |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38e 回（W6 の 30 本目） |
| 期間 | 2026-10-28（計画の承認の記録 `b3e183237` から、終了報告の承認の依頼まで） |
| 対象 | `AssignedCompanies`・`AssignmentChangeRejection`・`AssignmentChangeCheck`・`AssignmentChangeOutcome`・`AdministrationListing`・`AssignableCompanyQuery`、`RoleAssignmentPolicy`、`AuditAction`・`AuditRecord`、`Company.acceptsAssignment`、`UserAssignmentRepository` と MyBatis、`UserAssignmentService`、照会のサービス、`UserAssignmentController`・`UserDetailPage`・`ConfirmationSummary`、テンプレート、開発データ、テスト、受入シナリオ、マニュアル 11.8 とキャプチャ、設計の文書、`build.gradle` |
| GitHub | [#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14)（クローズは 38g）、[#50](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/50)（3 項目） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 38e 計画](bolt_38e_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | ui_design、domain_model、data_model、non_functional、user_story・system_usecase（担当案件は担当企業で近似する要件の変更）、architecture_backend、operation、test_strategy、development_strategy・release_plan の動画の送り先 | `cb06d1993` |
| 2〜4. Red と Green | 値・規則・リポジトリ・サービス・画面・受入シナリオ 5 本 | `cb06d1993` |
| 3b. 共通の部品 | `ConfirmationSummary`（緑のままのリファクタリング） | `cb06d1993` |
| 5. 開発データと #50 | 上の議題 4・6 | `cb06d1993` |
| マニュアル | 11.8（担当範囲の変更）、キャプチャ 4 枚（T-197） | `cb06d1993` |
| 品質ゲートと開発レビュー | 変異 13 本と 8 本、レビューの指摘の反映、テストのヒープ | `cb06d1993` |
| 終了報告と配備 | この報告、CI と配備 | この報告のコミット |

打ち切りには当たらなかった。

### US-16 の受入条件の照合（途中の経過。閉じるのは 38g）

| 受入条件 | 確かめるもの | Bolt | 状態 |
| :--- | :--- | :--- | :--- |
| AC1（固定役割の付与と関係・変更履歴） | 担当企業の割り当てと `ASSIGNMENTS_CHANGED`（担当案件は担当企業で近似する。決定 (10)） | 34・38b〜38e | 済み |
| AC2（管理範囲の外の拒否と試行の記録） | 担当範囲の変更の範囲外の 404 と `ADMIN_SCOPE_DENIED` | 34b・38b2・38b3b・38e | 済み |
| AC3（職務分離） | — | 34・38b | 済み |
| AC4（業務担当者の担当範囲の外の拒否と試行の記録） | 見積り・予約・経路設計・追跡の絞り込み | 38f・38f2・38g | 未 |
| AC5（利用終了・取消しで次の request から拒否） | 役割の取消しのシナリオ | 34d・38g | 役割の取消しは 38g |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` 3398 件、失敗 0（前の 3305 件から 93 件増えた。テストのヒープを 1 GB にした後。`test` の XML で数えた）。`uiTest` 332 件、失敗 0（前の 322 件から 10 件増えた）。`manualScreenshots` 26 件、失敗 0 |
| SpotBugs | 0 件 |
| 変異（計画の 13 個） | 11 個が 1 回目で落ちた。生き残った V4（サービスの版の確かめ）と V13（DELETE の `scope_kind` の条件）は、テストを足して落ちることを確かめた（V4 は対象に `UserAssignmentServiceTest` を入れていなかった） |
| 変異（レビューの直し） | 8 本すべてが落ちた（V4・V13、選べる企業の口の絞り込み、0 社の案内、変えられない企業の数、戻るの印の読み手と付け手、一覧の権限） |
| SonarQube | PASS（網羅率 95.6%、重複 0.1%、違反 0。1 回目は真偽値の任意の引数（既定値を与えた）と `containsEntry`（3 件）で落ちたので直した） |
| `okf:check` | ERROR 0 |
| CI | `cb06d1993` で `check`・`ui`・`deploy-demo` が成功した（デモ環境は v486 で動いている） |

### 開発レビューの指摘と扱い

| 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- |
| 中 | 「管理範囲の外の企業 N 社」が事実と違う | 文言を変えた（議題 3） |
| 中 | application 層に Spring Security の例外 | 値で返す形にそろえた（議題 6） |
| 中 | 選べる企業と変えられない企業の数の写し | 1 つの口に寄せた |
| 中 | #50 (a) の計画からの逸脱 | 議題 6 で承認を受ける |
| 中 | 同じ名前のボタンが 2 つ | ボタンの名前を変えた（議題 3） |
| 中 | すべて解除して戻ると選んだ状態が戻らない | 直した |
| 低 | 0 社のときの画面、置き換えの前の読み出しの名前、`before` を読む時点の注、ui_design の古い記述、POST の 404 の確かめ、完全修飾名 | 直した |
| 低 | 業務の役割どうしの付け替えで行が眠る | 既知の課題に記録した（W7・38f の論点） |
| 低 | 3b のリファクタリングを先にコミットしていない | 機能と同じコミットにした（差分は寄せた分だけ） |
| 低 | `UserDetail` の 11 引数のコンストラクタ | 受け入れた（W7 以降で射影の工場にする案） |

## デモ項目

- マニュアル: 11.8（担当範囲の変更）、11 章の詳細と停止中の図
- 動画は撮っていない（38f2 で拒否が見える場面と一緒に撮る）

## 判断と学び

- 同じ行に 2 つの意味を持たせるときは、読み出しを役割で分け、役割の一覧を 1 か所に置く。付け替えの手順は運用に明記する
- 画面に並べる選択肢と確定で受け付ける範囲は、同じ口から作る。別々に作ると、並べたのに拒む・拒むはずを受け付ける形にずれる
- 「空」を選べる画面では、「戻る」で空が伝わるかを確かめる。役割には空がなかったので、前例のテストでは表に出なかった

### ふりかえり（KPT）

- Keep: 整合性検証で、受け渡しとポートの先回り、行の二重の意味、版の口を計画の段階で直せた。セキュリティの統合テストの Red を見なかった分を変異で補えた
- Problem:
  - 変異の対象のテストの選び方で、V4 が一度生き残った（サービスの単体を入れていなかった）
  - テストの Spring の文脈が増え、既定のヒープで `check` が止まった
  - 親（私）が標準入力を待つコマンドを 1 回流した（T-204 の違反。ファイルは変わっていない）
- Try:
  - T-219: 変異の対象には、変異するクラスの単体テストと、それを使う統合テスト・契約テストの両方を入れる（T-214 を単体の側にも広げる）

### 既知の課題（後に持ち込む）

- テストの Spring の文脈の数（ヒープを 1 GB にした。文脈を減らす直しは #50 に送る）
- 業務の役割どうしの付け替えで行が眠る（W7・38f の論点）
- #50 (c) 範囲外のときだけ応答時間が違う（受け入れた）
- 「業務の画面で効くのは、今後の更新からです」の文言は 38g で外す
- 確認の画面の要約の共通の部品、照会の口の分離（38c・38c2 から）

## 次の Bolt

Bolt 38f（担当企業の受け渡しと範囲外の試行の記録、見積りの側の担当範囲の絞り込み。計画は承認済み）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-28 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38e 計画](bolt_38e_plan.md)、[リリース計画](release_plan.md)（W6 節の 38e の行）
- [Bolt 38f 計画](bolt_38f_plan.md)、[Bolt 34 計画](bolt_34_plan.md)（役割の変更の前例）
- [UI 設計](../../design/cargo-tracker/ui_design.md)（S-20）、[データモデル](../../design/cargo-tracker/data_model.md)（`user_assignment`）、[ユーザーマニュアル 11 章](../../manual/11-利用者の管理.md)
