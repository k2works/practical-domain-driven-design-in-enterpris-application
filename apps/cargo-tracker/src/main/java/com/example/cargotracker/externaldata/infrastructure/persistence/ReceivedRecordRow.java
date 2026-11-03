package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** external_data.received_record の 1 行（Bolt 39b）。 */
public record ReceivedRecordRow(
        UUID id,
        UUID externalRecordId,
        UUID ingestionId,
        String payloadHash,
        String payloadObjectKey,
        OffsetDateTime acquiredAt,
        String classification,
        String classificationNote,
        OffsetDateTime classifiedAt) {}
