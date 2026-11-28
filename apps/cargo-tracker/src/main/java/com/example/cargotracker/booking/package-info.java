/**
 * 予約コンテキスト。本予約の確定と貨物予約・予約版・追跡番号、予約サガの状態を扱う（U6）。
 * 依存してよいのは共有カーネルと、見積りの公開 API と画面の表示の部品（platform :: web）だけ（ADR-001・014・016。Bolt 23）。
 * 予約は追跡に依存しない。追跡の開始は追跡が DE-07 を購読して行う（ADR-015）。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "予約",
        allowedDependencies = {"shared", "quotation :: api", "platform :: web"})
package com.example.cargotracker.booking;
