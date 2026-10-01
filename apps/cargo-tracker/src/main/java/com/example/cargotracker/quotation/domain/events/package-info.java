/**
 * 見積りコンテキストのドメインイベント（公表された言語）。他のコンテキストはこのパッケージだけを参照できる。
 * Spring Modulith はイベントの型を完全修飾クラス名で保存するため、型を移動しない（バックエンドアーキテクチャ）。
 */
@org.springframework.modulith.NamedInterface("events")
package com.example.cargotracker.quotation.domain.events;
