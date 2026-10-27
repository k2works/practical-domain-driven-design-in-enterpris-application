---
type: Plan
title: "Bolt 38c2 終了報告 - S-20 の詳細に停止の記録を示し、ロックの期限を項目に分ける"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-27T07:43:57Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-27T07:43:59Z }
---

# Bolt 38c2 終了報告 - S-20 の詳細に停止の記録を示し、ロックの期限を項目に分ける

## 承認の議題（T-36。先に確かめてほしいこと）

停止中の利用者の詳細（S-20）に、今の停止の「停止の理由」「停止した人」「停止した日時」を示すようにしました。値は監査記録 `USER_SUSPENDED` から読み、対象で引く索引を足しました。ロックの期限は「ロック」と「解ける日時」の 2 つの項目に分けました。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **スキーマ**: 索引 `ix_audit_record_target (target_type, target_id, occurred_at)` を足すマイグレーション `V20261027100000`（`common`、通常の `CREATE INDEX`）。表と列は変えない | マイグレーション、`MyBatisIdentityRepositoriesIntegrationTest`（`pg_indexes`） |
| 2 | **示す値（計画から足した判定）**: 停止と再開のうち最新の 1 行を引き、それが再開なら何も示さない（停止 → 再開 → DB を直接書き換えた停止で、前の停止を今の停止として示さないため。開発レビューの指摘）。最新の停止の理由か停止した人が欠けていれば「停止の記録: 記録がありません」に落とす（詳細と確認の画面を 500 にして再開の経路をふさがないため） | `IdentityMapper.xml`、`SuspensionRecordRow.isCurrentSuspension`、契約テスト |
| 3 | **画面の文言**: 計画の文言の表のとおり。停止した人は管理範囲の外でも今の表示名、行がなければ「不明（利用者 ID: …）」。ロックの解除の確認の画面は、確かめの後に期限が切れていれば「現在の状態: なし」として「解ける日時」を出さない（開発レビューの指摘） | キャプチャ `11-user-detail-suspended.png`・`11-unlock-confirmation.png`・`11-users.png` |
| 4 | **負債の記録**: 監査記録のリポジトリに照会の口を足したので、追記しか使わない手書きの実装 9 か所を直した。US-17（S-21）で照会の口が増えるたびに同じ 9 か所が変わるので、US-17 の開始準備で照会の口を分けるか（追記の `AuditRecordRepository` と照会の口）を諮る | `AuditRecordRepository` |

計画は 2026-10-27 に承認を受けた（`1a5650d61`）。計画から変えたのは次の点です。

- **契約の置き場所**: `identity.infrastructure.persistence` に置いた（計画は `domain.model.aggregates`）。計画が前例に挙げた `UserStandingRepositoryContract` も persistence にあり、監査記録には InMemory の偽物がなく、契約の主題が mapper の SQL のため。
- **`new UserDetail(` の 13 か所**: 直さず、停止の記録を持たない 11 引数のコンストラクタを足した（リポジトリが読む形。正のコンストラクタは 12 項目のまま、`asOf` は記録を保つ。Javadoc に「作り直しに使わない」と書いた）。
- **SQL の判定**: 上の議題 2。壊れた行の判定は WHERE ではなく `isCurrentSuspension()` に置いた。WHERE で落とすと、理由を持たない再開の行も消え、壊れた最新の停止を飛ばして前の停止に戻るため。
- **確認の画面**: 上の議題 3 の「なし」の出し分けを足した。日時の整形の入口を `UserAdministrationLabels.staffTime` にまとめた。
- **キャプチャ**: 一覧のキャプチャ `11-users` は、ログインの資格のシナリオの最後に移した（停止とロックを 1 人に重ね、停止中の行を写すため）。撮る前に詳細を開き直し、知らせを写さない。`11-user-detail` は差分が出ないので撮り直していない。ほかの撮り直しの差分 47 枚は戻した。
- **マニュアル**: 分のずれの注を、決定 (9) の 11.2 と付録 B Q1-1 に加え、11.6 にも置いた。
- **data_model の ER の索引の注**: ER 図はどの表にも索引の注を持たないので該当なし（索引の表に足した）。

### Red の確かめ

| テスト | 落ちた理由（中身を作る前） |
| :--- | :--- |
| 受入シナリオ（行 11。最初に流した） | 期待「ロック: ロック中」に対して「ロック中（… まで）」のまま。`#suspension-record-reason` がない |
| 契約（H2・PostgreSQL。行 4〜6・5a・5b） | 口の仮の実装が空を返した |
| 索引（行 10） | `pg_indexes` が 0 件 |
| 照会・controller（行 1〜3・7〜9c） | 監査記録を読まない、`suspension-record-*` がない、ロックの 1 行の形が残る、確認の画面の現在の状態が違う、描き直した詳細に記録がない |
| 開発レビューで足したテスト | 再開の後の記録のない停止で前の停止が返った。欠けた行で `NullPointerException`。確認の画面の現在の状態が「ロック中」のまま。決定 (11) の契約は守りのテストで、最初から緑 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 38c2 回（W6 の 27 本目） |
| 期間 | 2026-10-27（計画の承認の記録 `1a5650d61` から、終了報告の承認の依頼まで） |
| 対象 | マイグレーション、`SuspensionRecord`・`SuspensionRecordRow`、`AuditRecordRepository` と mapper と手書きの実装 9 か所、`UserDetail`、照会のサービスと組み立て、`UserDetailPage`・`UserAdministrationLabels`・`UserLoginCredentialController`、テンプレート 2 つ、契約テストと H2・PostgreSQL の子、受入シナリオ、マニュアル 11 章・付録 B・用語集とキャプチャ、設計の文書 |
| GitHub | [#50](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/50)（利用者の管理の「停止の理由などの表示」の 2 つの課題） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 38c2 計画](bolt_38c2_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | ui_design、domain_model（用語集「停止の記録」）、data_model、non_functional（PERF-01・SEC-13）、architecture_backend、user_story、test_strategy、operation | `014095c90` |
| 2. 受入シナリオの Red | `manage_user_status_ui.feature` に 2 シナリオ（停止の記録、幅 320 px）、ロックの 2 つの UI ステップの期待 | `014095c90` |
| 3〜4. Red と Green | 上の議題 1〜3 | `014095c90` |
| 5. マニュアル・キャプチャ | 11 章（11.2・11.6・停止中の図）、付録 B Q1-1、用語集、キャプチャ 3 枚（T-197 で目で確かめた） | `014095c90` |
| 6. 品質ゲートと開発レビュー | 変異 11 個、レビューの指摘の反映 | `014095c90` |
| 7. 終了報告と配備 | この報告、CI と配備 | この報告のコミット |

### 行とテスト（完了条件の「行 1〜11 に名乗るテスト」）

- `UserAdministrationControllerTest`（行 1・2・6・7・8・9c）
- `UserAdministrationQueryServiceTest`（行 3・8b・9）
- `AuditRecordSuspensionContract` と `H2AuditRecordSuspensionTest`・`MyBatisAuditRecordSuspensionIntegrationTest`（行 4・5a・5b・6、再開の後、欠けた行、決定 (11)）
- `UserStatusChangeControllerTest`（行 9b）、`UserLoginCredentialControllerTest`（行 7 の確認の画面）
- `MyBatisIdentityRepositoriesIntegrationTest`（行 10）
- `manage_user_status_ui.feature`・`manage_user_login_credentials_ui.feature`（行 11。幅 320 px と axe を含む）

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` 3247 件、失敗 0（前の 3218 件から 29 件増えた。`test` の XML で数えた）。`uiTest` 320 件、失敗 0（前の 316 件から 4 件増えた）。`manualScreenshots` 26 件、失敗 0 |
| SpotBugs | 0 件 |
| 変異（計画の V1〜V8） | 8 個すべてが落ちた（対象に契約の H2・PostgreSQL の子を入れた。T-214）。<br>・V1 利用中でも記録を読む（行 3）<br>・V2 記録がないときに何も示さない（行 2・9c）<br>・V3 並びを ASC にする（行 4）<br>・V4 `action` の条件を外す（行 5a）<br>・V5 ロックの期限を 1 行に戻す（行 7）<br>・V6 ロックなしでも解ける日時を出す（行 8）<br>・V7 `target_id` の条件を外す（行 5b）<br>・V8 LEFT JOIN を INNER にする（行 6） |
| 変異（レビューの直し） | V9 再開の条件を外す、V10 欠けた行の判定を外す、V11 確認の画面の現在の状態を常に「ロック中」にする。3 つとも落ちた |
| SonarQube | PASS（網羅率 95.5%、重複 0.1%、違反 0。1 回目は変数名 `record`（制限された識別子）の 2 件で落ちたので直した） |
| `okf:check` | ERROR 0 |
| CI | `014095c90` で `check`・`ui`・`deploy-demo` が成功した（デモ環境は v470 で動いている） |

### 開発レビューの指摘と扱い

| 重大度 | 指摘 | 扱い |
| :--- | :--- | :--- |
| 中 | 幅 320 px を、停止の記録とロックの 2 行の画面で確かめていない | シナリオを 1 本足した |
| 中 | 停止 → 再開 → DB を直接書き換えた停止で、前の停止を今の停止として示す | 決定 (3) に反するので直した（議題 2） |
| 低 | 契約の置き場所、照会の口による負債（9 か所） | この報告に記録した（議題 4） |
| 低 | 壊れた行で 500、確認の画面の現在の状態、日時の整形の入口、Javadoc（11 引数・画面の判定）、キャプチャの注と知らせ、マイグレーションの注、import、決定 (11) を名乗るテスト | 直した |
| 低 | SQL と H2・PostgreSQL、`action` が索引にないこと | 問題なし |

## デモ項目

- マニュアル: 11 章（11.2 の停止の記録とロックの 2 行、停止中の図、11.6）、付録 B Q1-1、用語集
- 動画は撮っていない（表示だけの変更。計画の決定 (10)）

## 判断と学び

- 監査記録を業務の表示に使うときは「最新の 1 行」の意味に気をつける。停止の最新だけを引くと、後の再開を見落とす。打ち消す操作（再開）も同じ条件で引き、最新が打ち消しなら空にする
- 壊れた行を SQL の WHERE で落とすと、並びの意味が変わる（前の行が繰り上がる）。読んだ後に判定する
- 集約のリポジトリに照会の口を足すと、手書きの実装の数だけ直しが要る。照会が増える前に口を分けるかを決める

### ふりかえり（KPT）

- Keep: T-214（変異の対象に契約テストを入れる）と T-215（Gradle を流す担当を 1 つにする）を守り、変異 11 個が 1 回の実行で落ちた。2 つの整合性検証で、契約テスト・手書きの実装・ドメインモデル図の漏れを計画の段階で埋められた
- Problem:
  - 計画の状態の表に「停止 → 再開 → 記録のない停止」がなく、開発レビューで初めて見つかった
  - フォークが中身のない heredoc を 2 回流した（T-204 の違反。ファイルは変わっていない）
  - 変数名 `record` で SonarQube が 1 回落ちた（既にある決まり）
- Try:
  - T-216: 監査記録や履歴から「今の状態」を読む行は、打ち消す操作（再開・取消し）の後の場面を状態の表に必ず置く

### 既知の課題（後に持ち込む）

- 監査記録のリポジトリの照会の口の分離（議題 4。US-17 の開始準備で諮る）
- エラー要約の二重の読み上げ・chromium だけ、ACC-06、hidden の重複、見分けにくい名前（38c までの既知の課題）
- 確認の画面の要約の共通の部品（3 つ目で寄せる。38c）
- TOTP の解除の後の失効の失敗（#49）

## 次の Bolt

Bolt 38d（TOTP の解除・登録・再登録を本人のメールで知らせる。計画は承認済み）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-27 | 終了報告を承認した（議題 1〜4。議題 4 の照会の口の分離は US-17 の開始準備で諮る） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-10-27 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 38c2 計画](bolt_38c2_plan.md)、[リリース計画](release_plan.md)（W6 節の 38c2 の行）
- [Bolt 38c 終了報告](bolt_38c_report.md)、[Bolt 34d 終了報告](bolt_34d_report.md)（停止と再開、#50 の課題の出典）
- [UI 設計](../../design/cargo-tracker/ui_design.md)（S-20）、[データモデル](../../design/cargo-tracker/data_model.md)（索引）、[ユーザーマニュアル 11 章](../../manual/11-利用者の管理.md)
