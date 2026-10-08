/**
 * 見積りコンテキスト。輸送要求の提出・審査と、見積りの作成・提示を扱う（U1）。
 * 上流のコンテキストなので、依存してよいのは共有カーネルと画面の表示の部品（platform :: web）だけ（ADR-014。Bolt 22 レビュー）。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "見積り",
        allowedDependencies = {"shared", "platform :: web"})
package com.example.cargotracker.quotation;
