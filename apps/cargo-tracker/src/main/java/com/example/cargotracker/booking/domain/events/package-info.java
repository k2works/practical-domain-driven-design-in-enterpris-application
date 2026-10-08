/**
 * 予約コンテキストのドメインイベント（公表された言語）。他のコンテキストはこのパッケージだけを参照できる（追跡は DE-07 を購読する。
 * ADR-015）。Spring Modulith はイベントの型を完全修飾クラス名で保存するため、型を移動しない（バックエンドアーキテクチャ）。
 */
@org.springframework.modulith.NamedInterface("events")
package com.example.cargotracker.booking.domain.events;
