package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** external_data.received_record の 1 行（Bolt 39b）。キーのない隔離は外部原本 ID と取得時刻が {@code null}（Bolt 39b2）。行の範囲の 2 列はそろって {@code null} かそろって値を持つ（Bolt 39c3）。 */
public record ReceivedRecordRow(
        UUID id,
        UUID externalRecordId,
        UUID ingestionId,
        String payloadHash,
        String payloadObjectKey,
        OffsetDateTime acquiredAt,
        String classification,
        String classificationNote,
        OffsetDateTime classifiedAt,
        Integer firstLineNumber,
        Integer lastLineNumber) {}
