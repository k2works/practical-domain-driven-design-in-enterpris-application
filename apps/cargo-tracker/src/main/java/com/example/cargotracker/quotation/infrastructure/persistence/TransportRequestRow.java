package com.example.cargotracker.quotation.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 輸送要求のヘッダと現在の版を結合した行。
 */
public record TransportRequestRow(
        UUID id,
        String requestNumber,
        UUID shipperCompanyId,
        String status,
        int currentVersionNo,
        long version,
        UUID consigneeCompanyId,
        String originUnlocode,
        String destinationUnlocode,
        OffsetDateTime arrivalDeadline,
        String cargoCategory,
        String packageType,
        int packageCount,
        BigDecimal grossWeightKg,
        BigDecimal volumeM3,
        UUID submittedBy,
        OffsetDateTime submittedAt) {}
