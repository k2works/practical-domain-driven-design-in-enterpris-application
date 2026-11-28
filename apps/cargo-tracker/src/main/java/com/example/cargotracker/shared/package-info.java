/**
 * 共有カーネル。複数のコンテキストが同じ意味で使う値オブジェクト（UTC 時点、場所）と、
 * 設計上の役割を示す注釈の語彙を置く。どのコンテキストからも参照できる。振る舞いの型は、例外として担当範囲の外の試行の記録の
 * ポート 1 つだけを置く（ADR-019。Bolt 38f）。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "共有カーネル",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.example.cargotracker.shared;
