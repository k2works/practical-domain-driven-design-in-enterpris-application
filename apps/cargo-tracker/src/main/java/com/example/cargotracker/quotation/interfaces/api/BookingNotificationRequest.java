package com.example.cargotracker.quotation.interfaces.api;

import java.util.Objects;
import java.util.UUID;

/**
 * 予約確定済みの通知の依頼（見積りの公開 API。Bolt 23）。業務キーは輸送要求 ID と版。
 *
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 本予約に使った見積りの対象の輸送要求の版番号
 * @param quotationId 本予約に使った見積り ID（ログの手がかり）
 * @param bookingId 予約 ID（ログの手がかり）
 */
public record BookingNotificationRequest(
        UUID transportRequestId, int transportRequestVersionNo, UUID quotationId, UUID bookingId) {

    public BookingNotificationRequest {
        Objects.requireNonNull(transportRequestId, "transportRequestId");
        Objects.requireNonNull(quotationId, "quotationId");
        Objects.requireNonNull(bookingId, "bookingId");
    }
}
