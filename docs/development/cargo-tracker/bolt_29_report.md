---
type: Report
title: "Bolt 29 終了報告 - 画面の層のステップ定義のキー操作の部品を 1 か所に集める（#45）"
description: "29 回目の Bolt の終了報告。10 のクラスに 6 通りで写された tabUntilFocused と 2 つの tabUntilGroup を BrowserSession に集め、uiTest の 80 本が集める前と同じく通ることを確かめた結果。開発レビューで失敗の経路を読めるものに直したこと、後片付けのフックを動かさない判断、品質ゲート、ふりかえり、承認の議題を記す。"
tags: [development,bolt-report]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-11T02:42:38Z }
---

# Bolt 29 終了報告 - 画面の層のステップ定義のキー操作の部品を 1 か所に集める（#45）

## 承認の議題（T-36。先に確かめてほしいこと）

human:kakimomokuri の `/goal Bolt29` の指示で、確認ポイントはすべて推奨のとおりとし、途中のゲートは止めずに進めた（Bolt 22・28 の前例）。

| # | 確かめてほしいこと | AI の判断の根拠 | 見る場所 |
| :--- | :--- | :--- | :--- |
| 1 | **計画からの変更: 失敗の経路を変えた**。計画は写しの判定（`target.evaluate`）をそのまま集める案だったが、開発レビューの指摘で「目的の要素が現れるのを待ってから（`assertThat(target).isAttached()`）、待たない判定（`target.and(:focus).count()`）で押し、最後に `isFocused` で確かめる」形にした。画面にない要素を指定すると、写しの時代は 30 秒の `TimeoutError` だったのが、5 秒で「Locator expected to be attached」の失敗になる | 通る経路の押す回数とフォーカスの位置は変わらない（35 か所の呼び出しの直前の操作を開発レビューで読み、目的の要素にすでにフォーカスがある呼び出しは 0）。`uiTest` の 80 本が集める前と同じく通った。直す場所が 1 つになった今がいちばん安い | `BrowserSession.tabUntilFocused`、ステップ 2 の結果 |
| 2 | 判定を待たない形に変えた最初の案（`count()` だけ）は、画面の遷移の直後に要素が描かれる前に Tab を押し始める危険があると AI が自分で気づき、`isAttached()` で待つ形に直してから流した | 元の `evaluate` は要素が現れるまで待っていた。その待ちを `isAttached()` で残した | 同上 |
| 3 | **SonarQube の Quality Gate は PASS**。ただし Code Smell 1 件（MAJOR。`AcceptanceCriteriaCoverageTest.java:40` の正規表現のバックトラック）が残る。Bolt 28 で作ったコードで、Bolt 29 では変えていない。初版では「この環境では回せない」と書いたが、過去の履歴（Bolt 19 の T-52、手順書の 9 節）を確かめずに出した誤りで、人の指摘で手順書のとおり `sonar-local:start` → トークンの作成 → `scan` → `check` で回した | Code Smell は Bolt 29 の範囲外。Bolt 29b の開発レビューのときに直すか、W5 の締めで扱いを決める | 品質ゲート |
| 4 | 依存の取得で Maven Central が 429（Too Many Requests）を返し続けたので、`--max-workers=1` と 15 秒の間隔で再試行して流した。手順書のタスクの外の操作はしていない（Gradle のタスクだけ） | 一時的な流量制限で、同じ版の依存を取り直しただけ | ステップ 1 の結果 |

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Bolt | 第 29 回（W5 の最初の Bolt。Release 1.0 の最初の Bolt） |
| 期間 | 2026-10-11 01:13（計画の初版 `ea9836f`）〜 02:20 頃（本報告の初版）。着手の指示（`/goal`）は 01:25 頃（`f667033`）。`uiTest` の 1 回が約 13 分で、3 回（基準・レビュー前・レビュー後）と T-88 の確かめ 2 回を流した |
| 対象 | 画面の層のステップ定義（`src/test/java/com/example/cargotracker/ui`）。本番のコードは変えていない |
| GitHub | [#45](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/45)。承認の後に閉じる |
| 計画 | [Bolt 29 計画](bolt_29_plan.md) |

## 成果

### ステップの結果

| ステップ | 結果 | コミット | 備考 |
| :--- | :--- | :--- | :--- |
| 1. 集める前の基準 | `uiTest` 80 本、失敗 0、実行時間の合計 801 秒（実行時間のある方の XML。T-79） | — | Maven Central の 429 を再試行で越えた（議題 4） |
| 2. 部品を集めて置き換える | `tabUntilFocused` の写し 10 と `tabUntilGroup` の写し 2 を消し、`BrowserSession` に 1 つずつ置いた。呼び出し 35 か所（計画の「33 か所」は数え違い。`tabUntilFocused` 33・`tabUntilGroup` 2）を `browser.` 経由にした。開発レビューの後の `uiTest` は 80 本、失敗 0、シナリオの名前も集める前と一致、実行時間 780 秒。`check` 1608 件、失敗 0 | `71789d8` | 議題 1・2 |
| 3. 後片付けのフックの判断と文書 | フックは `RoutingUiSteps` から動かさない（確認ポイント 6）。理由を Javadoc に 1 文足した。test_strategy.md の「置き場所とステップ定義」に、キー操作の部品の置き場所と `tabTo` を各クラスに残す理由を書いた | `f667033`、`71789d8` | — |
| 4. 開発レビューと終了報告 | プログラマーとテスターの観点で 1 回（推奨 2・任意 3）。推奨 2 件と任意 1 件を反映した | `71789d8`、本報告 | 「開発レビュー」 |

### 作ったもの

- `BrowserSession.tabUntilFocused(Locator)`: 目的の要素が現れるのを待ち、フォーカスがなければ Tab を押す（上限 80）。届かなければ `assertThat(target).isFocused()` で失敗する
- `BrowserSession.tabUntilGroup(String)`: ラジオボタンのグループの入力欄に届くまで Tab を押す（上限 60）
- 11 のステップ定義のクラスから写しを消した（差分は 13 ファイル、+74・−164 行）

### デモ項目

なし（テストのコードのリファクタリング。画面と振る舞いを変えない。受入動画も撮っていない。計画の確認ポイント 9）。

## 指標

| 指標 | 値 |
| :--- | :--- |
| テスト | 新しいテストなし。`uiTest` 80 本（変化なし）、`check` 1608 件 |
| 承認ゲートの通過 | 計画は `/goal` の指示で推奨のとおり。途中のゲートは止めなかった（議題） |
| 人の変更依頼 | 0 |
| リードタイム | 約 1 時間 7 分（計画の目安 1.5〜2 時間の内。ほとんどが `uiTest` の待ち） |

## 品質ゲート

| ゲート | 結果 |
| :--- | :--- |
| `check`（ModularityTest・ArchUnit・AT-01〜06・Checkstyle・SpotBugs・Spotless・`documentationTest` を含む） | 通過（1608 件、失敗 0） |
| `uiTest`（axe-core を含む） | 通過（80 本、失敗 0。集める前と後でシナリオの名前が一致。開発レビューの対応の後にも流した。T-70） |
| T-88（集めた部品が失敗を報せる） | 確かめた。画面にない欄を一時的に指定し、スイートを `@US-02` に絞って流すと落ちた（対応の前は 30 秒の `TimeoutError`、対応の後は 5 秒の「Locator expected to be attached」）。確かめた後に戻した |
| CI | `71789d8` の cargo-tracker CI（`check`・`ui`）は通過（run 191） |
| SonarQube（ローカル） | Quality Gate PASS（`sonar-local:check`。手順書の 9 節のとおり起動）。カバレッジ 95.1%、重複 0.1%、Bug 0、Vulnerability 0、Security Hotspot 0、Code Smell 1（Bolt 28 のコード。議題 3） |
| `okf:check` | ERROR 0 |

## 仮説の結論

| # | 仮説 | 結論 | 根拠 |
| :--- | :--- | :--- | :--- |
| H1 | 6 通りの違いを 1 つにそろえても、どのシナリオの結果も変わらない | 支持 | `uiTest` の 80 本が集める前と同じく通った。開発レビューで、35 か所の呼び出しの直前の操作（`body.focus()`、`type`、Space、段階の移動の Enter、ほかの要素への移動）を読み、目的の要素にすでにフォーカスがある呼び出しがないことを確かめた |
| H2 | 上限を 80 にそろえても、`uiTest` の実行時間は目立って延びない | 支持 | 801 秒 → 780 秒（測り方のぶれの範囲） |

## 開発レビュー

プログラマーとテスターの観点で、置き換えた後の差分を読んだ（`developing-review`。画面を変えないのでインタラクションデザイナー・テクニカルライターの観点は外した）。

| # | 重さ | 内容 | 対応 |
| :--- | :--- | :--- | :--- |
| 1 | 推奨 | ステップ 3 の「フックを動かさない理由を Javadoc に残す」が差分にない | 反映した（`RoutingUiSteps`） |
| 2 | 推奨 | 画面にない要素で `evaluate` が 30 秒の `TimeoutError` になり、`isFocused` の失敗の文言に届かない。直す場所が 1 つになった今がいちばん安い | 反映した（議題 1・2） |
| 3 | 任意 | 上限は「近くにある」ことを暗に確かめる働きもあったが、順序を確かめるのは `tabTo` の役目 | 上限は順序の仕様ではないと Javadoc に書いた |
| 4 | 任意 | 計画の呼び出しの数「33 か所」は 35 か所 | 本報告で直した |
| 5 | 任意 | `TrackingUiSteps.chooseRadio` はグループの先頭へ Tab で入るので、グループの中ですでにどれかが選ばれていると先頭に届かない（写しの時代から同じ。いまは初期値が未選択） | 範囲外。初期値を持つグループを作る Bolt で直す（既知の課題） |

## 判断と学び

### 既知の課題（後の Bolt に持ち込む）

- `chooseRadio` の先頭への移動（開発レビュー 5）。初期値を持つラジオボタンのグループを作る Bolt で直す。Issue にはしない（顕在化する条件が具体的な画面の追加に結び付いており、その Bolt の計画の確認ポイントで拾える。T-89 の「後に回したもの」の数え方の例外として W5 の締めで人に諮る）
- `tabTo` の写し（`QuotationUiSteps` は `Locator`、`SubmitTransportRequestUiSteps` は `String`）は、確かめる順序が画面ごとに違う別の部品として残した（計画の確認ポイント 3）

### ふりかえり（KPT）

- Keep
  - 集める前に `uiTest` の基準を取り、シナリオの名前の集合まで比べたので、「振る舞いを変えない」を数で示せた
  - T-88 の確かめ（届かない要素を一時的に指定する）で、失敗の経路が 30 秒の `TimeoutError` だと分かり、開発レビューの指摘と合わせて直せた
- Problem
  - 計画で呼び出しの数を数え違えた（33 と 35）。`grep` の条件から `tabUntilGroup` を外していた
  - 開発レビューの指摘に合わせて判定を `count()` に変えたとき、要素を待たなくなる副作用に、流す前まで気づかなかった（流す前に自分で気づいて直した）
  - `pkill -f` のパターンが自分のシェルにも当たり、編集が当たらなかった
  - Maven Central の 429 で、1 回の `uiTest` の前に数分の再試行が要った
  - SonarQube のローカル検査を「この環境では回せない」と、過去の履歴と手順書（Bolt 19 の T-52、`application_development_setup.md` の 9 節）を確かめずに判断した。T-56（環境の操作の前に手順書を読む）を守らなかった。人の指摘で手順書のとおりに回し、PASS を確かめた
- Try
  - T-91: 自動待ちのある API（`evaluate`・`isVisible` など）を待たない API（`count` など）に置き換えるときは、待っていたものを別の手段で残すかを先に決める
  - T-92: プロセスを止めるときは `pkill -f` を使わず、ツールのタスクの停止か PID で止める
  - T-93: 依存の取得が 429 になる環境では、最初から `--max-workers=1` と再試行のループで流す
  - T-94: 「この環境ではできない」と書く前に、手順書（`docs/operation/`）と過去の終了報告で同じ操作の履歴を探す（T-56 の再確認）

## 次の Bolt

Bolt 29b（[#46](https://github.com/k2works/practical-domain-driven-design-in-enterpris-application/issues/46)）: マニュアルの作成で見つけた画面の文言と表示の課題を直し、マニュアルの該当の節とキャプチャを直す。開発レビューにマニュアルを書く立場の観点を入れる（T-90）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-10-11 | 人の指摘（SonarQube は過去に同様の環境で回せた）で、手順書のとおり SonarQube を起動して `sonar-local:check` を流し、議題 3・品質ゲート・ふりかえりを書き直した（承認待ち） | anthropic/claude-opus-5-5、指摘 human:kakimomokuri |
| 2026-10-11 | 初版作成（承認待ち） | anthropic/claude-opus-5-5 |

## 関連ドキュメント

- [Bolt 29 計画](bolt_29_plan.md)
- [リリース計画](release_plan.md)（W5）
- [テスト戦略](../../design/cargo-tracker/test_strategy.md)（置き場所とステップ定義）
- [Bolt 28 終了報告](bolt_28_report.md)、[Bolt 27b 終了報告](bolt_27b_report.md)
