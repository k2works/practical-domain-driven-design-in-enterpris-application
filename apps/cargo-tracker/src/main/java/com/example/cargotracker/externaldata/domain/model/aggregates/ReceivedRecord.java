package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 受信記録。外部原本を受け取るたびの、または形式の誤りで外部原本を持てない行・ファイルの追記専用の記録で、分類を一度だけ持つ
 * （data_model の {@code external_data.received_record}。ED-INV-02。Bolt 39b、39b2）。リポジトリのポートが受け渡す record で、
 * 振る舞いを持たない（トランザクションスクリプト。ADR-002）。取込によらない受信（表では {@code ingestion_id} が NULL 可）はまだないので、
 * 取込 ID を必ず持つ。外部原本 ID と取得時刻はそろって NULL かそろって NULL でなく、NULL なのは隔離だけ（キーのない隔離。表の CHECK
 * {@code ck_received_record_keyless_quarantine} と同じ規則。Bolt 39b2）。
 *
 * @param id 受信記録 ID
 * @param externalRecordId 外部原本 ID（キーのない隔離は {@code null}）
 * @param ingestionId 取込 ID
 * @param payloadHash 受け取った payload のハッシュ
 * @param payloadObjectKey 受け取った payload のオブジェクトキー
 * @param acquiredAt 取得時刻（行の値。キーのない隔離は {@code null}）
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
        Objects.requireNonNull(ingestionId, "ingestionId");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(payloadObjectKey, "payloadObjectKey");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(classifiedAt, "classifiedAt");
        if ((externalRecordId == null) != (acquiredAt == null)) {
            throw new IllegalArgumentException("外部原本 ID と取得時刻はそろって持つかそろって持たない: " + classification);
        }
        if (classification != Classification.QUARANTINED && externalRecordId == null) {
            throw new IllegalArgumentException("外部原本 ID と取得時刻を持たない受信記録は隔離だけ: " + classification);
        }
    }
}
