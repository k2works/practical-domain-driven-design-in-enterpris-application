---
name: cargo-tracker-review-points
description: cargo-tracker のドキュメント・画面レビューで繰り返し見るべき論点（用語の併記、報告書の数値・時刻の検証方法、手順書の環境依存値、承認済み文書の書き換え、決定と画面の食い違い）。Bolt 4（2026-10-02）時点
metadata:
  type: project
---

Bolt 1〜3（2026-10-01〜02）のレビューで見つかった、以降の Bolt でも再発しやすい論点。

- 顧客向け画面は「見積依頼（輸送要求）」を併記する決まり（ui_design の画面オブジェクト節）。テンプレートでは「輸送要求 ID」だけが出がち
- D-4 の UUID 表示は Bolt 4 で解消（完了画面・URL・KPI 一覧とも業務番号）。以後は「業務番号」欄に「版 1」まで入る表記と、ui_design の画面ごとの行（S-22 など）が実装に追従しているかを見る
- 計画からの変更は「結果」行に追記されるが、計画本文の元の記述（例: 確認ポイント 4 の NOT NULL）は直されず残りがち。両方を突き合わせる
- 形式の誤りの文言（「数字で入力してください」）がドメインの規則（1 以上の整数・小数 3 桁）とずれやすい
- 場所（UN/LOCODE）は大文字だけを受け付ける。小文字入力の扱いが UI 側で抜けやすい
- 報告書のテスト件数は `apps/cargo-tracker/build/test-results/{test,uiTest}/*.xml` の tests・skipped 属性の合計で突き合わせる（Bolt 3 終了時 test 75 件うち skip 2、uiTest 17 件うち skip 3。uiTest は ArchUnit 等も混ざる既知課題）。CI は `gh run list --workflow cargo-tracker-ci.yml` と `gh run view <id> --json jobs`、時刻は `git log --date=iso` で照合する。報告書の「期間」の終了時刻・所要時間がコミット時刻と合わないことが続く（Bolt 3、Bolt 4 は約 4 時間 50 分と記載、実際は 11:19〜15:55 で 4 時間 36 分）
- 手順書に作者のローカル `.env` の値が混入しやすい（Bolt 3 で SonarQube の URL が 9001。スクリプトの既定は 9000、LOCAL_SONAR_PORT / SONAR_HOST_URL で変わる）。版（Node.js など）は tech_stack に根拠があるか確かめる
- `sonar-local:gate`/`check` は Bolt 2 レビュー後、OK 以外で失敗するよう直り、scan は `sonar.qualitygate.wait=true`（2026-10-02 確認）
- status: stable・verified 済みの文書（ADR、設計、要件）を AI が決定の反映で書き換えるとき、generated だけ更新して verified や更新履歴が付かないことがある。承認待ちの印（履歴行・log）があるかを見る
- 計画・release_plan のチェックボックスが、終了報告の人の承認前に [x] にされがち。また「開発基盤」など上位項目が [ ] のまま残る
- apps/cargo-tracker/README.md と docs/operation/cargo-tracker/application_development_setup.md は Bolt 3 で作成済み。運用スクリプトやビルドを変えたコミットでは手順書の差分を確認する

**Why:** AI が自律実行で作る成果物は、設計書の横断ルールを骨格の段階で落としやすく、報告書もコードの後追いになりやすい。
**How to apply:** Bolt のレビューではまずこれらを確認し、解消済みなら本メモを更新・削除する。関連: [[cargo-tracker-doc-conventions]]
