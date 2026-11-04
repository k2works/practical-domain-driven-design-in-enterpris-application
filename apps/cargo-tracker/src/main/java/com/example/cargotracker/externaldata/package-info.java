/**
 * 外部データコンテキスト。外部データはほかのコンテキストに依存しない上流。採用値・隔離・判断をイベントで公開する（39b 以降）。
 * 経路設計の表を書かない（ADR-004 の改訂。Bolt 39）。画面の層（S-16）は日時表示の部品（{@code platform :: web}）を使う（Bolt 39c）。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "外部データ",
        allowedDependencies = {"shared", "platform :: web"})
package com.example.cargotracker.externaldata;
