---
type: Plan
title: "Bolt 38b3b 終了報告 - S-20 の利用者の登録の画面"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-26T09:21:14Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-26T09:21:15Z }
---

# Bolt 38b3b 終了報告 - S-20 の利用者の登録の画面

## 承認の議題（T-36。先に確かめてほしいこと）

システム管理者が S-20 の一覧から利用者を登録できるようにしました。流れは次のとおりです。

1. 企業を選ぶ。
2. 付ける役割・メールアドレス・表示名を入れる。
3. 確認する。
4. 登録する。

この画面は機能ブランチ `feature/bolt-38b3b`（PR #51）にあり、まだ develop にもデモ環境にも出ていません。38b4 と一緒にマージします。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **画面と文言**: 拒否の文言は表示名で始めない。画面で見る誤り（メールアドレスの空と形、表示名の空、付ける役割のなし）はまとめて示す。確認の領域は 6 つの項目（回復手段を含む）。確定の後は詳細へ PRG し、「利用者を登録しました。登録したメールアドレスへ初回の設定の案内を送ります。」を出す | キャプチャ `11-user-registration-*.png`、ui_design の S-20 |
| 2 | **認可と記録（セキュリティ）**: 5 つの URL はシステム管理者だけが使える。確認と「戻る」は CSRF のトークンが要り、何も書かず、範囲外も記録しない。範囲外の企業への確定は、値を解釈する前に登録のサービスが試行を記録して 404 にする（開発レビューの高-1。人の決定で直した。登録のサービスに `denyOutOfScope` を足した。書き手は登録のサービスのままで、ArchUnit の規則は変えていない） | `UserRegistrationController`、`UserRegistrationService` |
| 3 | **構造**: 表示名の正規化を `identity.domain.model.rules.DisplayNameRule` へ移した（人の決定）。登録の画面の部品 `UserRegistrationPage` は `UserDetailPage` を使わず、`RoleOption` の値だけを共有する | architecture_backend :323 |
| 4 | **ブランチと PR（GitHub）**: PR #51（develop 向け）を作った。PR の CI は緑。マージは 38b4 の終了報告の承認の後に、マージコミットで行う | PR #51 |

計画は 2026-10-26 に承認を受けた（`3f2bd8e0c`）。計画から変えたのは次の点です。

- **開発レビューで直したもの**:
  - 範囲外の記録の順（高-1）
  - `DisplayNameRule` への移動（人の決定）
  - UI テストの準備に名前の違う運用する会社「B 運輸」を足した（人の決定。計画では「荷主 テスト」も使う予定だったが、複数の役割で確かめるために運用する会社だけにした）
  - 文書の食い違い
  - テストの全文と件数
  - sealed の switch の `default` を消した
- **SpotBugs**: `Summary` の record の複製をとる形にした（EI_EXPOSE_REP）
- **SonarQube の指摘**: 1 件（assertThat を 1 つの連鎖にした）
- **入れなかったもの**: テンプレートの hidden の重複を `th:fragment` にまとめることはしなかった。レイアウトの中から同じテンプレートの断片を参照できるかが不確かで、壊すおそれがあるため。SonarQube は HTML の重複を数えていない

### Red の確かめ

| テスト | 落ちた理由（中身を作る前） |
| :--- | :--- |
| UI の通し（行 14。最初に流した） | 一覧に「利用者を登録する」がなく、待ち時間を過ぎて 4 シナリオが落ちた |
| controller と統合（行 1〜13） | 23 件が落ちた。空のテンプレートで、選択肢・要約・確認の領域・hidden がなかった。空の controller が、404・403・リダイレクトの代わりに 200 を返した。一覧に入口がなかった |
| CSRF（行 12）・ほかの役割の 403（行 12） | 空の形でも緑だった（フレームワークの検査と URL の認可で守られる行） |
| 範囲外の確定の記録（高-1 の直し） | サービスの単体のテスト: 空の口が false を返した。統合のテスト: 空のメールアドレスで確定しても試行が 0 行だった。外側のトランザクションの中でも例外が出なかった |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38b3b 回（W6 の 24 本目。38b3・38b3b の 2 つ目） |
| 期間 | 2026-10-26（計画の承認の記録 `3f2bd8e0c` から、終了報告の承認の依頼まで） |
| 対象 | `UserRegistrationController`・`UserRegistrationPage`・テンプレート 3 つ・`list.html`、`UserAdministrationParameters`・`UserAdministrationLabels`・`UserRoleChangeController`、`UserRegistrationService`（`denyOutOfScope`）、`DisplayNameRule`、テスト、マニュアル 11 章・00・index とキャプチャ、設計の文書 |
| GitHub | [#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14)、PR [#51](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/pull/51) |
| SP | 0 |
| ブランチ | `feature/bolt-38b3b`（38b4 も続ける） |
| 計画 | [Bolt 38b3b 計画](bolt_38b3b_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 0. ブランチ | `feature/bolt-38b3b` | — |
| 1. 設計の文書 | ui_design（:145、URL の表、確認は書かない、画面遷移図、登録の小節、確認の領域とボタンの名前）、architecture_backend :323、test_strategy、domain_model（`DisplayNameRule`） | `f208df2d7` |
| 2・3. Red と Green | 上の議題 1〜3 | `f208df2d7` |
| 4. マニュアルとキャプチャ | 11 章（冒頭、画面の表、11.1 の入口と記録の文、11.5）、00-はじめに、index、キャプチャ 5 枚（`11-users` の撮り直しと新しい 4 枚。ほかの PNG の撮り直しの差分は戻した） | `f208df2d7` |
| 5. 品質ゲートと開発レビュー | 変異 11 個、レビュー・SpotBugs・SonarQube の指摘の反映 | `f208df2d7` |
| 6. 終了報告 | この報告（ブランチに置く）、PR #51 | この報告のコミット |

### 行とテスト（完了条件の「行 1〜14 に名乗るテスト」）

行 1〜14 は、`UserAdministrationControllerTest`・`UserRegistrationControllerTest`・`UserRegistrationSecurityIntegrationTest`・`register_user_ui.feature` が名乗っている。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` 3110 件、失敗 0。前の 3070 件から 40 件増えた（`test` の XML で数えた）。`uiTest` 302 件、失敗 0（前の 294 件から 8 件増えた）。`manualScreenshots` 24 件、失敗 0 |
| SpotBugs | 0 件（1 回目は `Summary` の EI_EXPOSE_REP で `check` が落ちた。複製をとって直した） |
| 変異 | すべて落ちた（バイトのまま読み書きした。T-200）。<br>・V1 確認で登録する（行 6）<br>・V2 行き先にメールアドレス（行 11）<br>・V3 範囲外を企業を選ぶ画面へ戻す（行 5・10）<br>・V4 役割を宣言の順に並べ直さない（行 4）<br>・V5 メールアドレスの形を見ない（行 7）<br>・V6 画面の誤りを最初の 1 件だけにする（行 7）<br>・V7 戻るで入力を戻さない（行 10）<br>・V8 1 段目の印を見ない（行 3）<br>・H1a 確定で範囲外を先に記録しない（統合）<br>・H1b 範囲内でも記録する（単体）<br>・H1c 外側のトランザクションのガードを外す（統合 2 本） |
| SonarQube | 直した後は PASS（新しいコードの網羅率 95.7%、重複 0.04%、違反 0）。1 回目は違反 1 件（MINOR） |
| `okf:check` | ERROR 0 |
| CI | PR #51 の CI（`f208df2d7`）で `check`・`ui` が 1 回で成功した（`deploy-demo` は PR では動かない）。デモ環境の配備の確かめは 38b4 の終了報告 |

### 開発レビューの指摘と扱い（主なもの）

| 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- |
| 高 | 入力に誤りのある範囲外の確定が記録されずに 404 になる | 人の決定で、値を解釈する前に記録する形にした（H1a〜c で確かめた） |
| 中 | architecture_backend :323 が実装と違う | 実装に合わせた |
| 中 | ui_design の確認の領域・操作の列挙・ボタンの名前に登録がない | 足した |
| 中 | 行 12 のほかの役割に荷主担当者がない | 足した |
| 中 | 表示名の正規化がサービスの public static | 人の決定で `DisplayNameRule` へ移した |
| 中 | UI テストの企業が計画から外れていた | 人の決定で「B 運輸」を準備に足した |
| 中 | 三項演算子の入れ子、handler の引数の重複、hidden の重複 | 前の 2 つは直した。hidden の重複は残した（上） |
| 低 | sealed の switch の `default`、テストの全文・件数・職務分離、記録しないことの統合テスト、GET のフォームに CSRF のトークンが出ないこと、CSRF の拒否の行き先、ui_design の一般化の置き場所、ワイヤーフレームの違い、マニュアル、`autocapitalize` | 直した |

## デモ項目

マニュアル 11 章の 11.5 とキャプチャ 5 枚を足した。動画・受入シナリオ（登録から A-07 まで）・受入条件の照合の表・デモ環境の配備の確かめは 38b4 で行う（計画のとおり）。

## 判断と学び

- 範囲外の試行の記録は、値の検査より先に置く。画面を経ない改ざんの送信こそ、監査が捕まえたい試行（前例 ui_design :182 と同じ順）
- 大きな画面の Bolt は、実装をフォークに任せ、親はゲートとレビューと人の判断に集中すると、文脈を保てた。ただしキャプチャの全体の撮り直しのように、範囲の外へ広がる副作用は親が見て戻す
- JS のない GET のフォームでは、ラジオを選ばずに送ると値が送られない。段の印（hidden）で、初めて開いたときと見分ける

### ふりかえり（KPT）

- Keep: 2 つの整合性検証で文言の決まりと「選ばずに次へ」を計画の段階で直せた。変異 11 個がすべて落ちた
- Problem:
  - 開発レビューで高の指摘（範囲外の記録の順）が出た。計画の行 11 が、入力の正しい場合しか見ていなかった
  - フォークが heredoc で 2 回止まった（T-204 の再発）
- Try:
  - T-211: 範囲や権限の記録の行は、入力の誤りと組み合わせた行（誤った値 × 範囲外）も状態の組み合わせの表に置く
  - T-212: フォークに任せるときも、指示に T-204（heredoc を使わない）を最初に書く

### 既知の課題（後に持ち込む）

- エラー要約の二重の読み上げと fieldset の `aria-invalid`、リンクの先へのフォーカスは chromium だけで確かめた（Bolt 38 の既知の課題。W6 の支援技術による手動確認で確かめてから共通の部品ごと直す）
- 停止の理由の文言を名前の文字列で持つ（Bolt 38 の既知の課題）
- 入力の控え（ACC-06）は、POST の応答の画面（確認の誤り・戻る）で確かめていない
- テンプレートの hidden の重複（上）
- 計画の外の見分けにくい名前（Bolt 38b3 の既知の課題）

## 次の Bolt

Bolt 38b4（A-06 の初回の設定の画面の出し分け、受入シナリオ、動画。同じ機能ブランチで続ける。開始準備は `opening-iteration` で）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-26 | 終了報告を承認した（議題 1〜4） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-26 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38b3b 計画](bolt_38b3b_plan.md)、[リリース計画](release_plan.md)（W6 節の 38b3b・38b4 の行）
- [Bolt 38b3 終了報告](bolt_38b3_report.md)、[Bolt 38b2c 終了報告](bolt_38b2c_report.md)、[Bolt 38 終了報告](bolt_38_report.md)
- [UI 設計](../../design/cargo-tracker/ui_design.md)（S-20）、[ユーザーマニュアル 11 章](../../manual/11-利用者の管理.md)
