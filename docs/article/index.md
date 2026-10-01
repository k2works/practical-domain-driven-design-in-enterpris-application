# 記事

本リポジトリの開発ガイドラインと、学習用の記事シリーズの一覧です。

## 開発ガイドライン：実践ドメイン駆動設計

本リポジトリの開発で従う DDD の考え方と実装方針です。cargo-tracker の分析・設計・実装では、次の 3 章を開発ガイドラインとして参照します。

| 章 | 内容 | 主に使う工程 |
|------|------|------|
| [第 1 章：ドメイン駆動設計](./01-ddd-fundamentals.md) | 問題空間と解決空間、戦略的設計（サブドメイン・境界づけられたコンテキスト）と戦術的設計（集約・エンティティ・値オブジェクト・ドメインイベント）の概念 | アーキテクチャ設計、ドメインモデル設計 |
| [第 2 章：Cargo Tracker のドメインモデル](./02-cargo-domain-model.md) | Cargo Tracker を題材にした、コアドメイン、境界づけられたコンテキスト、ドメインモデル、コマンド・クエリ・イベント・サガの導出 | ドメインモデル設計、データモデル設計 |
| [第 3 章：Spring Platform × モジュラーモノリス](./03-spring-modular-monolith.md) | Spring Platform による Cargo Tracker のモジュラーモノリス実装 | 技術スタック選定、バックエンド実装 |

## 学習用記事シリーズ（外部サイト）

外部シリーズの記事は各リポジトリで継続的に追加・更新されているため、本文はここに置かず、公開サイトへのリンクのみを掲載しています。

| シリーズ | テーマ | 説明 |
|------|------|------|
| [テスト駆動開発から始めるプログラミング入門](https://k2works.github.io/getting-started-tdd/article/) | TDD を軸にした言語入門 | 共通題材で Red-Green-Refactor を体験し、言語ごとの設計思想を比較できます。 |
| [アルゴリズムからはじめるプログラミング入門](https://k2works.github.io/getting-started-algorithm/article/) | アルゴリズムを軸にした言語入門 | 基本アルゴリズム、配列、探索、スタックとキュー、再帰、ソート、文字列処理、リスト、木構造までを TDD で実装しながら、多言語で比較して学べます。 |
| [デザインパターンからはじめるプログラミング入門](https://k2works.github.io/getting-started-design-pattern/article/) | デザインパターンを軸にした言語入門 | Template Method・Strategy・Observer から Builder・Interpreter までの GoF パターンを TDD で実装しながら、パターンを支える設計原則とともに多言語で比較して学べます。 |
| [機械学習から始めるプログラミング入門](https://k2works.github.io/getting-started-machine-learning/article/getting-start-ml/) | 機械学習を軸にした言語入門 | 機械学習の題材をテストファーストで実装しながら、データ前処理、CI/CD、評価指標、モデル選択、API 設計まで多言語で学べます。 |
| [Grokking Functional Programming](https://k2works.github.io/grokkingfp-excersice/) | 関数型プログラミング | 純粋関数、イミュータブルデータ、Option/Either、IO、並行処理まで体系的に学べます。 |
| [Grokking Concurrency](https://k2works.github.io/grokking-concurrency-exercise/) | 並行処理プログラミング | スレッド、同期、非同期、ノンブロッキング I/O、分散並列処理を多言語で比較できます。 |
| [Grokking Machine Learning](https://k2works.github.io/grokking-machine-learning-excersice/grokking-machine-learning/) | 機械学習 | 線形回帰から決定木、ニューラルネットワーク、SVM、アンサンブル学習まで、機械学習アルゴリズムを Python・Kotlin・F# で自前実装しながら学べます。 |
| [関数型デザイン - 原則、パターン、実践](https://k2works.github.io/functional-desgin-ppp/article/) | 関数型デザインパターン | OOP のデザインパターンを関数型パラダイムでどう表現するかを実践的に学べます。 |
| [From Objects to Functions 演習](https://k2works.github.io/from-objects-to-functions-excersice/article/zettai/) | 関数型アーキテクチャ | To-Do アプリ Zettai を題材に、HTTP、ドメインモデリング、イベントソーシング、関数型エラーハンドリング、ファンクタ・モナドによる射影と永続化までを Kotlin・なでしこ3・Rust で実装しながら学べます。 |
| [実践データベース設計：基幹業務システム編](https://k2works.github.io/practical-database-design/article/) | データベース設計 | 販売管理・財務会計・生産管理の基幹業務システムを題材に、業務フローとデータモデルを体系的に学べます。 |
| [Docker/Kubernetes 実践コンテナ解説](https://k2works.github.io/getting-started-docker-kubernetes/article/) | コンテナ・オーケストレーション | コンテナの基礎から複数コンテナ構成、Kubernetes、継続的デリバリー、ケーススタディまで実践的に学べます。 |
