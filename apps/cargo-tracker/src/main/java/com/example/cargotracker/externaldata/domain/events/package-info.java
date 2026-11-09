/**
 * 外部データコンテキストのドメインイベント（公表された言語。Bolt 39b3）。他のコンテキストはこのパッケージだけを参照できる
 * （{@code externaldata :: events}。監査と経路設計が購読する。追跡は 39g で購読する）。イベントは値の写しを Java の標準と共有カーネルの型
 * だけで持つ。Spring Modulith はイベントの型を完全修飾クラス名で保存するため、型を移動しない（バックエンドアーキテクチャ）。
 */
@org.springframework.modulith.NamedInterface("events")
package com.example.cargotracker.externaldata.domain.events;
