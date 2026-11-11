/**
 * 追跡コンテキストのドメインイベント。DE-22 は追跡の中だけで購読する（予約サガの完了を別のトランザクションで返す。ADR-014・015）。DE-09・DE-10 も
 * 追跡の中だけで購読し、予約の公開 API で予約の状態と輸送段階を進める（ADR-014。Bolt 39g2）。
 * Spring Modulith はイベントの型を完全修飾クラス名で保存するため、型を移動しない（バックエンドアーキテクチャ）。
 */
package com.example.cargotracker.tracking.domain.events;
