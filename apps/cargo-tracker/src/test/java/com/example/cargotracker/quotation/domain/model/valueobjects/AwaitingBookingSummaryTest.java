package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 予約の確定待ちの 1 行の不変条件（承認済みの行と、経路を引き継いだ承認待ちの行。D-85。Bolt 35c）。 */
class AwaitingBookingSummaryTest {

    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final UtcInstant APPROVED_AT = new UtcInstant(Instant.parse("2026-10-07T06:00:00Z"));
    private static final UtcInstant EXPIRES = new UtcInstant(Instant.parse("2099-10-09T09:00:00Z"));

    @Test
    void 承認済みの行は荷主の承認時刻を持ち承認待ちの行は持たない() {
        assertThat(summary(QuotationStatus.APPROVED, APPROVED_AT).shipperApprovedAt())
                .isEqualTo(APPROVED_AT);
        assertThat(summary(QuotationStatus.PENDING_APPROVAL, null).shipperApprovedAt())
                .isNull();
    }

    @Test
    void 承認済みで承認時刻のない行と承認待ちで承認時刻のある行は作れない() {
        assertThatThrownBy(() -> summary(QuotationStatus.APPROVED, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summary(QuotationStatus.PENDING_APPROVAL, APPROVED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(
            names = {"APPROVED", "PENDING_APPROVAL"},
            mode = EnumSource.Mode.EXCLUDE)
    void 承認済みと承認待ちのほかの状態の行は作れない(QuotationStatus status) {
        assertThatThrownBy(() -> summary(status, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static AwaitingBookingSummary summary(QuotationStatus status, UtcInstant approvedAt) {
        return new AwaitingBookingSummary(NUMBER, 1, status, "RC-2026-0001", 1, approvedAt, EXPIRES);
    }
}
