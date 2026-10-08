/**
 * 経路設計コンテキスト。経路設計案件の候補の算出・比較と、経路の確定・再設計を扱う（U2）。
 * 依存してよいのは共有カーネルと、見積りの公表された言語（ドメインイベント）と公開 API、画面の表示の部品（platform :: web。Bolt 22）
 * だけ（ADR-001・003。Bolt 17）。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "経路設計",
        allowedDependencies = {"shared", "quotation :: events", "quotation :: api", "platform :: web"})
package com.example.cargotracker.routing;
