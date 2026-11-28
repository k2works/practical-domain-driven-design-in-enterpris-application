package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteAssignmentResult;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 条件の変更で旧版の見積りを無効にする（US-05 AC1。Bolt 62。CP-62-2）。無効の見積りは、回答・承認・割当て・本予約・再見積りに使えない。
 */
class QuotationVoidedByRevisionTest {

    private static final UserId SALES = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER = new UserId(UUID.randomUUID());
    private static final UtcInstant NOW = at("2026-10-08T03:00:00Z");

    private final TransportRequestId transportRequestId = new TransportRequestId(UUID.randomUUID());

    /** 条件の変更で無効にする対象（1 つの輸送要求に 1 つだけの見積りに数える状態）。 */
    private enum Active {
        PENDING_APPROVAL,
        PRESENTED,
        ROUTING_REQUESTED,
        AWAITING_SHIPPER_APPROVAL,
        APPROVED
    }

    @ParameterizedTest
    @EnumSource(Active.class)
    void 有効な見積りは無効になり有効な件数に数えず期限でも失効しない(Active state) {
        Quotation quotation = in(state);

        assertThat(quotation.voidByTermsRevision()).isTrue();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.VOIDED);
        assertThat(quotation.isActive()).isFalse();
        assertThat(quotation.status().isRetired()).isTrue();
        assertThat(quotation.status().expiresByTime()).isFalse();
        assertThat(quotation.domainEvents()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Active.class)
    void 無効の見積りは回答も承認も本予約も再見積りもできない(Active state) {
        Quotation quotation = in(state);
        quotation.voidByTermsRevision();

        assertThat(quotation.requestRouteDesign(SHIPPER, NOW)).isPresent();
        assertThat(quotation.decline(SHIPPER, "理由", NOW)).isPresent();
        assertThat(quotation.approveByShipper(SHIPPER, NOW)).isPresent();
        assertThat(quotation.presentInternally(SALES, NOW)).isPresent();
        assertThat(quotation.bookingRejectionAt(NOW)).hasValue(QuotationRejection.VOIDED);
        assertThat(quotation.requoteRejectionAt(NOW)).hasValue(QuotationRejection.VOIDED);
        assertThat(quotation.status()).isEqualTo(QuotationStatus.VOIDED);
        assertThat(quotation.domainEvents()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Active.class)
    void 無効の見積りには経路版を割り当てない(Active state) {
        Quotation quotation = in(state);
        quotation.voidByTermsRevision();

        assertThat(quotation.assignRoute(QuotationFixture.assignedRoute(), NOW))
                .isEqualTo(RouteAssignmentResult.RETIRED);
    }

    @ParameterizedTest
    @EnumSource(Active.class)
    void 旧版として退いた見積りと無効の見積りは変えない(Active state) {
        Quotation quotation = in(state);
        quotation.voidByTermsRevision();

        assertThat(quotation.voidByTermsRevision()).isFalse();
        assertThat(quotation.status()).isEqualTo(QuotationStatus.VOIDED);
    }

    private Quotation in(Active state) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-10-05T04:00:00Z"));
        if (state != Active.PENDING_APPROVAL) {
            quotation.presentInternally(SALES, at("2026-10-05T04:30:00Z"));
        }
        if (state.ordinal() >= Active.ROUTING_REQUESTED.ordinal()) {
            quotation.requestRouteDesign(SHIPPER, at("2026-10-06T02:00:00Z"));
        }
        if (state.ordinal() >= Active.AWAITING_SHIPPER_APPROVAL.ordinal()) {
            quotation.assignRoute(QuotationFixture.assignedRoute(), at("2026-10-08T02:30:00Z"));
        }
        if (state == Active.APPROVED) {
            quotation.approveByShipper(SHIPPER, at("2026-10-08T02:40:00Z"));
        }
        assertThat(quotation.status()).isEqualTo(QuotationStatus.valueOf(state.name()));
        quotation.clearDomainEvents();
        return quotation;
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
