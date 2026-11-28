/**
 * 追跡の公開 API（他のコンテキストが呼ぶインバウンドサービス。開発ガイドライン第 3 章のインターフェース層）。アクセス・監査が週次の KPI-02 の
 * 材料（期間の照会の件数）を引く（US-21 AC4。W9 の Bolt 44-1）。型はインターフェースと record で、Java 標準と共有カーネルの型だけを持つ。
 */
@org.springframework.modulith.NamedInterface("api")
package com.example.cargotracker.tracking.interfaces.api;
