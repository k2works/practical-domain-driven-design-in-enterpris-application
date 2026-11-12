/**
 * 追跡コンテキスト。追跡記録（予定と主要実績、現在状態）を扱う（U3）。
 * 依存してよいのは共有カーネルと、予約のイベント（DE-07）・公開 API（追跡の開始の結果を返す）と、経路設計の公開 API（確定した経路版の
 * 区間）（ADR-015。Bolt 25）と、外部データのイベント（DE-13。Bolt 39g。DE-12。Bolt 39g4b で最後の区間の航海の到着予定を受ける）と、画面の日時表示の部品（{@code platform :: web}。インターフェース層の Web からだけ使う。Bolt 26 で社内の画面、Bolt 27 で荷主の画面）
 * だけ。予約と経路設計は追跡に依存しない。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "追跡",
        allowedDependencies = {
            "shared",
            "booking :: events",
            "booking :: api",
            "routing :: api",
            "externaldata :: events",
            "platform :: web"
        })
package com.example.cargotracker.tracking;
