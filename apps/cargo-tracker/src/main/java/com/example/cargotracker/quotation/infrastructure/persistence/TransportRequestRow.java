package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 輸送要求のヘッダと現在の版を結合した行。
 */
public record TransportRequestRow(UUID id, UUID shipperCompanyId, String status, int currentVersionNo,
        long version, String originUnlocode, String destinationUnlocode, UUID submittedBy,
        OffsetDateTime submittedAt) {
}
