---
type: Plan
title: "Bolt 39e2 終了報告 - 確定のときに情報版と区間の予定の組で照合し、見本の航海を分ける"
tags: [plan]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-11-09T17:41:01Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-09T17:41:02Z }
---

# Bolt 39e2 終了報告 - 確定のときに情報版と区間の予定の組で照合し、見本の航海を分ける

## 承認の議題（T-36。先に確かめてほしいこと）

確定のときの照合（R-INV-03）を、情報版だけでなく情報版と区間の予定の組にしました。同じ情報版のまま予定が変わると確定できません。すべての判断を推奨のとおり承認として記録しています（Recommend 承認の指示による）。業務の判断を含むものを先頭に置きます。

| # | 確かめてほしいこと | 見る場所 |
| :--- | :--- | :--- |
| 1 | **【業務の判断】(s) と【前の決定の変更】(1)**（台帳 D-106。仮）: 同じ情報版でも区間の予定が変わると確定を拒否する。Bolt 37c・37d・37d2 の「情報版の照合」を、情報版と区間の予定の組の照合に改めた。元の予定に戻れば再び確定できる。人は個別に見ていないので、業務に合わなければここで見直す | [Bolt 39e2 計画](bolt_39e2_plan.md)、domain_model の「Bolt 39e2 で決めたこと」、マニュアル 8 章 |
| 2 | **【前の決定の変更】(4)**: S-07 の注を「情報版、区間の予定、取得から経った時間」に直し、キャプチャ `08-confirmation` を撮り直した | S-07、マニュアル 8 章 |
| 3 | **見本の航海**: 開発データの出発済みの見本 TR-2026-0911・0912 の航海を `DEMO-101` から `DEMO-901` に分けた（`V20261109130900`。`db/dev-data` のみで、ステージングと本番には行がない） | data_model、heroku_demo_setup |
| 4 | **理由の名前**: 拒否の理由 `INFO_VERSION_CHANGED` は 39e2 の後は意味より狭いが、外に見える名前なので変えていない。W10 の命名の整理で諮る | `RouteConfirmationRejectionReason` |
| 5 | **デモ環境の見え方は未確認**: CI の配備の成功までを確かめた。配備先の画面で 0911・0912 が `DEMO-901` で見えることは開いて確かめていない（`DevSampleDataSmokeTest` で同じデータを確かめている） | デモ環境 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 39e2 回（W7 の 24 本目） |
| 期間 | 2026-11-10（計画の承認 `f439503c6` から終了報告まで） |
| 対象 | `Voyage.matchesLeg`、`RoutingCase.anyLegChanged`、確定と評価の照合、見本の航海 `DEMO-901`、S-07 の注、マニュアル 8 章とキャプチャ 1 枚 |
| GitHub | [#18](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/18)（US-14。クローズは 39h） |
| SP | 0 |
| ブランチ | develop |
| 計画 | [Bolt 39e2 計画](bolt_39e2_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 1. 設計の文書 | domain_model・data_model・user_story・system_usecase・ui_design・test_strategy・ADR-004・heroku_demo_setup・release_plan（39e2・39f・39h の行、D-106） | `f29c39ff9` |
| 2. 受入シナリオとテストの Red | 同じ情報版の採用の後でも確定が成功する理由で受入シナリオが落ち、単体は骨組みの `matchesLeg` で落ちた。Red を develop に積んでいない（T-254） | `c4ad4d273` |
| 3. R-INV-03 の照合 | `Voyage.matchesLeg`、`anyLegChanged`。`legsWithChangedInfo` を消した。ほかに落ちたテストは 0 本 | `c4ad4d273` |
| 4. 見本の航海 | `V20261109130900__split_dev_sample_departed_voyage.sql` | `c4ad4d273` |
| 5. マニュアルと S-07 の注 | 8 章 :119・:138、`confirmation.html`、`08-confirmation.png`。実装の文と 12 項目を一字ずつ突き合わせて一致（T-197・T-251） | `c4ad4d273`、`20e997652` |
| 6. 受入シナリオの緑 | `adopted_voyage_reflected_ui.feature` の 3 シナリオが緑、axe 違反 0 | `c4ad4d273` |
| 7. 品質ゲートと開発レビュー | 下の「品質ゲート」「開発レビューの指摘と扱い」 | `42cb2ac83` |
| 8. 終了報告 | この報告 | この報告のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| テスト | `check` の `test` 4,929 件、失敗 0（Bolt 39e の 4,901 件から +28）。`uiTest` は 215 シナリオ、失敗 0（JUnit の XML では 430） |
| 変異 | 計画の 10 個を守りの場所ごとに分け、足した分を含めて 40 個。V22c（揚地の港の最初の寄港だけを見る）は生き残ったのでテストを 2 件足して落とした |
| axe | 違反 0 |
| SonarQube | Quality Gate が PASS（新しいコードのカバレッジ 96.0%、重複 0.02%、違反 0、ホットスポット 0） |
| spotbugsMain・checkstyleMain | 指摘なし |
| `okf:check` | ERROR 0 |
| CI | `42cb2ac83` で `check`・`ui`・`deploy-demo` が成功した（run 42662990550） |

### 開発レビューの指摘と扱い

1 視点（アーキテクト・テスター・プログラマー・ユーザー代表・UI/UX・テクニカルライター・セキュリティを兼ねる）。高 0、中 1、低 8。本体の振る舞いの欠陥はなかった。

| # | 指摘 | 扱い |
| :--- | :--- | :--- |
| 1 | 中: 揚地に 2 度寄る航海が未検証（V22c が生存） | 直した（`VoyageTest` に 2 件） |
| — | 低: テストの名前「区間の情報版が変わらなければ…」が狭い、`java.util.List.of` の完全修飾、古い注 2 か所、行 11 を名乗る記述が feature にない | 直した |
| — | 低: 積地・揚地の組の探し方が `RouteCandidateFinder.refresh` と `Voyage.matchesLeg` に重複している | 39f（寄港の位置を持たせるときに一緒に見る） |
| — | 低: 採用した航海で予定が同じなら確定できることの画面通しの確かめがない | 39f（配信までの統合テストで） |
| — | 低: 同じ版のまま拒否されると、どの区間が変わったか S-06 で分からない | 39f2（古くなった候補と一緒に） |
| — | 低: 理由の名 `INFO_VERSION_CHANGED` が意味より狭い | W10 |

## 計画から変えたこと

- 計画に名のない注 `ConfirmedRouteEvaluationOutcome` :29 も直した（完了条件に当たる）
- 変異を 40 個に増やし、行 1 に揚地に 2 度寄る航海の 2 件を足した

## デモ項目

- 動画: なし（39e2 は動画の対象外。W7 のデモの動画は 39h で撮る）
- マニュアル: 8 章を直し、キャプチャ `08-confirmation` を撮り直した

## 判断と学び

- 名前が「情報版」でも、守りたいのは「確定の根拠が今も成り立っていること」だった。識別子で比べる守りは、識別子が変わらずに中身が変わる入力が現れたときに破れる
- 変異を守りの場所ごとに分けると、積地と揚地の対称の片方だけが未検証であることが見つかる

### ふりかえり（KPT）

- Keep: 実装の担当と品質ゲートの担当の 2 回で、計画の完了条件の検索語を実際に流して 0 件を確かめた
- Problem: 同じ作業ツリーで変異テストが走る間に次の計画の検証を流したので、検証の担当がコードの中身の変化に気づいて HEAD で読み直す手間が出た
- Try: 変異テストと同時に検証を流すときは、最初から `git show HEAD:` で読むよう担当に書く

### 既知の課題（後に持ち込む）

- 39f: 組の探し方の重複、配信までの統合テスト
- 39f2: どの区間が変わったかの表示
- W10: `INFO_VERSION_CHANGED` の名前

## 次の Bolt

Bolt 39f（航海の更新から確定した経路を評価する入口）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-10 | 推奨のとおり承認として記録した（議題 1〜5。Recommend 承認の指示による。human:kakimomokuri） | anthropic/claude-opus-5-5、承認 human:kakimomokuri |
| 2026-11-10 | 初版作成 | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 39e2 計画](bolt_39e2_plan.md)
- [Bolt 39e 終了報告](bolt_39e_report.md)
- [ドメインモデル](../../design/cargo-tracker/domain_model.md)、[データモデル](../../design/cargo-tracker/data_model.md)、[ユーザーマニュアル 8 章](../../manual/08-経路設計.md)
