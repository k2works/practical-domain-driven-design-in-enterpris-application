/**
 * 追跡コンテキストのドメインイベント。DE-22 は追跡の中だけで購読する（予約サガの完了を別のトランザクションで返す。ADR-014・015）。DE-09・DE-10 も
 * 追跡の中だけで購読し、予約の公開 API で予約の状態と輸送段階を進める（ADR-014。Bolt 39g2）。DE-18 は identity が購読して荷主へ通知する
 * （ADR-017 の補足。Bolt 43-1。そのため `events` の名前付きインターフェースとして公表する）。
 * Spring Modulith はイベントの型を完全修飾クラス名で保存するため、型を移動しない（バックエンドアーキテクチャ）。
 */
@org.springframework.modulith.NamedInterface("events")
package com.example.cargotracker.tracking.domain.events;
