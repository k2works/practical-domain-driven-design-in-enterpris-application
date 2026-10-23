package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotification;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import com.example.cargotracker.shared.OverlappingTransactions;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 本予約の確定が見積りの行を押さえている間に、割り当てた経路版の再設計要が届いても、予約は残り、印は確定のコミットの後に付くことを、
 * 本物の本予約の確定と PostgreSQL で確かめる（US-07 AC4 の「予約は自動取消しされず」、R-INV-07。Bolt 37e2 の状態の組み合わせの表の
 * 行 13 を予約の保存のコミットまで。Bolt 37e4c の行 4・5）。印を付ける更新が確定の接続を待っていることは {@code pg_blocking_pids} で
 * 確かめる。テストの間でデータを共有しないよう、クラスに @Transactional を付けず、業務番号は 2081 年を使う。時計はシステムの時計の
 * まま（コンテキストを共有するため差し替えない）なので、事実の時刻は今より前、有効期限と区間の出発は 2099 年にする。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingConfirmationWithRouteRedesignIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());
    private static final AuthenticatedActor SALES = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(Role.SALES), "佐藤", "A 社");
    private static final String ROUTING_CASE_NUMBER = "RC-2081-0001";
    private static final UtcInstant REQUIRED_AT = at("2026-01-09T00:30:00Z");

    @Autowired
    BookingCommandService bookingCommandService;

    @Autowired
    BookingQueryService bookingQueryService;

    @Autowired
    RouteRedesignNotification routeRedesignNotification;

    @Autowired
    QuotationRepository quotations;

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 本予約の確定が見積りの行を押さえている間に来た再設計要の印は確定のコミットを待って付き予約は残る() {
        TransportRequest request = transactionTemplate.execute(status -> readyToBook(1));

        List<Object> results = OverlappingTransactions.run(
                transactionTemplate,
                () -> bookingCommandService.confirm(new ConfirmBookingCommand(
                        CommandId.random(), request.number().text(), 1, SALES, true)),
                () -> routeRedesignNotification.notifyRedesignRequired(
                        new RouteRedesignNotificationRequest(ROUTING_CASE_NUMBER, 1, REQUIRED_AT)));

        assertThat(results.get(0)).isInstanceOf(BookingConfirmationOutcome.Confirmed.class);
        TrackingNumber trackingNumber = ((BookingConfirmationOutcome.Confirmed) results.get(0)).trackingNumber();
        // 行 4。印が付いたことを先に確かめる（T-106・T-119）。印は確定のコミットの後に付いた
        assertThat(results.get(1)).isEqualTo(new RouteRedesignNotificationReceipt.Marked(1));
        Quotation marked =
                quotations.findByTransportRequestIdAndNo(request.id(), 1).orElseThrow();
        assertThat(marked.assignedRoute().orElseThrow().redesignRequiredAt()).isEqualTo(REQUIRED_AT);

        // 予約は自動で取り消されず確定のまま 1 件だけ残り、輸送要求は予約確定済みになる（DE-07 は非同期なので待つ）。予約サガの完了
        // （追跡の開始）は経路設計の案件の確定した区間を読むので、案件を作らないこのテストでは見ない
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(transportRequests
                                .findByNumber(request.number(), request.shipperCompanyId())
                                .orElseThrow()
                                .status())
                        .isEqualTo(TransportRequestStatus.BOOKED));
        assertThat(jdbc.queryForList(
                        "SELECT status FROM booking.booking WHERE transport_request_number = ?",
                        String.class,
                        request.number().text()))
                .containsExactly("CONFIRMED");
        // 行 5。予約の画面（S-24）の照会は予約を返す
        assertThat(bookingQueryService.detail(trackingNumber))
                .hasValueSatisfying(
                        detail -> assertThat(detail.booking().trackingNumber()).isEqualTo(trackingNumber));
    }

    /** 審査・提示・詳細経路設計の依頼・経路の割当て・荷主の承認まで進めた、予約待ちの輸送要求と見積り 1 を保存する。 */
    private TransportRequest readyToBook(int sequence) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2081, sequence),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                at("2026-01-05T01:00:00Z"));
        request.approve(1, STAFF, "契約条件を確認した", at("2026-01-05T03:00:00Z"));
        request.markQuotationPresented(1);
        request.markRoutingRequested(1);
        request.markAwaitingApproval(1);
        request.markReadyToBook(1);
        request.clearDomainEvents();
        transportRequests.save(request);
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-01-05T04:00:00Z"));
        quotation.presentInternally(STAFF, at("2026-01-05T04:30:00Z"));
        quotation.requestRouteDesign(SHIPPER_USER, at("2026-01-06T02:00:00Z"));
        quotation.assignRoute(
                new AssignedRoute(
                        ROUTING_CASE_NUMBER,
                        1,
                        at("2026-01-07T05:00:00Z"),
                        List.of(new AssignedRouteLeg(
                                "V-201",
                                new Location("JPTYO"),
                                new Location("NLRTM"),
                                at("2099-10-10T00:00:00Z"),
                                at("2099-10-31T00:00:00Z")))),
                at("2026-01-07T05:00:00Z"));
        quotation.approveByShipper(SHIPPER_USER, at("2026-01-08T03:00:00Z"));
        quotation.clearDomainEvents();
        quotations.save(quotation);
        return request;
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
