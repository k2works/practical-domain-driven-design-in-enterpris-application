/**
 * 共有カーネル。複数のコンテキストが同じ意味で使う値オブジェクト（UTC 時点、場所）と、
 * 設計上の役割を示す注釈の語彙を置く。どのコンテキストからも参照できる。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "共有カーネル",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.example.cargotracker.shared;
