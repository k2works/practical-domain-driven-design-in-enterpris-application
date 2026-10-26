---
type: Plan
title: "Bolt 38b4 終了報告 - A-06 の初回の設定の出し分けと、登録から A-07 までの受入シナリオ"
tags: [plan]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-26T22:19:33Z }
---

# Bolt 38b4 終了報告 - A-06 の初回の設定の出し分けと、登録から A-07 までの受入シナリオ

## 承認の議題（T-36。先に確かめてほしいこと）

招待のメールのリンクから開いた A-06 を「初回の password の設定」として見せるようにしました。システム管理者の登録から A-07（認証アプリの登録）までを、受入シナリオと動画で通しています。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **画面の文言**: <br>・A-06 の初回: 見出し「password の設定」、説明「初めて使う password を入力してください。」、title「password の設定 - cargo-tracker」<br>・A-01 の初回の知らせ: 「password を設定しました。このブラウザでログインしていた場合は、ログアウトしました。設定した password でログインし、認証アプリを登録してください。」<br>・使えないリンクの案内: 用途を問わず、時間を書かず、「有効期限を過ぎた、使用済み、新しいリンクが送られた、などが考えられます。」 | キャプチャ `02-password-initial-new.png`・`02-login-initial-password-set.png`・`02-password-reset-invalid.png`、動画 |
| 2 | **セキュリティ**: 次の DE-25 では招待を発行しない。<br>・登録から 24 時間を過ぎて遅れて配られた<br>・登録の後に本人の再設定のトークンがある（同じ時刻なら発行する）<br>どちらも、用途のメソッドで問い合わせる（`setsInitialPassword`・`acceptsTriggerAt`・`yieldsTo`。`USER_INVITATION` を外から参照しない）。再設定の振る舞いは変わらない | `PasswordResetTokenPurpose`、`PasswordResetTokenIssuance` |
| 3 | **監査**: 招待のリンクで初回の password を設定したら `INITIAL_PASSWORD_SET`（成功だけ、同じトランザクション）。再設定のリンクで初めて設定する場合は `PASSWORD_RESET_COMPLETED` のまま（開始準備の決定 (7) で受け入れた） | `PasswordResetCompletionService` |
| 4 | **受け入れたこと（開発レビューの後の決定、2026-10-27）**: <br>・登録したての利用者も、第三者の誤ったログインでロックされうる（設定でロックは解けない。SEC-02）。ui_design の場面の表とマニュアル 2.6 の注に書いた<br>・招待のリンクの期限は発行から数えるので、遅れて配られると登録から最大で約 48 時間使える。IA-INV-12 と SEC-08 に書いた | 文書 |
| 5 | **動画の添付（GitHub）**: 承認の後に、動画を #14 へ添付する（`issue:attach-demo`。dry run は済み） | `docs/assets/demo/bolt-38b4/register-and-setup.webm` |

計画は 2026-10-26 に承認を受けた（`f5104012a`）。計画から変えたのは次の点です。

- **テストの置き場所**: WebMvc の `PasswordResetControllerTest` は作らず、行 2・3・6 は `AuthenticationSecurityIntegrationTest` で確かめた。実際のテンプレートとセキュリティのフィルターを通すので、こちらの方が強い。
- **`isUsable` の扱い**: 消さずに残し、中身を `setupOf(tokenId).isPresent()` にした。`complete` と既存のテストが使っているため。
- **受入シナリオの共有の値**: `UiScenarioState` ではなく、`UserRegistrationUiSteps` の中で持たせた。登録から A-07 までのステップが同じクラスにあり、ほかのクラスと共有する値がないため。
- **開発レビューで足したもの**:
  - テスト: 再設定の A-06 の全文、行 4 の統合テスト、`AuditRecordTest`、使えないリンクの全文と招待の期限切れ、`setupOf` の期限切れと使用済み
  - 動画の索引と添付先の表、ADR の改訂の経緯、発行の手順の括弧書き、付録 B、release_plan の 38b4 の行
- **SpotBugs**: `PasswordResetController` を、前例と同じくクラスを限って抑止した（DI の部品を保持するだけ）。
- **開発の索引**: 38b2b〜38b4 の行が抜けていたので埋めた。

### Red の確かめ

| テスト | 落ちた理由（中身を作る前） |
| :--- | :--- |
| 受入シナリオ（行 12。最初に流した） | A-06 の見出しが「password の再設定」のままで、期待は「password の設定」 |
| 用途と発行の抑え（行 1・9・10。3 件） | 用途のメソッドが仮の値を返した。遅れた DE-25 と、再設定の後の DE-25 でも発行した（期待 0 に対して 1、期待 1 に対して 2） |
| A-06 の出し分けと監査（行 2〜8。6 件） | `setupOf` が空を返した。`Completed` が初回でも false だった。使えないリンクと初回の title・見出しが古いままだった |
| 再設定（行 11）・ArchUnit（行 13） | 今の実装で緑（守りの行）。行 13 は変異 V9 で落ちることを確かめた |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38b4 回（W6 の 25 本目） |
| 期間 | 2026-10-26〜27（計画の承認の記録 `f5104012a` から、終了報告の承認の依頼まで） |
| 対象 | `PasswordResetTokenPurpose`、`PasswordResetTokenIssuance`、2 つのハンドラー、`AuditAction`・`AuditRecord`、`PasswordResetCompletion`・`PasswordResetCompletionService`・`PasswordSetup`、`PasswordResetController` とテンプレート 3 つ、テスト、受入シナリオと Steps、動画、マニュアル 2 章・11 章・付録 B とキャプチャ、設計の文書、添付先 |
| GitHub | [#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14) |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 38b4 計画](bolt_38b4_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | ui_design、domain_model、data_model、ADR-012・ADR-017、architecture_backend、SEC-08、user_story、test_strategy、release_plan | `6d6a591ab` |
| 2〜4. Red と Green | 上の議題 1〜3 | `6d6a591ab` |
| 5. マニュアル・キャプチャ・動画・照合 | 2 章に「初回の password の設定」、付録 B、11 章、キャプチャ 3 枚、動画 `bolt-38b4/register-and-setup.webm`。計画の外で撮り直された PNG 47 枚と動画 65 本は戻した | `6d6a591ab` |
| 6. 品質ゲートと開発レビュー | 変異 13 個、レビューと SpotBugs の指摘の反映 | `6d6a591ab` |
| 7. 終了報告と配備 | この報告、CI と配備 | この報告のコミット |

### 行とテスト（完了条件の「行 1〜13 に名乗るテスト」）

行 1〜13 は、次のテストが名乗っている。

- `PasswordResetTokenPurposeTest`
- `AuthenticationSecurityIntegrationTest`
- `PasswordResetCompletionServiceTest`・`PasswordResetCompletionIntegrationTest`
- `UserInvitationIntegrationTest`
- `register_user_ui.feature`
- `PasswordResetRequestIntegrationTest`（既存）
- `LayerArchitectureTest`（既存）

### US-16 の受入条件の照合（途中の経過。閉じるのは 38g）

| 受入条件 | 確かめるもの | Bolt | 状態 |
| :--- | :--- | :--- | :--- |
| AC1（固定役割の付与と関係・変更履歴） | 役割の変更（`@US-16-AC1` の役割の変更）、登録から初回の設定と A-07 まで（`@demo-bolt-38b4/register-and-setup`）、監査 `ROLES_CHANGED`・`USER_REGISTERED`・`INITIAL_PASSWORD_SET` | 34・38b〜38b4 | 担当案件の関係は 38e |
| AC2（管理範囲の外の拒否と試行の記録） | `@US-16-AC2`、範囲外の試行の記録（役割・状態・登録） | 34b・38b2・38b3b | 済み |
| AC3（職務分離） | `@US-16-AC3`、職務分離の拒否 | 34・38b | 済み |
| AC4（業務担当者の担当範囲の外の拒否と試行の記録） | — | 38e〜38g | 未 |
| AC5（利用終了・取消しで次の request から拒否、履歴は保持） | `@US-16-AC5`（利用停止） | 34d | 役割の取消しのシナリオは 38g |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` 3125 件、失敗 0（前の 3110 件から 15 件増えた。`test` の XML で数えた）。`uiTest` 304 件、失敗 0（前の 302 件から 2 件増えた）。`manualScreenshots` 24 件、失敗 0。`demoVideo` 成功 |
| SpotBugs | 0 件（1 回目は `PasswordResetController` の EI_EXPOSE_REP2 で `check` が落ちた。前例と同じくクラスを限って抑止した） |
| 変異 | すべて落ちた（バイトのまま読み書きした。T-200）。<br>・V1 招待でも初回でない（行 1・2・4）<br>・V2 期限の境界を「以下」にする（行 1）<br>・V3 登録の時刻を渡さない（行 9・10）<br>・V4 後の発行に譲らない（行 10）<br>・V5 初回でも再設定の監査（行 4）<br>・V6 初回でも `reset-completed` へ移す（行 4）<br>・V7 使えない案内に「30 分」を戻す（行 7）<br>・V8 RESET も期限で見る（行 1）<br>・V9 再設定のハンドラーが招待の用途を参照する（行 13）<br>・レビューで足したテストの 4 つ: 再設定の説明を変える、初回でも再設定の監査（統合）、初回の監査の利用者の検査を外す、`setupOf` が使えるかを見ない |
| SonarQube | PASS（新しいコードの網羅率 95.8%、重複 0.04%、違反 0） |
| `okf:check` | ERROR 0 |
| CI | `6d6a591ab` で `check`・`ui`・`deploy-demo` が 1 回で成功した（デモ環境は v453 で動いている） |

### 開発レビューの指摘と扱い

| 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- |
| 中 | 再設定のリンクの A-06 が今のままであることを全文で確かめていない | 足した |
| 中 | 行 4 の統合テストを足していない | 足した |
| 中 | 動画の索引・添付先の表・`BOLT_ISSUES` がない | 足した（dry run は成功） |
| 中 | 登録したての利用者のロック | 人の決定で受け入れ、文書に書いた（議題 4） |
| 低 | `isUsable` と `setupOf` の重複、Javadoc | `isUsable` を `setupOf` に寄せた |
| 低 | `manualKey` のフィールド（S1450）、`AuditRecordTest`、ADR の改訂の経緯、発行の手順の括弧書き、付録 B、release_plan の 38b4 の行、使えないリンクと `setupOf` のテストの抜け | 直した |
| 低 | 招待のリンクの寿命 | 人の決定で受け入れ、注に書いた（議題 4） |
| 低 | 生成済みのマニュアルの HTML に古い文言が残る | 公開の手順のときに作り直す（T-197 の突き合わせの対象外） |

## デモ項目

- 動画: [登録から初回の設定と A-07 まで](../../assets/demo/bolt-38b4/register-and-setup.webm)（承認の後に #14 へ添付する）
- マニュアル: 2 章「初回の password の設定」、付録 B、11 章

## 判断と学び

- 大きな Bolt は、実装と直しをフォークに任せ、親はゲート・レビュー・人の判断・副作用の戻し（キャプチャと動画の撮り直し）に集中すると、6 時間の Bolt でも文脈を保てた
- 用途で出し分けるときは、列挙の中のメソッドで問い合わせると、ArchUnit の規則（`USER_INVITATION` の参照）と、テンプレートで用途を比べないという約束を両方守れる
- 遅れた配り直しの抑えは、契機の時刻をイベントに載せておくと足せる（DE-25 に登録の時刻を載せていた）

### ふりかえり（KPT）

- Keep: 2 つの整合性検証で、照合が閉じないこと（AC4 は 38e〜38g）と、使えないリンクの文言を計画の段階で直せた。変異 13 個がすべて落ちた
- Problem:
  - 計画の新しいファイル（WebMvc のテスト）と、実装の判断（統合テストで確かめる）が食い違った
  - SpotBugs の指摘で、`check` が 2 Bolt 続けて落ちた
- Try:
  - T-213: 画面のクラスに DI の部品を足すか替えるときは、SpotBugs の抑止の一覧に当たるかを、ゲートの前に `spotbugsMain` で確かめる

### 既知の課題（後に持ち込む）

- エラー要約の二重の読み上げ・chromium だけ、停止の理由の文言、ACC-06、hidden の重複、見分けにくい名前（38b3b までの既知の課題）
- 登録したての利用者のロック、招待のリンクの寿命（議題 4 で受け入れた）
- US-16 の照合を閉じるのは 38g

## 次の Bolt

Bolt 38c（ロックの解除と管理者による TOTP の解除。#47 の前半。開始準備は `opening-iteration` で）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-27 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38b4 計画](bolt_38b4_plan.md)、[リリース計画](release_plan.md)（W6 節の 38b4 の行）
- [Bolt 38b3b 終了報告](bolt_38b3b_report.md)、[Bolt 38b2c 終了報告](bolt_38b2c_report.md)、[Bolt 33a 終了報告](bolt_33a_report.md)
- [UI 設計](../../design/cargo-tracker/ui_design.md)（A-06）、[ユーザーマニュアル 2 章](../../manual/02-ログインと共通の画面.md)
