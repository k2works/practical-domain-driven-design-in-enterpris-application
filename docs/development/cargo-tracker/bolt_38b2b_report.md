---
type: Plan
title: "Bolt 38b2b 終了報告 - 利用者の招待のメール（通知の側）と範囲外の企業の記録の規則"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-26T01:30:54Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-26T01:30:55Z }
---

# Bolt 38b2b 終了報告 - 利用者の招待のメール（通知の側）と範囲外の企業の記録の規則

## 承認の議題（T-36。先に確かめてほしいこと）

利用者の招待のメールを送る通知の側の部品と、Bolt 38b2 の終了報告で決めた ArchUnit の規則を作りました。招待を発行して送る identity の側は Bolt 38b2c です。そのため、まだ誰も招待のメールを送らず、画面と利用者から見える振る舞いは変わりません。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **文面（外部連携）**: 件名「【cargo-tracker】利用者の登録のご案内」と本文の 6 文（期限を過ぎたときの出口の 1 文を含む）。日本語だけで、企業名・役割・登録した人は書かない。場面ごとに本文が事実と合うかを表にした | `resources/mail/user-invitation.txt`、ui_design の「利用者の招待のメール」の節 |
| 2 | **共有の部品（構造）**: 記録（PENDING）・送信・SENT か FAILED・伏せ字を、再設定と招待のサービスが package-private の `LinkMailDelivery` に任せる。伏せ字は「[リンク]」「[メールアドレス]」。再設定の失敗の理由も、これまでの「[再設定のリンク]」から「[リンク]」に変わる（記録の文字列だけで、画面には出ない）。結果の型は `MailDeliveryOutcome` に改名した | `LinkMailDelivery`、ADR-017 の決定 1 |
| 3 | **スキーマ**: `notification.kind` の CHECK に `USER_INVITATION` を足した（DROP と ADD）。`event_id` は登録の ID、`target_ref` は招待のトークンの ID で、本文は再現しない（再設定と同じ例外） | `V20261026100000`、data_model の通知の表 |
| 4 | **ArchUnit の規則（T-205）**: `AuditRecord.adminScopeDeniedCompany` を呼べるのは `UserRegistrationService` だけ、という呼び出しの規則と、書き手がこの呼び出しを持ち続けることの見張り。メソッド参照は捕まえない（Javadoc に書いた） | `LayerArchitectureTest` |

計画は 2026-10-26 に承認を受けた（`9dd7a9b06`）。計画からの変更は次の 2 つ。

- 変異 V3 を V3a（リンクの全体を伏せない）と V3b（トークンを伏せない）に分けた。V3a は最初の実行で生き残った。トークンを伏せればリンクの残りの段（`https://cargo.example/password-reset/`）は残る。これを見ていなかったので、行 3 の期待を理由の全文に強めた
- 開発レビューの指摘で、次のテストを足した
  - 招待の側の「文面の組み立ての失敗」（行 3）
  - 本文が 6 行だけであることの確かめ（行 5。変異 V4b で確かめた）

### Red の確かめ

| テスト | 落ちた理由（中身を作る前） |
| :--- | :--- |
| 招待のサービス（行 1〜3） | 空のサービスが送らず記録を作らず、いつも `Sent` を返した（3 件） |
| 招待の文面（行 5） | 空のテンプレートが件名と本文を空で返した（件名が一致しない） |
| 公開 API から Mailpit（行 1・2） | 空のサービスが送らず、Mailpit に 1 通も届かなかった（2 件） |
| 保存と読み戻し（行 6） | CHECK に `USER_INVITATION` がなく、挿入が拒まれた（`DataIntegrityViolationException`） |
| 再設定の伏せ字（行 4） | 「[再設定のリンク]」のままだった（共有の部品に移す前に期待を変えて落とした） |
| ArchUnit の規則（行 8） | 今の本体で緑（守りの行）。変異 V5・V6 で落ちることを確かめた |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38b2b 回（W6。38b2b・38b2c の 1 つ目） |
| 期間 | 2026-10-26（計画の承認の記録 `9dd7a9b06` から、終了報告の承認の依頼まで） |
| 対象 | 通知の公開 API `UserInvitationMail`・`UserInvitationMailRequest`・`UserInvitationMailReceipt`・アダプター、`UserInvitationMailService`・`SendUserInvitationMailCommand`、`LinkMailDelivery`・`MailDeliveryOutcome`、`UserInvitationMailTemplate`・`ThymeleafUserInvitationMailTemplate`・`MailTextTemplates`、`NotificationKind`、マイグレーション、`NotificationConfiguration`、`LayerArchitectureTest`、テスト、設計の文書、release_plan |
| GitHub | [#14](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/14) |
| SP | 0 |
| 計画 | [Bolt 38b2b 計画](bolt_38b2b_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model・ui_design・non_functional（ACC-04・SEC-08）・data_model・test_strategy・architecture_backend・ADR-017・release_plan（38b2b と 38b2c に分けた）。T-197 で古い記述が残っていないか探して直した | `c786c03a5` |
| 2〜4. 共有の部品、招待の公開 API と文面とスキーマ、ArchUnit の規則 | 上の議題 1〜4 | `c786c03a5` |
| 5. 品質ゲートと開発レビュー | 変異 10 個、レビューの指摘の反映 | `c786c03a5` |
| 6. 終了報告 | この報告、release_plan（更新履歴） | この報告のコミット |

### 行とテスト（完了条件の「行 1〜8 に名乗るテスト」）

行 1〜5 と行 8 は、テストのコメントか Javadoc で行の番号を名乗っている。行 6 の H2（`H2DevProfileSmokeTest`）と行 7（`PublicApiArchitectureTest`）は、既存のテストが新しいマイグレーションと型に当たる。

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` 3044 件、失敗 0（前の 3033 件から 11 件増えた。`test` の XML で数えた）。`uiTest` 294 件、失敗 0（件数は前と同じ。レビューの直しの後は画面に触れていないので流し直していない） |
| 変異 | V1 招待の種類を再設定にする（行 1）、V2 記録がなくても送る（行 2・4）、V3a リンクの全体を伏せない（行 3。テストを強めた後）、V3b トークンを伏せない（行 3・4）、V4 期限の行を外す（行 5）、V4b 本文に 7 文目を足す（行 5。レビューで足した）、V5 ほかの部品も範囲外の企業の記録を作る（行 8 の呼び出しの規則）、V6 登録のサービスが記録を作らない（行 8 の名前の見張り）、V7 文面の組み立てを try の外に出す（行 3・4）: すべて落ちた（バイトのまま読み書きした。T-200） |
| SonarQube | PASS（新しいコードの網羅率 95.9%、重複 0.04%、違反 0）。レビューの直しの後にも流し、同じく PASS |
| `okf:check` | ERROR 0 |
| CI | `c786c03a5` で `check`・`ui`・`deploy-demo` が 1 回で成功した（MkDocs の配備も成功） |

### 開発レビューの指摘と扱い

| 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- |
| 中 | 行 3 の「文面の組み立てが失敗する」が招待のテストにない | 足した。V7 で落ちることを確かめた |
| 中 | data_model の通知の注が「7 値を置く」のまま | 「8 値を置く。33 は `PASSWORD_RESET`、38b2b は `USER_INVITATION`、残りの 6 値は W9」に直した |
| 低 | 本文の 6 文を「含む」ことしか見ていない | 6 行だけであることを足した（V4b で確かめた） |
| 低 | テストのコメントの V3a が計画の表にない | この報告の変異の欄に書いた |
| 低 | 行 8 の規則が行の番号を名乗っていない | Javadoc に「計画の行 8」を足した。行 6・7 は上の「行とテスト」に書いた |
| 低 | `reasonOf` と伏せ字の定数が package-private | `private` にした |
| 低 | domain_model の通知の節と architecture_backend の合成ルートが再設定だけを述べている | 招待のサービスと共有の部品、招待の文面のテンプレートを足した |
| 低 | release_plan の主なタスクの行にこの Bolt のリンクがない | この報告のコミットで足す |

## デモ項目

利用者から見える振る舞いが変わらないので、動画・キャプチャ・マニュアル・受入シナリオは変えていない（計画の完了条件のとおり）。

## 判断と学び

- 伏せ字は「漏れてはいけない値を含まない」だけでなく、全文で期待すると、部分の置き換えで残る段（リンクのトークンの手前）まで捕まえられる。変異 V3a がこれを見つけた
- 共有の部品にするときは、各呼び手のテストに同じ失敗の道（文面の組み立ての失敗）を置く。片方の呼び手にしかないと、変異は落ちても行の表と合わない
- 「書き手は 1 か所」は、呼び出しの規則と名前の見張りの 2 本で守る。1 本目は書き手が増えたら落ち、2 本目は書き手が動いたら落ちる（T-205）

### ふりかえり（KPT）

- Keep: 2 つの整合性検証で、範囲を 38b2b・38b2c に分けたことを計画の段階で決められた。変異 10 個がすべて落ちた
- Problem:
  - 変異 V3 を 1 つにまとめていたため、トークンの手前の段が残ることを最初の計画で見落とした
  - 文書の直しのスクリプトで、ADR の改訂の経緯を表だと思い込んだ（実際は箇条書き）。行は末尾に入った
  - 変異のスクリプトを heredoc で直して壊した（T-204 の再発）
- Try:
  - T-206: 伏せ字・マスクのテストは、残る文字列の全文で期待する（`doesNotContain` だけにしない）
  - T-207: 文書を直すスクリプトで節に行を足すときは、先に節の形（表か箇条書きか）を読む

### 既知の課題（後に持ち込む）

- 種類ごとの公開 API と共有の部品の範囲（一般の口にまとめるか、リンクのないメールに広げるか）は Bolt 38d で決める
- 伏せ字は、URL エンコードされたリンクや折り返されたリンクを伏せない（Bolt 33 からの性質）
- 招待の後の再設定・停止で、リンクが期限の前に使えなくなる（SEC-08 の受け入れたリスク。出口は A-05）

## 次の Bolt

Bolt 38b2c（利用者の招待の発行。identity の側。開始準備は `opening-iteration` で）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-26 | 終了報告を承認した（議題 1〜4） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-26 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38b2b 計画](bolt_38b2b_plan.md)、[リリース計画](release_plan.md)（W6 節の 38b2b・38b2c の行）
- [Bolt 38b2 終了報告](bolt_38b2_report.md)（議題 2。規則を足すと決めた）、[Bolt 33 計画](bolt_33_plan.md)（再設定のメール）
- [ADR-017](../../adr/cargo-tracker/017-notification-module-and-mail.md)、[UI 設計](../../design/cargo-tracker/ui_design.md)、[データモデル](../../design/cargo-tracker/data_model.md)、[ドメインモデル](../../design/cargo-tracker/domain_model.md)、[バックエンドのアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)
