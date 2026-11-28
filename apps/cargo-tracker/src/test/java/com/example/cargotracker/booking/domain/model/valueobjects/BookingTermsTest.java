package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.BookingFixture;
import org.junit.jupiter.api.Test;

/** 予約条件（見積りの写し）。見積り番号は 1 から（Bolt 24）。 */
class BookingTermsTest {

    @Test
    void 見積り番号が1なら作れる() {
        assertThat(withQuotationNo(1).quotationNo()).isEqualTo(1);
    }

    @Test
    void 見積り番号が0なら見積り番号の誤りとして作れない() {
        assertThatThrownBy(() -> withQuotationNo(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("見積り番号");
    }

    private static BookingTerms withQuotationNo(int quotationNo) {
        BookingTerms base = BookingFixture.terms();
        return new BookingTerms(
                base.transportRequestId(),
                base.transportRequestVersionNo(),
                base.transportRequestNumber(),
                base.quotationId(),
                quotationNo,
                base.shipperCompanyId(),
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                base.cargoSummary(),
                base.shipperApproverId());
    }
}
