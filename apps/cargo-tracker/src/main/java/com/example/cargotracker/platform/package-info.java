/**
 * 基盤。コンテキストに属さない技術的な部品（MyBatis の型ハンドラーなど）を置く。
 * 業務の型と規則は置かず、コンテキストのコードからは設定を通してだけ使う。例外は画面の表示の部品（名前付きインターフェース
 * {@code web}）で、コンテキストの画面の層から参照できる（Bolt 22）。
 */
@org.springframework.modulith.ApplicationModule(displayName = "基盤")
package com.example.cargotracker.platform;
