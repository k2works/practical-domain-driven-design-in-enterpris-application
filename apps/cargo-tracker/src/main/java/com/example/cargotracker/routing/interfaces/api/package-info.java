/**
 * 経路設計の公開 API（他のコンテキストが呼ぶインバウンドサービス。開発ガイドライン第 3 章のインターフェース層）。追跡が確定した経路版の
 * 区間を予定として引く（ADR-015。Bolt 25）。型はインターフェースと record で、Java 標準と共有カーネルの型だけを持つ。
 */
@org.springframework.modulith.NamedInterface("api")
package com.example.cargotracker.routing.interfaces.api;
