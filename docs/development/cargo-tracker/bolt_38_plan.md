---
type: Plan
title: "Bolt 38 計画 - S-20 の前の直し（エラー要約の形とフォーカス、役割の確定の版の解釈の順、Section の足し方）"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-24T23:28:09Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-24T23:28:10Z }
---

# Bolt 38 計画 - S-20 の前の直し（エラー要約の形とフォーカス、役割の確定の版の解釈の順、Section の足し方）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38 回（W6 の 18 本目） |
| 予定 | W6（2026-11-09 の週。前倒しで着手できる）。作業は約 3.5 時間（`check`・`uiTest`・`manualScreenshots`・SonarQube・CI の待ちを除く） |
| アプローチ | アウトサイドイン（画面の構造。S-20 の詳細を描く MockMvc と画面の層を外側の Red にする。開発戦略 :346 の「S-20 の直し」） |
| 対象 | Bolt 34f の開発レビューの指摘 8・9 と #50 の「`Section` とテンプレート」。(1) S-20 の詳細のエラー要約のリンクを共通部品の「表示名: 文言」の形にし、リンク先を群の最初の選択肢にする、(2) 要約へフォーカスを移す、(3) 誤りのある群に `aria-invalid="true"` を付ける、(4) 欄のない利用者への拒否では項目名を付けず欄へリンクしない、(5) 停止の理由の誤りの文言を「利用終了・異動・緊急停止のどれかを選んでください。」にする、(6) 役割の確定の POST の版を範囲の判定の後に解釈する、(7) 詳細に領域と操作を足す手順を書く |
| トレース | 横断受入条件（BR-13、WCAG 2.2 AA。エラー要約と該当項目）: 行 1〜4・6〜8。US-16 AC2（範囲外の試行の記録）: 行 5 |
| GitHub | [#50](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/50)（「`Section` とテンプレート」の項目を閉じる） |
| SP | 0 |
| 承認ゲート | 計画と終了報告。画面の構造（エラー要約）と見える文言（停止の理由の誤り）、役割の確定の POST の応答の順（範囲外の試行の記録に関わる）。スキーマ・環境変数・開発データ・CI は変えない。新しいファイルは作らない（この計画と終了報告を除く） |
| 開始準備で決めたこと | 2026-10-25、human:kakimomokuri: (1) エラー要約のリンク先は群の最初の選択肢（`#role-<最初の役割>`・`#reason-<最初の理由>`）、(2) 要約へのフォーカスは既存の `transport-request-form.js` を S-20 の詳細でも読む（新しいファイルを作らない）、(3) `Section` と `Operation` の形は残し、領域と操作の足し方を書く（38c の「ログインの資格」が最初の利用）、(4) 停止の理由の誤りの文言は選択肢を示す文「利用終了・異動・緊急停止のどれかを選んでください。」にする（共通部品の規則「文言は表示名で始めない」。ui_design :922）、(5) `Section` の 1 つの値は 1 つの操作（見出しと、選び方の誤りの欄の項目名）を表す。選び方の誤りのない操作は項目名を持たずリンクしない。1 つの領域に操作が 2 つあるときは値を 2 つにし、テンプレートの 1 つの領域に対応させる（38c） |

## Bolt ゴール

38b〜38e で S-20 の詳細に領域と操作を足す前に、今の 2 つの領域（役割・利用状態）のエラー要約を共通部品と同じ形にし、確定の POST の解釈の順をそろえ、領域と操作の足し方を決めておく。

### 開始準備で確かめたこと

- **エラー要約の今の形**（`templates/identity/users/detail.html:17-23`）: リンクの文は文言だけ（「役割を 1 つ以上選んでください。」）。共通部品は「表示名: 文言」で、文言は表示名で始めない（ui_design :922。前例は C-03 `transport-requests/new.html:24-25`、S-13 `milestones/new.html:23-24`）。リンク先は `UserDetailPage.Section` の固定の `#roles`・`#suspension-reason`（`UserDetailPage.java:203・:205`）で、どちらも `fieldset`（`detail.html:44・:71`）。`fieldset` はフォーカスを受けないので、ブラウザの素のフラグメントの移動でもフォーカスが移らない
- **群の最初の選択肢**: 役割は `role-<Role の名前>`（`detail.html:52`）で、並びは `Role` の宣言の順で企業の種類に合うもの（`UserDetailPage.java:164-169`。運用する会社は `role-SALES`、荷主の企業は `role-SHIPPER`）。停止の理由は `reason-<理由の名前>`（`detail.html:78`）で、最初は `reason-USAGE_ENDED`、最後は `reason-EMERGENCY`
- **要約へのフォーカス**: `transport-request-form.js` を読む社内の画面は S-04（`quotations/new.html:8`）・S-05（`transport-requests/review.html:8`）・S-09（`bookings/new.html:9`）・S-13（`milestones/new.html:9`）・`tracking-records/list.html:9`（荷主の C-03 も）。スクリプトが S-20 で受け持つのは要約へのフォーカス（:5-8）だけで、リンクの扱い（:106-117）は `form[data-stepwise]` と `#step-nav` がないと :38-42 で抜けるので動かない。リンクを押した後は、ブラウザの素のフラグメントの移動でフォーカスを受ける要素（最初の選択肢）へ移る（画面の層は chromium）。貨物種別の処理も :13 で抜けるので、S-20 で読んでもエラーは出ない。読むにはレイアウトの第 3 引数を `~{::script}` にして head に `<script … defer>` を置く（`bookings/new.html:3・:9` の形。今の `detail.html:3` は `~{}`）
- **`aria-describedby`・`aria-invalid`**: `fieldset` の `aria-describedby` は Bolt 34f で付いている（`detail.html:44・:72`）。`aria-invalid` はない。S-13 の群（`milestones/new.html:30・:54`）は `aria-invalid=${#fields.hasErrors(..)}` で、誤りのないときも `"false"` を出す。この Bolt は誤りのある群だけに `"true"` を付け、ほかは属性を出さない（S-20 の `aria-describedby` と同じ形。前例とは違う）
- **欄のない利用者への拒否**（指摘 8）: 役割の拒否のうち自分の役割（`SELF_CHANGE`）以外は「選び方の誤り」で欄へリンクする（`UserRoleChangeController.java:138-155`）。判定の順は自分 → 役割なし → 変更なし → 監督 → 企業の種類 → 職務分離（`User.java:129-154`）。荷受人の企業の利用者には役割の欄がない（`detail.html:38-40`）ので、URL から `roles=SALES` を今の版で送ると `UNFIT_FOR_COMPANY`、空なら `NO_ROLES`、監督の役割を含めれば `OVERSIGHT_ROLE` になり、どれも `#roles` へリンクするがリンク先がない。役割の欄がないのは自分と荷受人の企業の利用者。停止の理由の欄がないのは自分・監督の役割・停止中の利用者だが、判定の順（自分 → 監督 → 変更なし → 理由なし。`User.java:193-206`）で、欄がないのに「理由なし」になるのは、判定の後に読み直すまでの間の競合のときだけ。MockMvc はサービスを mock するので、この組み合わせは行 4b で確かめられる
- **版の解釈の順**（指摘 9）: 役割の確定の POST は `@RequestParam long version`（`UserRoleChangeController.java:73`）で、数でない版も版のない送信も、範囲の判定（`page.find(id, actor, true)`）より先に Spring の型変換で 400 になり、範囲外の試行が記録に残らない。利用状態の確定の POST は Bolt 34d で `@RequestParam(name = "version", required = false) String` で受けて `find` の後に解釈する（`UserStatusChangeController.java:81・:88`、`UserAdministrationParameters.version` が null と数でない値を 400 にする）。確認の GET はどちらも `long` で、範囲外の試行を記録しない（`find(…, false)`）ので変えない
- **`Section`**: 見出し（`title`）とリンク先（`anchor`）を持つ enum（`UserDetailPage.java:201-214`）。`render` が `errorOf(Section.ROLES)`・`errorOf(Section.STATUS)` を model に置き（:101-102）、テンプレートが領域ごとに誤りを示す。38c の「ログインの資格」はロックの解除と TOTP の解除の 2 つの操作を 1 つの領域に持ち（release_plan の W6 の確認ポイント 13）、選び方の誤りのない操作がある
- **欄を描く条件**: テンプレートの `!self and assignable`（`detail.html:40`）と `statusNote == null and user.status.name() == 'ACTIVE'`（:67）。リンク先を Java で決めると条件が 2 か所に分かれるので、Java で決めた欄の有無を model に置き、テンプレートはそれを読む
- **テスト**: リンク先を確かめるのは `UserRoleChangeControllerTest.java:175`（`href="#roles"`）と `UserStatusChangeControllerTest.java:179`（`href="#suspension-reason"`）。停止の理由の文言は `UserStatusErrorMessageTest.java:24`、`UserStatusChangeControllerTest.java:178`。画面の層は `manage_user_roles_ui.feature`・`manage_user_status_ui.feature`（:31 が「停止の理由を選んでください。」）で、`UserAdministrationUiSteps:166・:217・:223`、`UserStatusUiSteps:119` が要約の文言を `containsText` で見る。axe が回る。フォーカスとリンクを押した先を確かめるテストは 0 件
- **マニュアルとキャプチャ**: 11 章 :100「画面の上に示された理由はリンクになっていて、押すと『付ける役割』の欄へ移ります」、:102 の画像 `11-role-change-rejected.png`（`manual_screenshots.feature:153-156` が役割を 0 件にして撮る。要約のリンクの文が写る）、:104-108 の「示される理由」の表、:112「『停止の理由を選んでください。』と示されます」
- **設計の文書**: ui_design :181（S-20。「見出しとリンク先を controller が選ぶ」「『付ける役割』の欄へリンクする」）、:922（共通部品のエラー要約）、:1312（利用状態の拒否の文言の表の「理由なし」）。architecture_backend :317（S-20 の画面の構造。`Operation` と領域）
- **古くなる注**（T-148・T-152）: `detail.html:14-16`（「見出しとリンク先を controller が選ぶ」）、`UserDetailPage.java:200`（`Section` の Javadoc「見出しとリンク先を決める」）、:287（`ErrorSummary` の `@param anchor`）、ui_design :181、architecture_backend :317
- **数えた場所と switch**（T-148・T-175）: `Section` の値を使うのは `UserDetailPage`（:101-102・:297）と、`Operation` を作る 2 つの controller。`ErrorSummary.anchor` を読むのは `detail.html:20` だけ。`Section` で分岐する switch は今は 0 個で、この Bolt で `render` に 1 つ（分岐 2 つ）足す。38c で値が 2 つ増えると分岐 4 つになる。循環的複雑度の上限 10 を超えないよう、switch は欄の有無とリンク先を返す 1 つの操作にまとめる

## 設計の決定（案）

| 項目 | 決定 | 根拠 |
| :--- | :--- | :--- |
| リンクの文 | 「付ける役割: 役割を 1 つ以上選んでください。」「停止の理由: 利用終了・異動・緊急停止のどれかを選んでください。」。項目名は欄の legend と同じ語で、`Section` が持つ | 指摘 8、ui_design :922、開始準備の決定 (4) |
| 停止の理由の誤りの文言 | 「利用終了・異動・緊急停止のどれかを選んでください。」（欄の下の文も同じ）。`UserStatusChangeController.errorMessage`、ui_design :1312、マニュアル 11 章 :112、`manage_user_status_ui.feature:31`、文言のテストを直す | 開始準備の決定 (4) |
| リンク先 | 群の最初の選択肢。`Section` の固定のリンク先をやめ、`render` が描く選択肢から決める（役割は `roleOptions` の最初、停止の理由は `reasonOptions` の最初）。欄を描かないとき（役割: 自分・荷受人の企業の利用者。停止の理由: 自分・監督の役割・停止中の利用者）は、項目名を付けずに文言だけを示し、リンクしない | 開始準備の決定 (1)、指摘 8 |
| 欄の有無の置き場 | `render` で、`Section` ごとに欄を描くかとリンク先を 1 つの switch 式で決め（値を足すと網羅の誤りでコンパイルが止まる）、欄の有無を model に置く。テンプレートの欄を描く条件はそれを読む（条件を 1 か所に） | 開始準備の決定 (3)、横断検証の指摘 |
| `Section` の単位 | 1 つの値が 1 つの操作を表す（見出し「…できませんでした」と、選び方の誤りの欄の項目名）。選び方の誤りのない操作は項目名を持たず（`Optional`）、リンクしない | 開始準備の決定 (5) |
| フォーカス | `detail.html` で `transport-request-form.js` を読む（レイアウトの第 3 引数を `~{::script}` に）。スクリプトが要約へフォーカスし、リンクを押すとブラウザの素の移動で最初の選択肢へフォーカスが移る | 開始準備の決定 (2) |
| `aria-invalid` | 誤りのある群の `fieldset` だけに `aria-invalid="true"`。ほかの群には属性を出さない（S-13 は `"false"` を出すが、S-20 の `aria-describedby` と同じ形にそろえる） | 指摘 8 |
| 版の解釈の順 | 役割の確定の POST の版を `@RequestParam(name = "version", required = false) String` で受け、`page.find(id, actor, true)` の後に `UserAdministrationParameters.version` で解釈する（利用状態の確定と同じ）。範囲外の利用者に数でない版・版なしを送ると 404 で、試行が記録に残る | 指摘 9 |
| 足し方の文書 | `UserDetailPage` の Javadoc、ui_design の S-20（:181）、architecture_backend :317 に「操作ごとに `Section` に 1 行（見出しと、選び方の誤りがあれば項目名）、`render` の switch に欄の有無とリンク先、テンプレートに領域（1 つの領域に操作が 2 つなら値を 2 つ対応させる）、controller は `Operation` を渡す」と書く | 開始準備の決定 (3)・(5) |

## ふりかえりと前の Bolt からの引き継ぎ

| 出典 | 内容 | この Bolt での扱い |
| :--- | :--- | :--- |
| Bolt 37f3 Try T-198 | 受入条件の「見えること」の文は句ごとに確かめる行をテストに書く | 状態の組み合わせの表の各行を、句ごとの確かめに分けてテストにする |
| Bolt 37f3 Try T-199 | Gradle のログは結果を読み終えるまで消さない | ログはスクラッチパッドに置き、報告を書くまで消さない |
| Bolt 37f2 Try T-197 | 計画の「文書」のステップの行は、実装のコミットの前に一覧で突き合わせる | ステップ 4 の終わりに突き合わせる（下のステップ 4 の一覧） |
| Bolt 37e3 Try T-185 | 画面の文言を直す Bolt は、古い文言が出ないことを確かめる | 新しいリンクの文は古い文を含むので、行 1・2 はリンクの文の全体（`>付ける役割: 役割を…</a>` の形）で確かめる。行 2 は古い文言「停止の理由を選んでください。」が出ないことも確かめる |
| Bolt 37e3 Try T-186・Bolt 37e3b T-187 | 変異を替えない、全件が落ちたら見直す | 下の変異の表 |
| Bolt 37c Try T-175 | 列挙型に値を足す Bolt は switch と分岐を数え、循環的複雑度の上限 10 を計画に書く | 開始準備で確かめたこと（数えた場所と switch）に書いた |
| W5 の Try T-106・T-119 | 「ないこと」の前に対象があることを確かめる | 行 3・4・4b は、群や要約の文があることを先に確かめる |
| W5 の Try T-148・T-152 | 共通の部品と写しは数えてから変える | 数えた場所と古くなる注に書いた。`transport-request-form.js` は変えない |
| W5 の Try T-104・T-147・T-139 | SonarQube はほかの Gradle の後、テストのラムダの中で値を作らない | ステップ 5 |
| W5 の Try T-125 | 規則に当たったら止めて諮る | ArchUnit・ModularityTest に当たったら止める |
| Bolt 34f の既知の課題 | 指摘 8・9、H1 | この Bolt で閉じる |

## 状態の組み合わせ（行ごとにテストを 1 本。T-182）

| 行 | 状況 | 見えること | 出口 | 確かめ方 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 運用する会社の利用者で役割を 0 件にして確認へ | 要約のリンクの文が「付ける役割: 役割を 1 つ以上選んでください。」で `#role-SALES` へ向かう。役割の群に `aria-invalid="true"`、停止の理由の群には `aria-invalid` がない | 同じ詳細（役割を選び直す） | `UserRoleChangeControllerTest`（:175 を直す） |
| 2 | 利用中の利用者で停止の理由を選ばず確認へ | 要約のリンクの文が「停止の理由: 利用終了・異動・緊急停止のどれかを選んでください。」で `#reason-USAGE_ENDED` へ向かう。停止の理由の群に `aria-invalid="true"`、役割の群には `aria-invalid` がない。古い文言「停止の理由を選んでください。」が出ない | 同じ詳細（理由を選び直す） | `UserStatusChangeControllerTest`（:178-179 を直す） |
| 3 | 誤りのない詳細 | 役割と停止の理由の群がどちらもあり、どちらにも `aria-invalid` がない（陰性の対照） | — | `UserAdministrationControllerTest` |
| 4 | 荷受人の企業の利用者へ URL から `roles=SALES` を今の版で送る（`UNFIT_FOR_COMPANY`） | 要約に文言があり、項目名「付ける役割: 」が付かず、要約の中にリンク（`<a`）がない | 同じ詳細（利用状態の領域と「利用者の一覧へ戻る」） | `UserRoleChangeControllerTest` |
| 4b | 停止中の利用者に、確認で `REASON_REQUIRED` が返る（判定の後に停止された競合。サービスを mock） | 要約に文言があり、項目名が付かず、要約の中にリンクがない | 同じ詳細（再開の操作と「利用者の一覧へ戻る」） | `UserStatusChangeControllerTest` |
| 5 | 管理範囲の外の利用者へ、数でない版で役割の確定を送る。版なしでも送る | どちらも 404 で、範囲外の試行が記録に残る（今は 400 で残らない） | 共通の 404 の画面 | `UserRoleChangeControllerTest` |
| 6 | 画面の層で、役割を 0 件にして確認へ | 要約にフォーカスがあり、リンクを押すと最初の役割のチェックボックスにフォーカスが移る。axe が通る | 同じ詳細 | `UserAdministrationUiSteps`（`manage_user_roles_ui.feature`） |
| 7 | 画面の層で、停止の理由を選ばず確認へ | 要約にフォーカスがあり、リンクを押すと最初の停止の理由にフォーカスが移る。axe が通る | 同じ詳細 | `UserStatusUiSteps`（`manage_user_status_ui.feature`） |

## スコープ

### 入れるもの

- 行 1〜7 のテスト、`UserDetailPage`（`Section`・`render`・`ErrorSummary`）、`detail.html`（要約・`aria-invalid`・欄を描く条件・スクリプト）、`UserRoleChangeController` の版の受け方、`UserStatusChangeController` の停止の理由の文言
- `UserDetailPage` の Javadoc、ui_design（:181・:922 の参照・:1312）、architecture_backend :317 の領域と操作の足し方
- マニュアル 11 章（:100・:104-108 の確かめ・:112）と `11-role-change-rejected.png` の撮り直し（ほかの撮り直しの差分は戻す）
- release_plan（:703 の状態、:773 の 38 の行、更新履歴）、#50 へのコメント

### 入れないもの

- 確認の GET の版の受け方（範囲外の試行を記録しないので、順の違いが記録に響かない）
- ほかの画面のエラー要約とリンク先（気づいたものは既知の課題に書く）
- `transport-request-form.js` の名前と中身、共通の新しいスクリプト
- 動画（W6 のデモ項目に当たらない。release_plan :789 と開発戦略 :185 の動画を撮る Bolt の一覧に 38 はない）

## 設計（この Bolt の範囲）

- ドメインモデル図・状態遷移図・ER 図・画面遷移図: 型・状態・スキーマ・画面の遷移は変えないので省く。画面の層の部品（`Section`・`ErrorSummary`）と、要約の形・読み上げ・フォーカス・停止の理由の誤りの文言だけが変わる

## ステップ計画

- [ ] **1. 設計の文書**: ui_design（:181 の S-20 の要約の形・リンク先・フォーカス・足し方、:1312 の文言）、architecture_backend :317、release_plan（:703 の状態、:773 の 38 の行を「`aria-describedby` は 34f で付いている。マニュアルは 11 章の該当行とキャプチャ 1 枚。動画は撮らない」に）（完了の判定: 文書の差分）
- [ ] **2. テスト（Red）**: 行 1〜5 の MockMvc、行 6・7 の画面の層のステップ、停止の理由の文言のテストと feature :31。今の実装で期待した理由（文が項目名なし、リンク先が fieldset、`aria-invalid` がない、欄のない利用者でリンクがある、版の誤りが 400、要約にフォーカスがない、文言が古い）で落ちることを確かめる（完了の判定: 期待した理由で落ちる。行 3 は今の実装で緑のまま）
- [ ] **3. 実装（Green）**: `Section` に項目名（`Optional`）を持たせ固定のリンク先をやめる、`render` の switch で欄の有無とリンク先を決めて model に置く、`ErrorSummary` のリンクの文、`detail.html` の要約・`aria-invalid`・欄を描く条件・スクリプト（レイアウトの第 3 引数）、役割の確定の POST の版、停止の理由の文言（完了の判定: テストが緑、既存の S-20 のテストが緑のまま）
- [ ] **4. 文書とキャプチャ**: `UserDetailPage` の Javadoc と古くなる注（`detail.html:14-16`、`UserDetailPage.java:200・:287`）、マニュアル 11 章（:100・:104-108・:112）、`manualScreenshots` で撮り `11-role-change-rejected.png` だけを残す。実装のコミットの前に T-197 の突き合わせ（ui_design :181・:1312、architecture_backend :317、release_plan :703・:773、11 章 :100・:102・:104-108・:112、Javadoc と古くなる注 3 か所）をする（完了の判定: 差分と画像）
- [ ] **5. 品質ゲートと開発レビュー**: 変異、`check`・`uiTest`、その後に SonarQube、開発レビューとその直し
- [ ] **6. 終了報告**: push して CI と MkDocs とデモ環境の配備を確かめる。終了報告の承認の後に #50 にコメントし、release_plan（W6 節の状態、W6 の Bolt の一覧のリンク、更新履歴）・開発の索引・mkdocs を完了に直す

### 時間の配分と打ち切り

| # | 目安 | 打ち切り |
| :--- | :--- | :--- |
| 1 | 20 分 | — |
| 2 | 55 分 | 画面の層のリンクを押した後のフォーカスが安定しないときは、要約のフォーカスだけを画面の層で確かめ、リンク先は MockMvc のリンク先と選択肢の id の一致で確かめる（報告に書く） |
| 3 | 50 分 | — |
| 4 | 35 分（`manualScreenshots` の待ちを除く） | — |
| 5 | 35 分（待ちを除く） | — |
| 6 | 15 分（CI の待ちを除く） | — |

合計 210 分（約 3.5 時間）。

## 確認ポイント（計画の承認の場でまとめて確認する。Try T-6）

| # | 確認すること | 場所 | 推奨 |
| :--- | :--- | :--- | :--- |
| 1 | **リンク先（開始準備の決定 (1)）** | ステップ 3 | 群の最初の選択肢。欄を描かないときは項目名を付けずリンクしない |
| 2 | **フォーカス（開始準備の決定 (2)）** | ステップ 3 | `transport-request-form.js` を読む（変えない）。リンクの先へはブラウザの素の移動で移る |
| 3 | **`Section` の単位と足し方（開始準備の決定 (3)・(5)）** | ステップ 3・4 | 1 つの値が 1 つの操作。項目名は `Optional`、リンク先は `render` の switch で決める。足し方を Javadoc・ui_design・architecture_backend に書く |
| 4 | **停止の理由の文言（開始準備の決定 (4)）** | ステップ 3・4 | 「利用終了・異動・緊急停止のどれかを選んでください。」。欄の下の文も同じ |
| 5 | **版の解釈の順（指摘 9）** | ステップ 3 | 役割の確定の POST だけを利用状態の確定と同じ（`required = false` の `String`）にする。確認の GET は変えない |
| 6 | **`aria-invalid` の出し方** | ステップ 3 | 誤りのある群だけに `"true"`。S-13 の前例（`"false"` も出す）とは違う形 |
| 7 | **変異** | ステップ 5 | 下の 7 つ。スクリプトは gradlew.bat を絶対パスで呼び、結果の XML を消してから流す（T-164・T-178） |

### 変異と落ちる見込みのテスト

| # | 変異 | 落ちる見込み |
| :--- | :--- | :--- |
| V1 | リンクの文から項目名を外す | 行 1・2 |
| V2 | リンク先を最初の選択肢でなく最後の選択肢にする | 行 1・2・6・7 |
| V3 | `aria-invalid` を誤りの有無によらず付ける | 行 3 |
| V3b | 誤りがあれば両方の群に `aria-invalid` を付ける | 行 1・2 |
| V4 | 欄を描かないときもリンク先と項目名を返す | 行 4・4b |
| V5 | 役割の確定の POST の版を `find` の前に解釈する | 行 5 |
| V6 | `detail.html` でスクリプトを読まない | 行 6・7（要約にフォーカス） |

## 完了条件

- [ ] 上のステップ 1〜6 の完了の判定を満たす
- [ ] 行 1〜7（4b を含む）に、それを名乗るテストがある
- [ ] `check`・`uiTest` が緑、SonarQube が PASS、CI が緑、デモ環境が新しい版で動く
- [ ] マニュアル 11 章とキャプチャ（`11-role-change-rejected.png`）が実装と一致する
- [ ] 終了報告が承認される

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-25 | 計画を承認した（確認ポイント 1〜7 を推奨のとおり） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-25 | 整合性検証の指摘を反映した（承認待ち）。誤りのキャプチャの撮り直し、スクリプトの受け持ち（要約へのフォーカスだけ）、停止の理由の文言、`Section` の単位（操作ごと）、欄の有無の置き場、行 4b と出口の列、V3b・V6、`required = false`、古くなる注、引き継ぎ（T-175・T-185・T-186・T-187）、トレースを足した。停止の理由の文言と `Section` の単位を決めた（human:kakimomokuri） | anthropic/claude-opus-5-5 |
| 2026-10-25 | 初版作成（承認待ち）。開始準備で、リンク先は群の最初の選択肢、フォーカスは既存のスクリプト、`Section` は形を残して足し方を書くと決めた（human:kakimomokuri） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [リリース計画](release_plan.md)（W6 節の 38 の行）
- [Bolt 34f 終了報告](bolt_34f_report.md)（開発レビューの指摘 8・9、H1）、[Bolt 37f3 終了報告](bolt_37f3_report.md)
- [UI 設計](../../design/cargo-tracker/ui_design.md)（S-20、共通部品のエラー要約）、[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)（S-20 の画面の構造）
- [11 章 利用者の管理](../../manual/11-利用者の管理.md)
