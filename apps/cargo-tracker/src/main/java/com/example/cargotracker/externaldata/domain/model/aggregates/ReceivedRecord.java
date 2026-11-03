package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 受信記録。外部原本を受け取るたびの追記専用の記録で、分類を一度だけ持つ（data_model の {@code external_data.received_record}。
 * ED-INV-02。Bolt 39b）。リポジトリのポートが受け渡す record で、振る舞いを持たない（トランザクションスクリプト。ADR-002）。
 * 取込によらない受信（表では {@code ingestion_id} が NULL 可）はまだないので、39b では取込 ID を必ず持つ。
 *
 * @param id 受信記録 ID
 * @param externalRecordId 外部原本 ID
 * @param ingestionId 取込 ID
 * @param payloadHash 受け取った payload のハッシュ
 * @param payloadObjectKey 受け取った payload のオブジェクトキー
 * @param acquiredAt 取得時刻（行の値）
 * @param classification 分類
 * @param classificationNote 分類の注記（採用は注記なしで {@code null}）
 * @param classifiedAt 分類の時刻
 */
@Entity
public record ReceivedRecord(
        UUID id,
        UUID externalRecordId,
        UUID ingestionId,
        PayloadHash payloadHash,
        PayloadObjectKey payloadObjectKey,
        Instant acquiredAt,
        Classification classification,
        String classificationNote,
        Instant classifiedAt) {

    public ReceivedRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(externalRecordId, "externalRecordId");
        Objects.requireNonNull(ingestionId, "ingestionId");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(payloadObjectKey, "payloadObjectKey");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(classifiedAt, "classifiedAt");
    }
}
