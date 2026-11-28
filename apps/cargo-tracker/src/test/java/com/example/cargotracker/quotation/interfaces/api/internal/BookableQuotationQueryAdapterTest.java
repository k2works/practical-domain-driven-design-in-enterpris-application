package com.example.cargotracker.quotation.interfaces.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.queryservices.BookableQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestScope;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 見積りの公開 API の予約確定に使える見積りの照会（ADR-016、Q-INV-06。Bolt 23）。業務番号と見積り番号で引き（画面の URL に内部の
 * ID を出さない D-4。Bolt 23b）、commit 時刻で判定し、使えるなら確定に要る写しを、使えないなら理由を返す。境界の 3 点は集約の単体テスト（QuotationShipperApprovalTest）で確かめる。
 * 操作者の担当企業の外の輸送要求は、ほかの理由より先に、行を押さえる前に範囲の外を返す（Bolt 38f2。US-16 AC4、ADR-016）。
 */
class BookableQuotationQueryAdapterTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
    private static final UserId SALES = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));

    private static final AuthenticatedActor HANDLER = AuthenticatedActorFixture.salesHandling(SALES, SHIPPER);
    private static final AuthenticatedActor OUTSIDER =
            AuthenticatedActorFixture.salesHandling(SALES, new CompanyId(UUID.randomUUID()));

    private final InMemoryTransportRequestRepository transportRequests = new InMemoryTransportRequestRepository();

    private final LockCountingQuotations quotations = new LockCountingQuotations();
    private final BookableQuotationQueryAdapter service =
            new BookableQuotationQueryAdapter(new BookableQuotationQueryService(
                    quotations, transportRequests, new StaffTransportRequestScope(transportRequests)));

    @Test
    void 承認済みでcommit時刻が有効期限より前の見積りは確定に要る写しを返す() {
        TransportRequest request = submitted();
        Quotation quotation = approved(request);

        BookableQuotationResult result =
                service.find(new BookableQuotationRequest("TR-2026-0007", 1, at("2099-10-08T08:59:00Z"), HANDLER));

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
                        "一般 / パレット × 12 / 8,400 kg / 32.5 m3",
                        SHIPPER_USER.value(),
                        at("2026-10-07T06:00:00Z"),
                        QuotationFixture.EXPIRES_AT));
    }

    @Test
    void 有効期限と同時刻の見積りは失効を返す() {
        approved(submitted());

        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, at("2099-10-08T09:00:00Z"), HANDLER)))
                .isEqualTo(new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED));
    }

    @Test
    void 承認済みでない見積りと見つからない見積りは使えない理由を返す() {
        presented(submitted());

        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, now(), HANDLER)))
                .isEqualTo(new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.NOT_APPROVED));
        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 2, now(), HANDLER)))
                .as("見積り番号がない")
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND));
        assertThat(service.find(new BookableQuotationRequest("TR-2026-0099", 1, now(), HANDLER)))
                .as("業務番号がない")
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND));
        assertThat(service.find(new BookableQuotationRequest("TR-XXXX", 1, now(), HANDLER)))
                .as("業務番号の形でない")
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND));
    }

    @Test
    void 割り当てた経路が再設計要の承認済みの見積りは再設計要を返す() {
        // Bolt 37e（B-INV-05）。理由は見積りの側の照会で既定の NOT_APPROVED に寄せず、公開 API の定数で返す
        Quotation quotation = approved(submitted());
        Quotation marked = quotations.findById(quotation.id()).orElseThrow();
        marked.markRouteRedesignRequired(
                QuotationFixture.assignedRoute().routingCaseNumber(),
                QuotationFixture.assignedRoute().routeVersionNo(),
                at("2026-10-07T07:00:00Z"));
        quotations.update(marked);

        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, now(), HANDLER)))
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.ROUTE_REDESIGN_REQUIRED));
    }

    @Test
    void 割り当てた経路の最初の区間が出発した承認済みの見積りは出発済みを返す() {
        // Bolt 37e4（D-88）。見積りの写しの区間の出発が commit 時刻より前
        Quotation quotation = presented(submitted());
        quotation.requestRouteDesign(SHIPPER_USER, at("2026-10-06T02:00:00Z"));
        quotation.assignRoute(
                new com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute(
                        "RC-2026-0001",
                        1,
                        at("2026-10-07T05:00:00Z"),
                        java.util.List.of(
                                new com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg(
                                        "V-101",
                                        new com.example.cargotracker.shared.domain.Location("JPTYO"),
                                        new com.example.cargotracker.shared.domain.Location("NLRTM"),
                                        at("2026-10-08T00:00:00Z"),
                                        at("2026-10-30T00:00:00Z")))),
                at("2026-10-07T05:00:00Z"));
        quotation.approveByShipper(SHIPPER_USER, at("2026-10-07T06:00:00Z"));
        quotation.clearDomainEvents();
        quotations.update(quotation);

        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, now(), HANDLER)))
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.ROUTE_ALREADY_DEPARTED));
    }

    @Test
    void 担当企業の外の輸送要求はほかの理由より先に範囲の外を返す() {
        // 行 2b・4（範囲の外では失効・未承認・見つからない見積り番号を示さない）。結果は正規の業務番号と見積り番号だけを持つ
        approved(submitted());
        BookableQuotationResult outOfScope = new BookableQuotationResult.OutOfScope("TR-2026-0007", 1);

        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, now(), OUTSIDER)))
                .isEqualTo(outOfScope);
        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, at("2099-10-08T09:00:00Z"), OUTSIDER)))
                .as("有効期限を過ぎていても")
                .isEqualTo(outOfScope);
        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 2, now(), OUTSIDER)))
                .as("見積り番号がなくても")
                .isEqualTo(new BookableQuotationResult.OutOfScope("TR-2026-0007", 2));
        assertThat(service.find(new BookableQuotationRequest("TR-2026-0099", 1, now(), OUTSIDER)))
                .as("業務番号がないのは範囲によらず見つからない")
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND));
    }

    @Test
    void 担当企業の外の未承認の見積りも範囲の外を返す() {
        presented(submitted());

        assertThat(service.find(new BookableQuotationRequest("TR-2026-0007", 1, now(), OUTSIDER)))
                .isEqualTo(new BookableQuotationResult.OutOfScope("TR-2026-0007", 1));
    }

    @Test
    void 本予約の確定の照会は担当企業の外なら見積りの行を押さえずに範囲の外を返す() {
        // 行 4（範囲の判定は lockForBooking より前。V2b）
        approved(submitted());

        assertThat(service.findForConfirmation(new BookableQuotationRequest("TR-2026-0007", 1, now(), OUTSIDER)))
                .isEqualTo(new BookableQuotationResult.OutOfScope("TR-2026-0007", 1));
        assertThat(quotations.locks()).isZero();

        assertThat(service.findForConfirmation(new BookableQuotationRequest("TR-2026-0007", 1, now(), HANDLER)))
                .isInstanceOf(BookableQuotationResult.Bookable.class);
        assertThat(quotations.locks()).as("範囲の中は今のまま押さえる").isOne();
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

    /** 見積りの行を押さえた回数を数える（範囲の外で押さえないことを確かめる）。 */
    private static final class LockCountingQuotations extends InMemoryQuotationRepository {

        private final AtomicInteger locks = new AtomicInteger();

        @Override
        public boolean lockForBooking(TransportRequestId transportRequestId, int quotationNo) {
            locks.incrementAndGet();
            return super.lockForBooking(transportRequestId, quotationNo);
        }

        int locks() {
            return locks.get();
        }
    }

    private static UtcInstant now() {
        return at("2026-10-08T03:00:00Z");
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
