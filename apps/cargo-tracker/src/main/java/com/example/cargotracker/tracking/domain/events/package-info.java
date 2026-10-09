/**
 * 追跡コンテキストのドメインイベント。DE-22 は追跡の中だけで購読する（予約サガの完了を別のトランザクションで返す。ADR-014・015）。
 * Spring Modulith はイベントの型を完全修飾クラス名で保存するため、型を移動しない（バックエンドアーキテクチャ）。
 */
package com.example.cargotracker.tracking.domain.events;
