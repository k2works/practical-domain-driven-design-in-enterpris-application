package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** external_data.adopted_value の 1 行（Bolt 39b3）。 */
public record AdoptedValueRow(
        UUID id,
        UUID receivedRecordId,
        String targetType,
        String targetKey,
        String valueSummary,
        String infoVersion,
        OffsetDateTime publishedAt) {}
