/**
 * 通知の公開 API（他のコンテキストが呼ぶインバウンドサービス。開発ガイドライン第 3 章のインターフェース層）。identity が password の
 * 再設定のメールを送る（ADR-017。Bolt 33）。型はインターフェースと record で、Java 標準と共有カーネルの型だけを持つ。実装は
 * アプリケーションサービスに委ねる。
 */
@org.springframework.modulith.NamedInterface("api")
package com.example.cargotracker.notification.interfaces.api;
