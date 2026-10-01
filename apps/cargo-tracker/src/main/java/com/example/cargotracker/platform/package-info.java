/**
 * 基盤。コンテキストに属さない技術的な部品（MyBatis の型ハンドラーなど）を置く。
 * 業務の型と規則は置かず、コンテキストのコードからは設定を通してだけ使う。
 */
@org.springframework.modulith.ApplicationModule(displayName = "基盤")
package com.example.cargotracker.platform;
