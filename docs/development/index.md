# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## プロジェクト一覧

| プロジェクト | 概要 | 状況 |
| :--- | :--- | :--- |
| [cargo-tracker](cargo-tracker/index.md) | A 社国際貨物輸送管理システム | W1 完了（Bolt 1〜8、6 SP）、W2 完了（Bolt 9〜14、7 SP）、W3 完了（Bolt 15〜21、11 SP）、W4 完了（Bolt 22〜28、11 SP）。Release 0.1 は 35 / 35 SP で完了（2026-10-10、[リリース完了報告書](cargo-tracker/release_report-0_1.md)）。次は Release 1.0 の W5 |

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
