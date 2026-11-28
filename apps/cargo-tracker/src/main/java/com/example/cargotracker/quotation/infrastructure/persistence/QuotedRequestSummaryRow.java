package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 見積提示済みの見積依頼の最新の見積りの 1 行（受付一覧 S-02。Bolt 11）。
 */
public record QuotedRequestSummaryRow(String requestNumber, int quotationNo, String status, OffsetDateTime expiresAt) {}
