/**
 * 通知の公開 API（他のコンテキストが呼ぶインバウンドサービス。開発ガイドライン第 3 章のインターフェース層）。identity が password の
 * 再設定のメール（Bolt 33）、利用者の招待のメール（Bolt 38b2b）、TOTP の解除・登録・再登録のお知らせ（Bolt 38d）を送る（ADR-017。
 * 種類ごとの口）。型はインターフェースと record で、Java 標準と共有カーネルの型だけを持つ。実装は
 * アプリケーションサービスに委ねる。
 */
@org.springframework.modulith.NamedInterface("api")
package com.example.cargotracker.notification.interfaces.api;
