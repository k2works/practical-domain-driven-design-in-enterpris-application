package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** external_data.external_record の 1 行（Bolt 39b）。 */
public record ExternalRecordRow(
        UUID id,
        String provider,
        String recordType,
        String externalId,
        String externalVersion,
        String payloadHash,
        String payloadObjectKey,
        OffsetDateTime firstReceivedAt) {}
