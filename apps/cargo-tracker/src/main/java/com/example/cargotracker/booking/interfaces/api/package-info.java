/**
 * 予約の公開 API（他のコンテキストが呼ぶインバウンドサービス。開発ガイドライン第 3 章のインターフェース層）。追跡が追跡の開始の結果を返す
 * （ADR-015。Bolt 25）。型はインターフェースと record で、Java 標準と共有カーネルの型だけを持つ。実装はアプリケーションサービスに委ねる。
 */
@org.springframework.modulith.NamedInterface("api")
package com.example.cargotracker.booking.interfaces.api;
