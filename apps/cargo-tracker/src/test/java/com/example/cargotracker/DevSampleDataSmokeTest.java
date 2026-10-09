package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.quotation.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.api.BookableQuotationResult;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AwaitingBookingSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * デモ環境のサンプルの業務データ（Bolt 18。`db/dev-data`）。dev プロファイルで起動した初期状態で、荷主・営業・経路設計者の
 * 照会にサンプルが出ること、算出済みの候補が判定の時刻で算出し直した結果と一致すること、日時が起動した日からの相対で
 * 先にあること、新しく振る番号がサンプルとぶつからないことを確かめる。荷主が承認した見積り（予約の確定待ち）の見本で、本予約の
 * 確定・送り直し・同じ見積りの別の確定をデモ環境で確かめられること（Bolt 24）も確かめる。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DevSampleDataSmokeTest {

    static final CompanyId SHIPPER_A = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    static final CompanyId SHIPPER_B = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    KpiObservationRepository kpiObservations;

    @Autowired
    QuotationRepository quotations;

    @Autowired
    RoutingCaseRepository routingCases;

    @Autowired
    VoyageRepository voyages;

    @Autowired
    ConnectionRuleRepository rules;

    @Autowired
    TransportRequestNumberIssuer transportRequestNumbers;

    @Autowired
    RoutingCaseNumberIssuer routingCaseNumbers;

    @Autowired
    BookableQuotationQuery bookableQuotations;

    @Autowired
    BookingCommandService bookingCommandService;

    static final AuthenticatedActor SALES = new AuthenticatedActor(
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301")),
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000003")),
            Set.of(Role.SALES),
            "営業 一郎（開発）",
            "A 社（開発）");

    @Test
    void 荷主は自社のサンプルの見積依頼だけを見る() {
        assertThat(transportRequests.findSummariesByShipper(SHIPPER_A))
                .extracting(summary -> summary.number().text())
                .contains("TR-2026-0902", "TR-2026-0903", "TR-2026-0905")
                .doesNotContain("TR-2026-0901", "TR-2026-0904");
        assertThat(transportRequests.findSummariesByShipper(SHIPPER_B))
                .extracting(summary -> summary.number().text())
                .contains("TR-2026-0901", "TR-2026-0904")
                .doesNotContain("TR-2026-0902", "TR-2026-0903", "TR-2026-0905");
    }

    @Test
    void 見積りを提示したサンプルはKPI計測記録に最初の提示時刻とリードタイムを持ち未提示のサンプルは持たない() {
        // Bolt 21。提示済み・詳細経路設計へ進んだ見積依頼（0903〜0905）は見積りの提示時刻を持ち、審査中・見積り作成中（0901・0902）は持たない
        java.util.Map<String, Boolean> presented = java.util.Map.of(
                "TR-2026-0901", false,
                "TR-2026-0902", false,
                "TR-2026-0903", true,
                "TR-2026-0904", true,
                "TR-2026-0905", true);
        assertThat(kpiObservations.findAll())
                .filteredOn(observation -> presented.containsKey(observation.transportRequestNumber()))
                .hasSize(presented.size())
                .allSatisfy(observation -> {
                    boolean expected = presented.get(observation.transportRequestNumber());
                    assertThat(observation.leadTime().isPresent()).isEqualTo(expected);
                    if (expected) {
                        assertThat(observation.firstPresentedAt())
                                .isEqualTo(quotations
                                        .findByTransportRequestIdAndNo(
                                                new TransportRequestId(observation.transportRequestId()), 1)
                                        .flatMap(Quotation::presentedAt));
                    } else {
                        assertThat(observation.firstPresentedAt()).isEmpty();
                    }
                });
    }

    @Test
    void 営業の受付一覧にサンプルが状態ごとに出る() {
        assertThat(transportRequests.findUnderReviewSummaries())
                .extracting(TransportRequestSummary::number)
                .contains(number(901));
        assertThat(transportRequests.findQuotingSummaries())
                .extracting(TransportRequestSummary::number)
                .contains(number(902));
        assertThat(quotations.findLatestOfQuotedRequests())
                .filteredOn(summary -> summary.number().equals(number(903)))
                .singleElement()
                .satisfies(summary -> assertThat(summary.expiresAt().instant()).isAfter(Instant.now()))
                .extracting(QuotedRequestSummary::quotationNo)
                .isEqualTo(1);
        assertThat(quotations.findRoutingRequestedSummaries())
                .extracting(RoutingRequestedSummary::number)
                .contains(number(904), number(905));
    }

    @Test
    void 経路設計者の案件一覧に算出待ちと算出済みの案件が出る() {
        assertThat(routingCases.findSummaries())
                .filteredOn(summary -> summary.number().sequence() >= 901)
                .extracting(
                        RoutingCaseSummary::number,
                        RoutingCaseSummary::transportRequestNumber,
                        RoutingCaseSummary::status)
                .contains(
                        org.assertj.core.groups.Tuple.tuple(
                                new RoutingCaseNumber(2026, 901), "TR-2026-0904", RouteVersionStatus.DRAFT),
                        org.assertj.core.groups.Tuple.tuple(
                                new RoutingCaseNumber(2026, 902),
                                "TR-2026-0905",
                                RouteVersionStatus.CANDIDATES_PRESENTED),
                        // 荷主が承認した見積りの見本の案件は、直行の候補を確定した経路版 1（Bolt 24）
                        org.assertj.core.groups.Tuple.tuple(
                                new RoutingCaseNumber(2026, 903), "TR-2026-0906", RouteVersionStatus.CONFIRMED),
                        org.assertj.core.groups.Tuple.tuple(
                                new RoutingCaseNumber(2026, 904), "TR-2026-0907", RouteVersionStatus.CONFIRMED));
        // DE-16 の写しの見積有効期限が入り、一覧は期限の近い順（Bolt 19）
        assertThat(routingCases.findSummaries())
                .filteredOn(summary -> summary.number().sequence() >= 901)
                .hasSize(4)
                .allSatisfy(summary -> assertThat(summary.expiresAt()).isPresent());
    }

    @Test
    void 算出済みの候補は判定の時刻で算出し直した結果と一致し希望到着期限は先にある() {
        RoutingCase seeded =
                routingCases.findByNumber(new RoutingCaseNumber(2026, 902)).orElseThrow();
        UtcInstant evaluatedAt = seeded.routeVersion().evaluatedAt().orElseThrow();
        RoutingCase recalculated =
                routingCases.findByNumber(new RoutingCaseNumber(2026, 902)).orElseThrow();

        recalculated.calculateCandidates(
                voyages.findAll(), rules.findAll(), evaluatedAt, new RouteCandidateFinder(), new ConstraintEvaluator());

        RouteVersion expected = recalculated.routeVersion();
        assertThat(seeded.routeVersion()).isEqualTo(expected);
        assertThat(expected.candidates()).hasSize(5);
        assertThat(expected.candidates())
                .filteredOn(candidate -> candidate.evaluation().conforming())
                .hasSize(2);
        assertThat(seeded.specification().arrivalDeadline().instant()).isAfter(Instant.now());
    }

    @Test
    void 営業の受付一覧の予約の確定待ちに荷主が承認した見本が有効期限の近い順に出る() {
        assertThat(quotations.findAwaitingBookingSummaries())
                .filteredOn(summary -> summary.number().sequence() >= 901)
                .extracting(AwaitingBookingSummary::number, AwaitingBookingSummary::quotationNo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(number(906), 1),
                        org.assertj.core.groups.Tuple.tuple(number(907), 1));
        assertThat(quotations.findAwaitingBookingSummaries())
                .filteredOn(summary -> summary.number().sequence() >= 901)
                .allSatisfy(summary -> assertThat(summary.expiresAt().instant()).isAfter(Instant.now()));
    }

    @Test
    void 荷主が承認した見本は見積りの公開APIで本予約の確定に使える() {
        for (String number : java.util.List.of("TR-2026-0906", "TR-2026-0907")) {
            assertThat(bookableQuotations.find(new BookableQuotationRequest(number, 1, new UtcInstant(Instant.now()))))
                    .as(number)
                    .isInstanceOf(BookableQuotationResult.Bookable.class);
        }
    }

    @Test
    void 見本で本予約を確定すると送り直しは同じ追跡番号で別のコマンドIDは既存の追跡番号になる() {
        CommandId commandId = CommandId.random();
        BookingConfirmationOutcome first =
                bookingCommandService.confirm(new ConfirmBookingCommand(commandId, "TR-2026-0906", 1, SALES, true));
        assertThat(first).isInstanceOf(BookingConfirmationOutcome.Confirmed.class);
        TrackingNumber trackingNumber = ((BookingConfirmationOutcome.Confirmed) first).trackingNumber();

        assertThat(bookingCommandService.confirm(new ConfirmBookingCommand(commandId, "TR-2026-0906", 1, SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.Confirmed(trackingNumber));
        assertThat(bookingCommandService.confirm(
                        new ConfirmBookingCommand(CommandId.random(), "TR-2026-0906", 1, SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.AlreadyBooked(trackingNumber));
    }

    @Test
    void 新しく振る番号はサンプルの番号の後から() {
        assertThat(transportRequestNumbers.next(2026).sequence()).isGreaterThan(907);
        assertThat(routingCaseNumbers.next(2026).sequence()).isGreaterThan(904);
    }

    private static TransportRequestNumber number(int sequence) {
        return new TransportRequestNumber(2026, sequence);
    }
}
