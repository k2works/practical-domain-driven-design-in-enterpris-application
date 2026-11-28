package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 荷主に見える最新の見積りの 1 行（C-02 の状態の選び方。D-85。Bolt 35d）。見積りの集約と同じ判定を写しで行う。 */
class ShipperLatestQuotationSummaryTest {

    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final UtcInstant PRESENTED = at("2026-10-05T04:00:00Z");
    private static final UtcInstant RESPONDED = at("2026-10-06T05:00:00Z");
    private static final UtcInstant EXPIRES = at("2026-10-09T09:00:00Z");

    @Test
    void 回答が提示より前なら再承認の見積りで後か同時刻か回答がなければそうでない() {
        assertThat(summary(QuotationStatus.AWAITING_SHIPPER_APPROVAL, null, at("2026-10-07T00:00:00Z"), RESPONDED)
                        .isPresentedForReapproval())
                .isTrue();
        assertThat(summary(QuotationStatus.AWAITING_SHIPPER_APPROVAL, null, PRESENTED, RESPONDED)
                        .isPresentedForReapproval())
                .isFalse();
        assertThat(summary(QuotationStatus.AWAITING_SHIPPER_APPROVAL, null, RESPONDED, RESPONDED)
                        .isPresentedForReapproval())
                .isFalse();
        assertThat(summary(QuotationStatus.PRESENTED, null, PRESENTED, null).isPresentedForReapproval())
                .isFalse();
    }

    @Test
    void 失効は失効の記録か期限で決まり置換済みは失効でない() {
        assertThat(summary(QuotationStatus.APPROVED, at("2026-10-06T00:00:00Z"), PRESENTED, RESPONDED)
                        .isExpiredAt(EXPIRES))
                .isTrue();
        assertThat(summary(QuotationStatus.APPROVED, at("2026-10-06T00:00:00Z"), PRESENTED, RESPONDED)
                        .isExpiredAt(at("2026-10-09T08:59:59Z")))
                .isFalse();
        assertThat(summary(QuotationStatus.EXPIRED, at("2026-10-06T00:00:00Z"), PRESENTED, RESPONDED)
                        .isExpiredAt(PRESENTED))
                .isTrue();
        assertThat(summary(QuotationStatus.REPLACED, null, PRESENTED, null).isExpiredAt(EXPIRES))
                .isFalse();
    }

    @Test
    void 荷主の承認の時刻があれば荷主が承認した見積り() {
        assertThat(summary(QuotationStatus.EXPIRED, at("2026-10-06T00:00:00Z"), PRESENTED, RESPONDED)
                        .isShipperApproved())
                .isTrue();
        assertThat(summary(QuotationStatus.AWAITING_SHIPPER_APPROVAL, null, PRESENTED, RESPONDED)
                        .isShipperApproved())
                .isFalse();
    }

    @Test
    void 荷主に見えない提示の前の見積りの行は作れない() {
        assertThatThrownBy(() -> summary(QuotationStatus.PENDING_APPROVAL, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static ShipperLatestQuotationSummary summary(
            QuotationStatus status, UtcInstant approvedAt, UtcInstant presentedAt, UtcInstant respondedAt) {
        return new ShipperLatestQuotationSummary(NUMBER, status, EXPIRES, approvedAt, presentedAt, respondedAt);
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
