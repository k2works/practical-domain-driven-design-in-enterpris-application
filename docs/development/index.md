# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## プロジェクト一覧

| プロジェクト | 概要 | 状況 |
| :--- | :--- | :--- |
| [cargo-tracker](cargo-tracker/index.md) | A 社国際貨物輸送管理システム | W1 完了（Bolt 1〜8、6 SP）、W2 完了（Bolt 9〜14、7 SP）、W3 完了（Bolt 15〜21、11 SP）、W4 完了（Bolt 22〜28、11 SP）。Release 0.1 は 35 / 35 SP で完了（2026-10-10、[リリース完了報告書](cargo-tracker/release_report-0_1.md)）。Release 1.0 の W5 完了（Bolt 29〜35e、9 SP。2026-10-20 に締めた）、W6 完了（Bolt 36〜38g、12 SP。2026-11-02 に締めた。[W6 完了報告書](cargo-tracker/iteration_report-W6.md)）。W7 完了（Bolt 38h・39〜39i の 39 本、10 SP。2026-11-13 に締めの作業を終えた。[W7 完了報告書](cargo-tracker/iteration_report-W7.md)）。W8 完了（Bolt 40〜40-17、8 SP。[W8 完了報告書](cargo-tracker/iteration_report-W8.md)）、W9 完了（Bolt 41〜48-1、15 SP。[W9 完了報告書](cargo-tracker/iteration_report-W9.md)）、W10 一部完了（運用準備、SP 0。[W10 完了報告書](cargo-tracker/iteration_report-W10.md)）。Release 1.0 は 54 / 54 SP で機能は揃ったが、リリース条件の一部が未達で、タグは未（[リリース完了報告書](cargo-tracker/release_report-1_0.md)）。次は Release 1.1 の W11（[W11 計画](cargo-tracker/iteration_plan-W11.md)は承認待ち） |

## デモ環境

| プロジェクト | URL | 内容 |
| :--- | :--- | :--- |
| cargo-tracker | <https://cargo-tracker-mono-demo-883bf0b92807.herokuapp.com/> | dev プロファイルのまま Heroku で動かす公開のデモ環境。ログインの画面の「開発用の利用者でログイン」から、荷主担当者・営業担当者でログインできる。本物のデータを入れない。データは再起動で初期状態に戻る（[ADR-013](../adr/cargo-tracker/013-heroku-demo-environment.md)、[Heroku デモ環境セットアップ手順書](../operation/cargo-tracker/heroku_demo_setup.md)） |

## 補足

- 実ドキュメントを追加したら、この一覧と `docs/index.md` を更新します。
- テンプレートは次を利用できます。
  - [template/リリース計画.md](../template/リリース計画.md)
  - [template/イテレーション計画.md](../template/イテレーション計画.md)
  - [template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)
  - [template/リリース完了報告書.md](../template/リリース完了報告書.md)
