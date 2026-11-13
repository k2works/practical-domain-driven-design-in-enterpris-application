---
type: Plan
title: "Bolt 40-1 終了報告 - W7 週全体レビューの返済（M1・M3・M6・M7）"
tags: [plan]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-13T03:10:00Z }
---

# Bolt 40-1 終了報告 - W7 週全体レビューの返済（M1・M3・M6・M7）

## 承認の議題（T-36。先に確かめてほしいこと）

| # | 確かめてほしいこと | 推奨 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **M1 の新しい拒否**: `TrackingRecord` の組み立てが、(a) 当初の到着予定が予定の最後の到着と違う、(b) 現在状態の根拠の実績番号が実績にない、を `IllegalArgumentException` で拒む。製品コードの振る舞いが変わるのはここだけ。既存の保存・往復のテストはすべて緑だった | 承認 | 成果のステップ 2 |
| 2 | **行 60 のテストを書き換えた**: `MyBatisTrackingRecordRepositoryIntegrationTest` の行 60 は、不変条件の破れた集約を作ってリポジトリが「競合」に変えることを確かめていた。M1 で集約が先に拒むので、この経路は届かなくなった。テストは「集約が組み立てを拒み、表は変わらない」に替えた。**リポジトリの「外部キー違反を競合に変える」写しは、今は届かないコード**になった。残すか消すかは別に諮る（技術的負債に足す） | 承認（残す／消すは別） | 申し送り |
| 3 | **CP-9（人の回答）**: `ExternalMilestone` も M3 で同時に直した（推奨は「直さない」だった）。入力の変換を `ExternalMilestoneInput` に出し、値オブジェクトは必須の値と出典の種類の不変条件だけを持つ。`InvalidEventValueException` は `ExternalMilestone` に残した（コンストラクターの不変条件が投げるため） | 承認 | ステップ 3 |
| 4 | **VoyageArrival の変更**: `voyageNumberOf`・`portCallsOf`・`orderOf`・`MIN_PORT_CALLS`・例外の型を `VoyageArrivalInput`（`application.internal.eventhandlers`、package-private）へ移した。`VoyagePortCall.of` は null の変換だけにした。旧 `VoyageArrivalTest` は `VoyageArrivalInputTest` に移した | 承認 | ステップ 3 |
| 5 | **規律の違反（私の側）**: (a) `python3 -I -` が標準入力を待って止まり、`TaskStop` で止めた。(b) `cat > /dev/null <<EOF` という禁止の形を書いた（空の命令で実害なし）。(c) **作業の取り違え**: 「W8 を release_plan に足す」を一度実施・コミットした後、あなたの「キャンセル」で取り消す際、`git revert` が失敗したのに続けて `git commit --amend` を実行し、W8 の追加のコミットを「Revert」の名前にしてしまった（さらにステージ済みの別の削除も混ざった）。未 push のコミットだったので `reset --soft` と `restore` で W8 の文書だけを戻し、削除は M3 のコミットに正しく入れた。W8 の節は `release_plan.md` にない（確認済み）。以後は `&&` でつなぎ、失敗したら次へ進まない | 承認 | — |
| 6 | **流していないもの**: `uiTest`・`documentationTest`（テストと構造の内部の変更で画面は触れない。CP-2）、CI・デモ環境の配備（push していない） | 承認 | — |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 40-1 回（W8 の 2 本目） |
| 期間 | 2026-11-13 |
| 種別・SP | 技術・SP 0 |
| 見積り／実績 | 見積り 210 分（CP-9 で M3 が増えた分は見積りに入れていない）。**実績は約 120 分（概算。時刻を厳密には記録していない）。差は約 −90 分**。壁時計は `check` 8 分 24 秒・Sonar（2 回）を含む |
| GitHub | 変えていない |
| 計画 | [Bolt 40-1 計画](bolt_40-1_plan.md) |

## 成果

| ステップ | 結果 | コミット |
| :--- | :--- | :--- |
| 2. M1 | Red（拒む 2 件が落ちた）→ コンストラクターに 2 つの検査 → Green。既存 2 件（根拠のない形の fixture の番号 0、行 60）は新しい契約に合わせて直した | `be1a5870d` |
| 3. M3 | `VoyageArrivalInput`・`ExternalMilestoneInput` を作り、3 つの値オブジェクトの欠けの検査と変換を移した。`tracking` と ArchUnit のテストが緑 | `e25660f5f` |
| 4. M6 | `wrongCombinations` を区分 × 列（持ってはいけない列を足す、必須の列を抜く）の全組から作る表にし、正しい組も区分ごとに確かめる。43 件が緑 | M6 のコミット |
| 5. M7 | 失敗した移し替えの後の形を、どちらの DB でも履歴に成功行がないことで、PostgreSQL では旧 `threshold` 列だけが残り新しい列がないことでも確かめる。H2・PostgreSQL の 6 件が緑 | M7 のコミット |
| 6. 品質ゲート | 下の表 | Sonar の指摘のコミット |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check`（`uiTest` は流さない。CP-2） | BUILD SUCCESSFUL（8 分 24 秒） |
| SonarQube（`sonar-local:check`） | **1 回目は FAIL**（新しい違反 3 件: テストのラムダに例外を投げ得る呼びが複数）。引数を先に作る・補助メソッドに出すで直し、**2 回目は PASS**（新しいコードのカバレッジ 96.3%、重複 0.017%、違反 0） |

## 完了条件

- [x] M1 の拒否が単体で緑、既存の組み立て経路が緑
- [x] M3 の移し替え後も DE-12・DE-13 の既存テストが緑
- [x] M6 の表と M7 の確認が緑
- [x] `check` と SonarQube が緑
- [x] 見積り／実績／差を書いた

## 申し送り

- 技術的負債: `MyBatisTrackingRecordRepository` の「外部キー違反を競合に変える」写しが届かなくなった（議題 2）。残すか消すかを諮る。
- `ExternalMilestone.InvalidEventValueException` は値オブジェクトに残っている。`RejectedMilestone` などでも使うなら、後で置き場所を見直す。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-11-13 | 初版作成（承認待ち） | anthropic/claude-sonnet-5-5 |
