package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 外部原本。冪等性キーごとの外部の原本で、採用中の payload のハッシュとオブジェクトキーを持つ（基準。はじめは最初に受け取った payload で、採用の判断で付け替える。data_model の
 * {@code external_data.external_record}。Bolt 39b、Bolt 39d4）。分類は持たない（受け取るたびの受信記録が持つ）。リポジトリのポートが受け渡す
 * record で、振る舞いを持たない（トランザクションスクリプト。ADR-002）。
 *
 * @param id 外部原本 ID
 * @param key 冪等性キー
 * @param payloadHash 採用中の payload のハッシュ
 * @param payloadObjectKey 採用中の payload のオブジェクトキー
 * @param firstReceivedAt 最初の受信時刻
 */
@Entity
public record ExternalRecord(
        UUID id,
        IdempotencyKey key,
        PayloadHash payloadHash,
        PayloadObjectKey payloadObjectKey,
        Instant firstReceivedAt) {

    public ExternalRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(payloadObjectKey, "payloadObjectKey");
        Objects.requireNonNull(firstReceivedAt, "firstReceivedAt");
    }
}
