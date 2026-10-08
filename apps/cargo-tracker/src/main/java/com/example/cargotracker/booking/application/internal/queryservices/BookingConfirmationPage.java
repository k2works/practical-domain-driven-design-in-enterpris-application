package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 本予約の確定の画面（S-09）を開いたときの読み取りモデル（US-04。Bolt 23b）。開いた時刻での参考の判定で、確定の可否は送ったときの
 * 照会（commit 時刻）で決まる（ADR-016）。
 */
public sealed interface BookingConfirmationPage {

    /**
     * 確定に使える見積り。
     *
     * @param terms 予約条件（見積りの写し）
     * @param shipperApprovedAt 荷主の承認時刻
     * @param expiresAt 見積りの有効期限
     */
    record Available(BookingTerms terms, UtcInstant shipperApprovedAt, UtcInstant expiresAt)
            implements BookingConfirmationPage {

        public Available {
            Objects.requireNonNull(terms, "terms");
            Objects.requireNonNull(shipperApprovedAt, "shipperApprovedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    /**
     * 確定に使えない見積り。
     *
     * @param reason 理由
     */
    record Unavailable(QuotationUnavailability reason) implements BookingConfirmationPage {

        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 見積りの貨物予約がすでにある（B-INV-11。既存の追跡番号を示すのは AC4 の Bolt 24）。 */
    record AlreadyBooked() implements BookingConfirmationPage {}
}
