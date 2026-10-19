package com.example.cargotracker.quotation.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.application.internal.QuotationGate.Verdict;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 輸送要求を進める・戻す前の見積りの判定（ADR-018。Bolt 35b2）。見積りの状態ごとの判定の表。 */
class QuotationGateTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-07T05:00:00Z"));

    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final TransportRequestId transportRequestId = new TransportRequestId(UUID.randomUUID());
    private final UserId user = new UserId(UUID.randomUUID());

    @ParameterizedTest(name = "{0} の見積り: 旧版の守りは {1}、再承認待ちの判定は {2}")
    @CsvSource({
        "DRAFT, PROCEED, NOT_AWAITING",
        "PENDING_APPROVAL, PROCEED, NOT_AWAITING",
        "PRESENTED, PROCEED, NOT_AWAITING",
        "ROUTING_REQUESTED, PROCEED, NOT_AWAITING",
        "AWAITING_SHIPPER_APPROVAL, PROCEED, PROCEED",
        "APPROVED, PROCEED, SETTLED",
        "EXPIRED, RETIRED, RETIRED",
        "REPLACED, RETIRED, RETIRED"
    })
    void 見積りの状態ごとに判定する(QuotationStatus status, Verdict notRetired, Verdict awaitingReapproval) {
        Quotation quotation = quotationIn(status);
        quotations.save(quotation);

        assertThat(QuotationGate.notRetired(quotations).check(quotation.id().value()))
                .isEqualTo(notRetired);
        assertThat(QuotationGate.awaitingReapproval(quotations)
                        .check(quotation.id().value()))
                .isEqualTo(awaitingReapproval);
    }

    @Test
    void 見積りが見つからなければ旧版の守りは進め再承認待ちの判定は見つからないとする() {
        UUID missing = UUID.randomUUID();

        assertThat(QuotationGate.notRetired(quotations).check(missing)).isEqualTo(Verdict.PROCEED);
        assertThat(QuotationGate.awaitingReapproval(quotations).check(missing)).isEqualTo(Verdict.NOT_FOUND);
        assertThat(QuotationGate.OPEN.check(missing)).isEqualTo(Verdict.PROCEED);
    }

    /** 実際の操作でその状態にした見積り（作成中は保存の前の形のまま）。 */
    private Quotation quotationIn(QuotationStatus status) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 1);
        if (status == QuotationStatus.DRAFT) {
            return quotation;
        }
        quotation.calculate(QuotationFixture.completeInput(), NOW);
        if (status == QuotationStatus.PENDING_APPROVAL) {
            return quotation;
        }
        quotation.presentInternally(user, NOW);
        if (status == QuotationStatus.PRESENTED || status == QuotationStatus.REPLACED) {
            if (status == QuotationStatus.REPLACED) {
                quotation.requoteInto(next(), NOW);
            }
            return quotation;
        }
        quotation.requestRouteDesign(user, NOW);
        if (status == QuotationStatus.ROUTING_REQUESTED) {
            return quotation;
        }
        quotation.assignRoute(QuotationFixture.assignedRoute(), NOW);
        if (status == QuotationStatus.AWAITING_SHIPPER_APPROVAL) {
            return quotation;
        }
        quotation.approveByShipper(user, NOW);
        if (status == QuotationStatus.EXPIRED) {
            quotation.requoteInto(next(), QuotationFixture.EXPIRES_AT);
        }
        assertThat(quotation.status()).as("前提").isEqualTo(status);
        return quotation;
    }

    private Quotation next() {
        Quotation next = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 2);
        next.calculate(QuotationFixture.completeInput(), NOW);
        return next;
    }
}
