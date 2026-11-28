/**
 * アクセス・監査コンテキストのドメインイベント。password の再設定を依頼した（DE-23）は identity の中だけで購読する（依頼の応答から
 * トークンの発行とメールの送信を切り離す。ADR-017。Bolt 33）。他のコンテキストに公開しない（名前付きインターフェースにしない）。
 * Spring Modulith はイベントの型を完全修飾クラス名で保存するため、型を移動しない（バックエンドアーキテクチャ）。
 */
package com.example.cargotracker.identity.domain.events;
