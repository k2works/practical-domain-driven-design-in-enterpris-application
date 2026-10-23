package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.quotation.application.internal.commands.ApproveQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequoteQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.PresentationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.RequotationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.ShipperApprovalOutcome;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.rules.RequotePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.AwaitingBookingSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * 確定・送り直し・同じ見積りの別の確定をデモ環境で確かめられること（Bolt 24）も確かめる。荷主の承認の後に失効した見積りの
 * 見本（TR-2026-0908。D-85）で、事実の時刻の順がそろい、再見積り・提示・荷主の再承認・見積り 2 での本予約の確定まで集約の守りを
 * 通って進めること（Bolt 35e）も確かめる。割り当てた経路が再設計要になった見積りの見本（TR-2026-0909 は荷主承認待ち、TR-2026-0910 は
 * 承認済み。US-07 AC5）で、荷主の承認・本予約の照会・再見積り・経路の確定が再設計要の理由で拒まれ、予約の確定待ちの行に印が出ること
 * （Bolt 37e3b）も確かめる。直接入れた事実の順は読み込みで検査されないので、このテストで守る。
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
    org.springframework.jdbc.core.JdbcTemplate jdbc;

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

    /** 本体の組み立てが設定から作った情報鮮度の判定（既定の値を写さない。Bolt 37b2 の開発レビュー）。 */
    @Autowired
    InfoFreshnessPolicy infoFreshnessPolicy;

    @Autowired
    TransportRequestNumberIssuer transportRequestNumbers;

    @Autowired
    RoutingCaseNumberIssuer routingCaseNumbers;

    @Autowired
    BookableQuotationQuery bookableQuotations;

    @Autowired
    BookingCommandService bookingCommandService;

    @Autowired
    QuotationCommandService quotationCommandService;

    @Autowired
    QuotationResponseService quotationResponseService;

    static final UserId SHIPPER_A_USER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

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
                .contains(
                        "TR-2026-0902", "TR-2026-0903", "TR-2026-0905", "TR-2026-0908", "TR-2026-0909", "TR-2026-0910")
                .doesNotContain("TR-2026-0901", "TR-2026-0904");
        assertThat(transportRequests.findSummariesByShipper(SHIPPER_B))
                .extracting(summary -> summary.number().text())
                .contains("TR-2026-0901", "TR-2026-0904")
                .doesNotContain("TR-2026-0902", "TR-2026-0903", "TR-2026-0905", "TR-2026-0908");
    }

    @Test
    void 承認の後に失効した見本の見積依頼は予約待ちで版1() {
        TransportRequest request = expiredAfterApprovalRequest();

        assertThat(request.status()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(request.currentVersion().versionNo()).isEqualTo(1);
    }

    @Test
    void 承認の後に失効した見本の見積り1は提示と回答と荷主の承認と有効期限が今より前の順に並ぶ() {
        Quotation quotation = expiredAfterApprovalQuotation(1);
        Instant presentedAt = quotation.presentedAt().orElseThrow().instant();
        Instant respondedAt = quotation.respondedAt().orElseThrow().instant();
        Instant confirmedAt =
                quotation.assignedRoute().orElseThrow().confirmedAt().instant();
        Instant approvedAt =
                quotation.shipperApproval().orElseThrow().approvedAt().instant();
        Instant expiresAt = quotation.expiry().orElseThrow().expiresAt().instant();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(presentedAt).isBefore(respondedAt);
        assertThat(confirmedAt).isAfter(respondedAt).isBefore(approvedAt);
        assertThat(approvedAt).isBefore(expiresAt);
        assertThat(expiresAt).isBefore(Instant.now());
        assertThat(quotation.isPresentedForReapproval()).isFalse();
    }

    @Test
    void 承認の後に失効した見本は荷主に見える最新の見積りが荷主承認済みで失効した見積り1() {
        assertThat(quotations.findLatestVisibleForShipper(SHIPPER_A))
                .filteredOn(summary -> summary.number().equals(number(908)))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.status()).isEqualTo(QuotationStatus.APPROVED);
                    assertThat(summary.isShipperApproved()).isTrue();
                    assertThat(summary.isExpiredAt(new UtcInstant(Instant.now())))
                            .isTrue();
                    assertThat(summary.isPresentedForReapproval()).isFalse();
                    // 読み取りモデルは見積り番号を持たないので、見積り 1 の有効期限と提示の時刻で見積り 1 だと確かめる
                    Quotation first = expiredAfterApprovalQuotation(1);
                    assertThat(summary.expiresAt())
                            .isEqualTo(first.expiry().orElseThrow().expiresAt());
                    assertThat(summary.presentedAt())
                            .isEqualTo(first.presentedAt().orElseThrow());
                });
    }

    @Test
    void 承認の後に失効した見本の見積り1は再見積りできる() {
        assertThat(RequotePolicy.rejection(
                        expiredAfterApprovalRequest(), expiredAfterApprovalQuotation(1), new UtcInstant(Instant.now())))
                .isEmpty();
    }

    @Test
    void 承認の後に失効した見本の見積り1は本予約の確定に使えない() {
        assertThat(bookableQuotations.find(
                        new BookableQuotationRequest("TR-2026-0908", 1, new UtcInstant(Instant.now()))))
                .isEqualTo(new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED));
    }

    @Test
    void 承認の後に失効した見本を再見積りし提示すると荷主がもう一度承認し見積り2で本予約を確定できる() {
        // 集約の守りだけを確かめる。輸送要求の戻し（DE-24・DE-04）はコミットの後の購読なので、このテストの中では動かない（Bolt 35e）
        TransportRequestNumber number = number(908);

        assertThat(quotationCommandService.requote(
                        new RequoteQuotationCommand(number, 1, QuotationFixture.completeInput())))
                .isInstanceOf(RequotationOutcome.Calculated.class);
        // 旧版の見積り 1 は失効を記録し、本予約に使えないまま
        assertThat(expiredAfterApprovalQuotation(1).status()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(bookableQuotations.find(
                        new BookableQuotationRequest("TR-2026-0908", 1, new UtcInstant(Instant.now()))))
                .isInstanceOf(BookableQuotationResult.NotBookable.class);
        assertThat(quotationCommandService.present(new PresentQuotationCommand(number, 2, SALES.userId())))
                .isInstanceOf(PresentationOutcome.Presented.class);
        Quotation presented = expiredAfterApprovalQuotation(2);
        assertThat(presented.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(presented.isPresentedForReapproval()).isTrue();

        assertThat(quotationResponseService.approve(new ApproveQuotationCommand(number, 2, SHIPPER_A, SHIPPER_A_USER)))
                .isInstanceOf(ShipperApprovalOutcome.Approved.class);
        assertThat(expiredAfterApprovalQuotation(2).status()).isEqualTo(QuotationStatus.APPROVED);

        assertThat(bookingCommandService.confirm(
                        new ConfirmBookingCommand(CommandId.random(), "TR-2026-0908", 2, SALES, true)))
                .isInstanceOf(BookingConfirmationOutcome.Confirmed.class);
    }

    @Test
    void 見積りを提示したサンプルはKPI計測記録に最初の提示時刻とリードタイムを持ち未提示のサンプルは持たない() {
        // Bolt 21。提示済み・詳細経路設計へ進んだ見積依頼（0903〜0905。Bolt 35e の 0908 も）は見積りの提示時刻を持ち、審査中・見積り作成中（0901・0902）は持たない
        java.util.Map<String, Boolean> presented = java.util.Map.of(
                "TR-2026-0901", false,
                "TR-2026-0902", false,
                "TR-2026-0903", true,
                "TR-2026-0904", true,
                "TR-2026-0905", true,
                "TR-2026-0908", true,
                "TR-2026-0909", true,
                "TR-2026-0910", true);
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
                                new RoutingCaseNumber(2026, 904), "TR-2026-0907", RouteVersionStatus.CONFIRMED),
                        // 荷主の承認の後に失効した見積りの見本の案件も確定した経路版 1（Bolt 35e）
                        org.assertj.core.groups.Tuple.tuple(
                                new RoutingCaseNumber(2026, 905), "TR-2026-0908", RouteVersionStatus.CONFIRMED),
                        // 割り当てた経路が再設計要になった見本の案件（Bolt 37e3b）
                        org.assertj.core.groups.Tuple.tuple(
                                new RoutingCaseNumber(2026, 906), "TR-2026-0909", RouteVersionStatus.REDESIGN_REQUIRED),
                        org.assertj.core.groups.Tuple.tuple(
                                new RoutingCaseNumber(2026, 907),
                                "TR-2026-0910",
                                RouteVersionStatus.REDESIGN_REQUIRED));
        // DE-16 の写しの見積有効期限が入り、一覧は期限の近い順（Bolt 19）
        assertThat(routingCases.findSummaries())
                .filteredOn(summary -> summary.number().sequence() >= 901)
                .hasSize(7)
                .allSatisfy(summary -> assertThat(summary.expiresAt()).isPresent());
    }

    @Test
    void 承認の後に失効した見本の案件は見積有効期限を過ぎて案件一覧の先頭に出る() {
        // DE-16 の時点の見積有効期限の写しなので、見積りの失効の後は過ぎて見える（現実の流れと同じ。Bolt 35e）
        assertThat(routingCases.findSummaries())
                .filteredOn(summary -> summary.number().sequence() >= 901)
                .first()
                .satisfies(summary -> {
                    assertThat(summary.number()).isEqualTo(new RoutingCaseNumber(2026, 905));
                    assertThat(summary.expiresAt().orElseThrow().instant()).isBefore(Instant.now());
                });
    }

    @Test
    void 承認の後に失効した見本の案件は候補の算出の後に確定し確定の時刻と見積有効期限が見積り1の写しと一致する() {
        // 経路設計の時計の順と、見積りへの写し（DE-05 の経路の確定の時刻、DE-16 の見積有効期限）は直接入れるので、ここで守る（T-162）
        RoutingCase routingCase =
                routingCases.findByNumber(new RoutingCaseNumber(2026, 905)).orElseThrow();
        Quotation first = expiredAfterApprovalQuotation(1);
        RouteVersion confirmed = routingCase.confirmedRouteVersion().orElseThrow();
        Instant approvedAt = confirmed.confirmation().orElseThrow().approvedAt().instant();

        assertThat(confirmed.evaluatedAt().orElseThrow().instant()).isBefore(approvedAt);
        assertThat(approvedAt)
                .isEqualTo(first.assignedRoute().orElseThrow().confirmedAt().instant());
        assertThat(routingCase.quotationExpiresAt())
                .contains(first.expiry().orElseThrow().expiresAt());
    }

    @Test
    void 算出済みの候補は判定の時刻で算出し直した結果と一致し希望到着期限は先にある() {
        RoutingCase seeded =
                routingCases.findByNumber(new RoutingCaseNumber(2026, 902)).orElseThrow();
        UtcInstant evaluatedAt = seeded.routeVersion().evaluatedAt().orElseThrow();
        RoutingCase recalculated =
                routingCases.findByNumber(new RoutingCaseNumber(2026, 902)).orElseThrow();

        recalculated.calculateCandidates(
                voyages.findAll(),
                rules.findAll(),
                evaluatedAt,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                infoFreshnessPolicy);

        RouteVersion expected = recalculated.routeVersion();
        assertThat(seeded.routeVersion()).isEqualTo(expected);
        assertThat(expected.candidates()).hasSize(5);
        assertThat(expected.candidates())
                .filteredOn(candidate -> candidate.evaluation().conforming())
                .hasSize(2);
        assertThat(seeded.specification().arrivalDeadline().instant()).isAfter(Instant.now());
    }

    @Test
    void 航海の取得時刻は起動の時刻で見本の候補の判定時刻と区間の取得時刻にそろう() {
        // Bolt 37b: 情報鮮度の判定（US-06 AC4、Bolt 37b2。許容 24 時間）で、起動から 24 時間は情報不足にならない。取得が判定より後に見えない
        RoutingCase seeded =
                routingCases.findByNumber(new RoutingCaseNumber(2026, 902)).orElseThrow();
        UtcInstant evaluatedAt = seeded.routeVersion().evaluatedAt().orElseThrow();

        assertThat(voyages.findAll())
                .isNotEmpty()
                .allSatisfy(voyage -> assertThat(voyage.acquiredAt()).isEqualTo(evaluatedAt));
        assertThat(seeded.routeVersion().candidates())
                .flatMap(RouteCandidate::legs)
                .allSatisfy(leg -> assertThat(leg.infoAcquiredAt()).isEqualTo(evaluatedAt));
        // 起動の時刻は、このマイグレーションを流した時刻（H2 の DB はテストの JVM の中で共有されるので、今の時刻とは比べない）
        LocalDateTime installedOn = jdbc.queryForObject(
                "SELECT installed_on FROM flyway_schema_history WHERE version = '20261021170900'", LocalDateTime.class);
        Instant migratedAt = installedOn.atZone(ZoneId.systemDefault()).toInstant();
        assertThat(Duration.between(evaluatedAt.instant(), migratedAt).abs()).isLessThan(Duration.ofMinutes(1));
        // 最も古い情報の取得時刻の列は読み戻しでは使わない（区間から求める）ので、列の値を直に確かめる
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM routing.route_candidate c WHERE c.routing_case_id ="
                                + " '00000000-0000-0000-0000-000000000942' AND (c.oldest_info_acquired_at IS NULL OR"
                                + " c.oldest_info_acquired_at <> (SELECT MIN(l.info_acquired_at) FROM routing.candidate_leg l"
                                + " WHERE l.routing_case_id = c.routing_case_id AND l.route_version_no = c.route_version_no"
                                + " AND l.candidate_no = c.candidate_no))",
                        Integer.class))
                .isZero();
    }

    @Test
    void 営業の受付一覧の予約の確定待ちに荷主が承認した見本が有効期限の近い順に出る() {
        assertThat(quotations.findAwaitingBookingSummaries())
                .filteredOn(summary -> summary.number().sequence() >= 901)
                .extracting(AwaitingBookingSummary::number, AwaitingBookingSummary::quotationNo)
                .containsExactly(
                        // 荷主の承認の後に失効した見本（有効期限は起動した日の前日。Bolt 35e）が先頭
                        org.assertj.core.groups.Tuple.tuple(number(908), 1),
                        org.assertj.core.groups.Tuple.tuple(number(906), 1),
                        org.assertj.core.groups.Tuple.tuple(number(907), 1),
                        // 承認の後に経路が再設計要になった見本（有効期限は起動した日の 7 日後。Bolt 37e3b）
                        org.assertj.core.groups.Tuple.tuple(number(910), 1));
        assertThat(quotations.findAwaitingBookingSummaries())
                .filteredOn(summary ->
                        summary.number().equals(number(906)) || summary.number().equals(number(907)))
                .hasSize(2)
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
        assertThat(transportRequestNumbers.next(2026).sequence()).isGreaterThan(910);
        assertThat(routingCaseNumbers.next(2026).sequence()).isGreaterThan(907);
    }

    // 割り当てた経路が再設計要になった見本（US-07 AC5。Bolt 37e3b の状態の組み合わせの行 1〜8）

    @Test
    void 再設計要の見本の見積依頼は荷主承認待ちと予約待ちで見積りは割り当てた経路が再設計要() {
        assertThat(routeRedesignRequest(909).status()).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
        assertThat(routeRedesignRequest(910).status()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(routeRedesignQuotation(909).status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(routeRedesignQuotation(910).status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(routeRedesignQuotation(909).isRouteRedesignRequired()).isTrue();
        assertThat(routeRedesignQuotation(910).isRouteRedesignRequired()).isTrue();
        // 荷主の承認は 0910 だけにある（0909 は承認の前に印が付いた）
        assertThat(routeRedesignQuotation(909).shipperApproval()).isEmpty();
        assertThat(routeRedesignQuotation(910).shipperApproval()).isPresent();
    }

    @Test
    void 再設計要でない見本の見積りと予約の確定待ちの行には印がない() {
        // 陰性の対照（印の写し違いで、ほかの見本に印が付いていないこと）
        assertThat(quotations.findAwaitingBookingSummaries())
                .filteredOn(summary -> java.util.Set.of(906, 907, 908)
                        .contains(summary.number().sequence()))
                .hasSize(3)
                .allSatisfy(
                        summary -> assertThat(summary.routeRedesignRequired()).isFalse());
        assertThat(expiredAfterApprovalQuotation(1).isRouteRedesignRequired()).isFalse();
    }

    @Test
    void 再設計要の見本は提示と回答と経路の確定と承認と再設計要と起動と出発と有効期限の順に並ぶ() {
        // 直接入れた事実の順を守る（T-162）。出発を起動の後にして、出発済みの判定（D-88。Bolt 37e4）と混ぜない
        Instant now = Instant.now();
        for (int sequence : java.util.List.of(909, 910)) {
            Quotation quotation = routeRedesignQuotation(sequence);
            Instant presentedAt = quotation.presentedAt().orElseThrow().instant();
            Instant respondedAt = quotation.respondedAt().orElseThrow().instant();
            Instant confirmedAt =
                    quotation.assignedRoute().orElseThrow().confirmedAt().instant();
            Instant redesignAt =
                    quotation.assignedRoute().orElseThrow().redesignRequiredAt().instant();
            Instant departureAt = quotation
                    .assignedRoute()
                    .orElseThrow()
                    .legs()
                    .getFirst()
                    .departureAt()
                    .instant();
            Instant expiresAt = quotation.expiry().orElseThrow().expiresAt().instant();

            assertThat(presentedAt).as("提示 < 回答").isBefore(respondedAt);
            assertThat(respondedAt).as("回答 < 経路の確定").isBefore(confirmedAt);
            if (sequence == 910) {
                Instant approvedAt =
                        quotation.shipperApproval().orElseThrow().approvedAt().instant();
                assertThat(confirmedAt).as("経路の確定 < 荷主の承認").isBefore(approvedAt);
                assertThat(approvedAt).as("荷主の承認 < 再設計要").isBefore(redesignAt);
            } else {
                assertThat(confirmedAt).as("経路の確定 < 再設計要").isBefore(redesignAt);
            }
            assertThat(redesignAt).as("再設計要 < 起動").isBefore(now);
            assertThat(departureAt).as("起動 < 最初の区間の出発").isAfter(now);
            assertThat(expiresAt).as("出発 < 有効期限").isAfter(departureAt);
        }
    }

    @Test
    void 再設計要の荷主承認待ちの見本は荷主が承認できない() {
        // 行 1・2
        assertThat(routeRedesignQuotation(909).approvalRejectionAt(new UtcInstant(Instant.now())))
                .contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
    }

    @Test
    void 再設計要の荷主承認待ちの見本は予約の確定待ちに出ず経路設計中と荷主承認待ちの表に出る() {
        // 行 3
        assertThat(quotations.findAwaitingBookingSummaries())
                .extracting(AwaitingBookingSummary::number)
                .doesNotContain(number(909));
        assertThat(quotations.findRoutingRequestedSummaries())
                .extracting(RoutingRequestedSummary::number)
                .contains(number(909));
    }

    @Test
    void 再設計要の承認済みの見本は予約の確定待ちの行に再設計要と分かり本予約の確定に使えない() {
        // 行 4・5
        assertThat(quotations.findAwaitingBookingSummaries())
                .filteredOn(summary -> summary.number().equals(number(910)))
                .singleElement()
                .satisfies(
                        summary -> assertThat(summary.routeRedesignRequired()).isTrue());
        assertThat(bookableQuotations.find(
                        new BookableQuotationRequest("TR-2026-0910", 1, new UtcInstant(Instant.now()))))
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.ROUTE_REDESIGN_REQUIRED));
    }

    @Test
    void 再設計要の見本は有効期限まで再見積りできない() {
        // 行 8
        for (int sequence : java.util.List.of(909, 910)) {
            assertThat(routeRedesignQuotation(sequence).requoteRejectionAt(new UtcInstant(Instant.now())))
                    .as("TR-2026-0" + sequence)
                    .contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
        }
    }

    @Test
    void 再設計要の見本の案件は確定し直せず再設計要の記録の時刻が見積りの印と一致する() {
        // 行 7。経路設計の記録と見積りの印は別々に直接入れるので、時刻が合っていることをここで守る（DE-06 の伝達と同じ値）
        // 案件番号と業務番号の対応（RC-2026-0906 は TR-2026-0909、RC-2026-0907 は TR-2026-0910）
        java.util.Map<Integer, Integer> requestOfCase = java.util.Map.of(906, 909, 907, 910);
        for (var entry : requestOfCase.entrySet()) {
            RoutingCase routingCase = routingCases
                    .findByNumber(new RoutingCaseNumber(2026, entry.getKey()))
                    .orElseThrow();
            Quotation quotation = routeRedesignQuotation(entry.getValue());

            assertThat(routingCase.confirmationRejection())
                    .contains(RouteConfirmationRejectionReason.ROUTE_REDESIGN_REQUIRED);
            RouteVersion version = routingCase.routeVersion();
            assertThat(version.redesign().orElseThrow().requiredAt())
                    .isEqualTo(quotation.assignedRoute().orElseThrow().redesignRequiredAt());
            assertThat(version.confirmation().orElseThrow().approvedAt())
                    .isEqualTo(quotation.assignedRoute().orElseThrow().confirmedAt());
            assertThat(routingCase.quotationExpiresAt())
                    .contains(quotation.expiry().orElseThrow().expiresAt());
            // S-06 は案件の候補の区間を、C-04 は見積りの割り当てた区間の写しを示すので、2 つが一致する（ADR-014）
            assertThat(routingCase.confirmedCandidateOf(1).orElseThrow().legs())
                    .extracting(leg -> leg.departureAt(), leg -> leg.arrivalAt())
                    .containsExactlyElementsOf(quotation.assignedRoute().orElseThrow().legs().stream()
                            .map(leg -> org.assertj.core.groups.Tuple.tuple(leg.departureAt(), leg.arrivalAt()))
                            .toList());
        }
    }

    private TransportRequest routeRedesignRequest(int sequence) {
        return transportRequests.findByNumber(number(sequence), SHIPPER_A).orElseThrow();
    }

    private Quotation routeRedesignQuotation(int sequence) {
        return quotations
                .findByTransportRequestIdAndNo(routeRedesignRequest(sequence).id(), 1)
                .orElseThrow();
    }

    private TransportRequest expiredAfterApprovalRequest() {
        return transportRequests.findByNumber(number(908), SHIPPER_A).orElseThrow();
    }

    private Quotation expiredAfterApprovalQuotation(int quotationNo) {
        return quotations
                .findByTransportRequestIdAndNo(expiredAfterApprovalRequest().id(), quotationNo)
                .orElseThrow();
    }

    private static TransportRequestNumber number(int sequence) {
        return new TransportRequestNumber(2026, sequence);
    }
}
