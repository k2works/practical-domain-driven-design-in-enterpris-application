package com.example.cargotracker.routing.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;

/**
 * DE-30 航海を更新した（US-07 AC4、US-14 AC1。Bolt 39f）。経路設計の航海の更新と同じトランザクションで発行し、コミットの後に確定した経路を評価する
 * （同じファイルの 2 行で別々の航海が同時に更新されても、後にコミットした側の評価が両方の値を見る。【前の決定の変更】(6)）。予定が同じ採用値でも発行する。
 * 初めての航海では発行しない（確定の区間はまだない）。購読は経路設計の中だけ。
 *
 * @param voyageNumber 航海番号
 * @param infoVersion 更新した後の採用情報版
 * @param publishedAt 採用値を公開した時刻
 */
@DomainEvent
public record VoyageUpdated(String voyageNumber, String infoVersion, UtcInstant publishedAt) {}
