/**
 * 経路設計コンテキスト。経路設計案件の候補の算出・比較と、経路の確定・再設計を扱う（U2）。
 * 依存してよいのは共有カーネルだけ（ADR-001・003）。見積りの公表された言語と公開 API は、使うステップで足す。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "経路設計",
        allowedDependencies = {"shared"})
package com.example.cargotracker.routing;
