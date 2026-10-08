package com.example.cargotracker.quotation.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 予約確定に使える見積りの照会の依頼（見積りの公開 API。Bolt 23）。
 *
 * @param quotationId 見積り ID
 * @param committedAt 予約確定の commit 時刻（判定と記録に同じ値を使う。ADR-016）
 */
public record BookableQuotationRequest(UUID quotationId, UtcInstant committedAt) {

    public BookableQuotationRequest {
        Objects.requireNonNull(quotationId, "quotationId");
        Objects.requireNonNull(committedAt, "committedAt");
    }
}
