/**
 * サガ。予約サガ（業務の流れ）の状態（処理中・完了・失敗・有人確認要）とリポジトリ（送信ポート）を置き、状態を予約の 1 か所に持つ
 * （開発ガイドライン第 3 章の {@code application.sagas}、ADR-015）。サガを進める処理（追跡の開始の結果の受け取り、処理中の滞留の
 * 検出）もここに足す（Bolt 25・W8）。リポジトリの実装（{@code infrastructure.persistence}）がここを参照するのは、層の規則 D-5 の
 * 例外（{@code LayerArchitectureTest}）。
 */
package com.example.cargotracker.booking.application.sagas;
