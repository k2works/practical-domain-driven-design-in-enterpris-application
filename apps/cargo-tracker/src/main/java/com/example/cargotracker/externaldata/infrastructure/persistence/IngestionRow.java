package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** external_data.ingestion の 1 行（Bolt 39b。取り込めなかった行の 2 列は Bolt 39b2）。 */
public record IngestionRow(
        UUID id,
        UUID dataSourceId,
        String fileObjectKey,
        String fileHash,
        UUID ingestedBy,
        OffsetDateTime ingestedAt,
        int receivedCount,
        Integer unparsedFromLine,
        int unparsedLineCount) {}
