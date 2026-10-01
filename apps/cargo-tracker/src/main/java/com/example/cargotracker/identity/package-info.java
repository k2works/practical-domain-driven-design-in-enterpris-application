/**
 * アクセス・監査コンテキスト。企業・利用者・役割、監査記録、KPI 計測記録を扱う（U4）。
 * 依存してよいのは共有カーネルと、見積りの公表された言語（ドメインイベント）だけ（ADR-001・003）。
 */
@org.springframework.modulith.ApplicationModule(displayName = "アクセス・監査",
        allowedDependencies = {"shared", "quotation :: events"})
package com.example.cargotracker.identity;
