package com.example.cargotracker.quotation.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.api.BookableQuotationResult;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 見積りの公開 API の予約確定に使える見積りの照会（ADR-016、Q-INV-06。Bolt 23）。業務番号と見積り番号で引き（画面の URL に内部の
 * ID を出さない D-4。Bolt 23b）、commit 時刻で判定し、使えるなら確定に要る写しを、使えないなら理由を返す。境界の 3 点は集約の単体テスト（QuotationShipperApprovalTest）で確かめる。
 */
class BookableQuotationQueryServiceTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
    private static final UserId SALES = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));

    private final InMemoryTransportRequestRepository transportRequests = new InMemoryTransportRequestRepository();
    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final BookableQuotationQueryService service =
            new BookableQuotationQueryService(quotations, transportRequests);

    @Test
    void 承認済みでcommit時刻が有効期限より前の見積りは確定に要る写しを返す() {
        TransportRequest request = submitted();
        Quotation quotation = approved(request);

        BookableQuotationResult result =
                service.find(new BookableQuotationRequest("TR-2026-0007", 1, at("2099-10-08T08:59:00Z")));

        assertThat(result)
                .isEqualTo(new BookableQuotationResult.Bookable(
                        request.id().value(),
                        1,
                        "TR-2026-0007",
                        quotation.id().value(),
                        1,
                        SHIPPER,
                        ShipmentTermsFixture.CONSIGNEE,
                        "RC-2026-0001",
                        1,
                        "GENERAL",
                        "GENERAL / PALLET × 12 / 8400.000 kg / 32.500 m3",
                        SHIPPER_USER.value(),
                        at("2026-10-07T06:00:00Z"),
                        QuotationFixture.EXPIRES_AT));
    }

    @Test
    void 有効期限と同時刻の見積りは失効を返す() {
        Quotation quotation = approved(submitted());

        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, at("2099-10-08T09:00:00Z"))))
                .isEqualTo(new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED));
    }

    @Test
    void 承認済みでない見積りと見つからない見積りは使えない理由を返す() {
        presented(submitted());

        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, now())))
                .isEqualTo(new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.NOT_APPROVED));
        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 2, now())))
                .as("見積り番号がない")
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND));
        assertThat(service.find(new BookableQuotationRequest("TR-2026-0099", 1, now())))
                .as("業務番号がない")
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND));
        assertThat(service.find(new BookableQuotationRequest("TR-XXXX", 1, now())))
                .as("業務番号の形でない")
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND));
    }

    private TransportRequest submitted() {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 7),
                SHIPPER,
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                at("2026-10-05T01:00:00Z"));
        transportRequests.save(request);
        return request;
    }

    private Quotation presented(TransportRequest request) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-10-05T04:00:00Z"));
        quotation.presentInternally(SALES, at("2026-10-05T04:30:00Z"));
        quotation.clearDomainEvents();
        quotations.save(quotation);
        return quotation;
    }

    private Quotation approved(TransportRequest request) {
        Quotation quotation = presented(request);
        quotation.requestRouteDesign(SHIPPER_USER, at("2026-10-06T02:00:00Z"));
        quotation.assignRoute(QuotationFixture.assignedRoute(), at("2026-10-07T05:00:00Z"));
        quotation.approveByShipper(SHIPPER_USER, at("2026-10-07T06:00:00Z"));
        quotation.clearDomainEvents();
        quotations.update(quotation);
        return quotation;
    }

    private static UtcInstant now() {
        return at("2026-10-08T03:00:00Z");
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
