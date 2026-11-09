package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 航海の表（`voyage`）の書き込みの 1 行。更新では {@code version} が読み込んだときの版（楽観ロック）。
 */
public record VoyageRow(
        String voyageNumber,
        String adoptedInfoVersion,
        String sourceKind,
        String sourceRef,
        OffsetDateTime acquiredAt,
        OffsetDateTime publishedAt,
        long version,
        OffsetDateTime updatedAt) {}
