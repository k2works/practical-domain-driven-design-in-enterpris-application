package com.example.cargotracker.quotation.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 料金明細の表（`pricing_line`）の 1 行。
 */
public record PricingLineRow(
        UUID quotationId,
        int lineNo,
        String description,
        BigDecimal amount,
        String currency,
        String contractReference) {}
