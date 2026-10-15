/**
 * アクセス・監査コンテキスト。企業・利用者・役割、監査記録、KPI 計測記録を扱う（U4）。
 * 依存してよいのは共有カーネルと、見積りの公表された言語（ドメインイベント）、画面の表示の部品（platform :: web。Bolt 22）と、
 * 通知の公開 API（password の再設定のメール。腐敗防止層からだけ呼ぶ。ADR-017、Bolt 33）だけ（ADR-001・003）。通知は identity に依存しない。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "アクセス・監査",
        allowedDependencies = {"shared", "quotation :: events", "platform :: web", "notification :: api"})
package com.example.cargotracker.identity;
