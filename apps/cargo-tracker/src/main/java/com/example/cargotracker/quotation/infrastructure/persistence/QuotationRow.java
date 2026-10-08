package com.example.cargotracker.quotation.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 見積りの表（`quotation`）の 1 行。
 */
public record QuotationRow(
        UUID id,
        UUID transportRequestId,
        int quotationNo,
        int transportRequestVersionNo,
        String status,
        OffsetDateTime expiresAt,
        BigDecimal totalAmount,
        String currency,
        String routePolicyVia,
        OffsetDateTime routePolicyDepartureAt,
        OffsetDateTime routePolicyArrivalAt,
        UUID internalApprovedBy,
        OffsetDateTime internalApprovedAt,
        OffsetDateTime presentedAt,
        UUID replacedByQuotationId,
        String shipperResponse,
        UUID respondedBy,
        OffsetDateTime respondedAt,
        String routingCaseNumber,
        Integer routeVersionNo,
        OffsetDateTime routeConfirmedAt,
        UUID shipperApprovedBy,
        OffsetDateTime shipperApprovedAt,
        long version) {}
