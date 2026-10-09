/**
 * 追跡コンテキスト。追跡記録（予定と主要実績、現在状態）を扱う（U3）。
 * 依存してよいのは共有カーネルと、予約のイベント（DE-07）・公開 API（追跡の開始の結果を返す）と、経路設計の公開 API（確定した経路版の
 * 区間）だけ（ADR-015。Bolt 25）。予約と経路設計は追跡に依存しない。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "追跡",
        allowedDependencies = {"shared", "booking :: events", "booking :: api", "routing :: api"})
package com.example.cargotracker.tracking;
