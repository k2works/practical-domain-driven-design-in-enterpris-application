package com.example.cargotracker.routing.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentRequest;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteAssignments;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteRedesigns;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** DE-05 を受けて見積りの公開 API で経路版を割り当てる（R-INV-11、ADR-014。Bolt 20）。 */
class QuotationRouteAssignmentEventHandlerTest {

    private final List<RoutingCaseNumber> locked = new ArrayList<>();
    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository() {
        @Override
        public Optional<RoutingCase> findByNumberForRouteAssignment(RoutingCaseNumber number) {
            locked.add(number);
            return super.findByNumberForRouteAssignment(number);
        }
    };
    private final List<RouteAssignmentRequest> requests = new ArrayList<>();
    private RouteAssignmentReceipt receipt = new RouteAssignmentReceipt.Assigned();
    private final List<RouteRedesignNotificationRequest> redesignRequests = new ArrayList<>();
    private RouteRedesignNotificationReceipt redesignReceipt = new RouteRedesignNotificationReceipt.Marked(1);
    private RuntimeException redesignFailure;
    private final QuotationRouteAssignmentEventHandler handler = new QuotationRouteAssignmentEventHandler(
            repository,
            new QuotationRouteAssignments(request -> {
                requests.add(request);
                return receipt;
            }),
            new QuotationRouteRedesigns(request -> {
                redesignRequests.add(request);
                if (redesignFailure != null) {
                    throw redesignFailure;
                }
                return redesignReceipt;
            }));
    private final Logger logger = (Logger) LoggerFactory.getLogger(QuotationRouteAssignmentEventHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    void 確定した候補の区間と案件番号と経路版と確定の時刻を見積りに渡す() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());

        handler.on(confirmed.event());

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.quotationId()).isEqualTo(confirmed.routingCase().quotationId());
            assertThat(request.routingCaseNumber()).isEqualTo("RC-2026-0001");
            assertThat(request.routeVersionNo()).isEqualTo(1);
            assertThat(request.confirmedAt()).isEqualTo(confirmed.event().approvedAt());
            assertThat(request.legs()).singleElement().satisfies(leg -> {
                assertThat(leg.voyageNumber()).isEqualTo("V-EARLY");
                assertThat(leg.load().unLocode()).isEqualTo("JPTYO");
                assertThat(leg.discharge().unLocode()).isEqualTo("NLRTM");
            });
        });
        assertThat(logs.list).isEmpty();
    }

    @Test
    void すでに同じ経路版を割り当てていればログを残さない() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());
        receipt = new RouteAssignmentReceipt.AlreadyAssigned();

        handler.on(confirmed.event());

        assertThat(logs.list).isEmpty();
    }

    @Test
    void 見積りが割り当てなかったときは理由を警告のログに残す() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());
        receipt = new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.RETIRED);

        handler.on(confirmed.event());

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("RC-2026-0001").contains("RETIRED");
        });
    }

    @Test
    void 案件か確定した候補が見つからなければ見積りを呼ばず警告のログに残す() {
        RouteConfirmed event = RoutingCaseFixture.confirmed().event();

        handler.on(event);

        assertThat(requests).isEmpty();
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage())
                    .contains("RC-2026-0001")
                    .contains(event.quotationId().toString());
        });
    }

    @Test
    void 別の経路版のDE05では確定した候補が見つからない() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());
        RouteConfirmed event = confirmed.event();
        RouteConfirmed another = new RouteConfirmed(
                event.routingCaseId(),
                event.caseNumber(),
                2,
                event.quotationId(),
                event.transportRequestId(),
                event.transportRequestVersionNo(),
                UUID.randomUUID(),
                event.approvedAt(),
                event.referencedInfoVersions());

        handler.on(another);

        assertThat(requests).isEmpty();
        assertThat(logs.list).hasSize(1);
    }

    // 再設計要の続けての伝達（Bolt 37e2。状態の組み合わせの表の行 6〜12）

    private static final RedesignRecord REDESIGN =
            new RedesignRecord(at("2026-10-07T06:00:00Z"), List.of(RedesignReasonCode.DEADLINE_EXCEEDED));

    @Test
    void 案件をロックして読む() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());

        handler.on(confirmed.event());

        assertThat(locked).containsExactly(confirmed.routingCase().number());
    }

    @Test
    void 経路版が再設計要なら割り当てた後に続けて再設計要の記録の時刻で伝える() {
        // 行 6（DE-06 が先に届いていた。届く順 B）
        repository.save(RoutingCaseFixture.redesignRequired(REDESIGN));

        handler.on(RoutingCaseFixture.confirmed().event());

        assertThat(requests).hasSize(1);
        assertThat(redesignRequests)
                .containsExactly(new RouteRedesignNotificationRequest("RC-2026-0001", 1, REDESIGN.requiredAt()));
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 割当て済みで再配信されても再設計要なら続けて伝える() {
        // 行 7（届く順 C）
        repository.save(RoutingCaseFixture.redesignRequired(REDESIGN));
        receipt = new RouteAssignmentReceipt.AlreadyAssigned();
        redesignReceipt = new RouteRedesignNotificationReceipt.AlreadyMarked();

        handler.on(RoutingCaseFixture.confirmed().event());

        assertThat(redesignRequests)
                .containsExactly(new RouteRedesignNotificationRequest("RC-2026-0001", 1, REDESIGN.requiredAt()));
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 再設計要の記録を持つ旧版の経路版でも続けて伝える() {
        // 行 8（状態ではなく記録の有無で見る）
        RoutingCase redesignRequired = RoutingCaseFixture.redesignRequired(REDESIGN);
        RouteVersion v1 = redesignRequired.routeVersion();
        RouteVersion superseded = new RouteVersion(
                1,
                RouteVersionStatus.SUPERSEDED,
                v1.candidates(),
                v1.evaluatedAt().orElseThrow(),
                v1.candidatesFound(),
                v1.confirmation().orElseThrow(),
                REDESIGN);
        repository.save(
                RoutingCaseFixture.withRouteVersions(redesignRequired, List.of(superseded, RouteVersion.draft(2))));

        handler.on(RoutingCaseFixture.confirmed().event());

        assertThat(redesignRequests)
                .containsExactly(new RouteRedesignNotificationRequest("RC-2026-0001", 1, REDESIGN.requiredAt()));
    }

    @Test
    void 確定のままなら続けて伝えない() {
        // 行 9
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());

        handler.on(confirmed.event());

        assertThat(requests).hasSize(1);
        assertThat(redesignRequests).isEmpty();
    }

    @Test
    void 割り当てなかったときは続けて伝えない() {
        // 行 10
        repository.save(RoutingCaseFixture.redesignRequired(REDESIGN));
        receipt = new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.RETIRED);

        handler.on(RoutingCaseFixture.confirmed().event());

        assertThat(redesignRequests).isEmpty();
        assertThat(logs.list)
                .singleElement()
                .satisfies(log -> assertThat(log.getFormattedMessage()).contains("RETIRED"));
    }

    @Test
    void 続けての伝達で付けなかったときは警告のログに残す() {
        // 行 11
        repository.save(RoutingCaseFixture.redesignRequired(REDESIGN));
        redesignReceipt = new RouteRedesignNotificationReceipt.NotMarked(
                RouteRedesignNotificationReceipt.NotMarked.QUOTATION_NOT_FOUND);

        handler.on(RoutingCaseFixture.confirmed().event());

        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage()).contains("RC-2026-0001").contains("QUOTATION_NOT_FOUND");
        });
    }

    @Test
    void 続けての伝達の楽観ロックの競合は握りつぶさずに上げる() {
        // 行 12（上げた例外で listener のトランザクションごと割当ても戻る。戻った状態は未割当てで荷主は承認できない）
        repository.save(RoutingCaseFixture.redesignRequired(REDESIGN));
        redesignFailure = new IllegalStateException("競合");
        RouteConfirmed event = RoutingCaseFixture.confirmed().event();

        assertThatThrownBy(() -> handler.on(event)).isSameAs(redesignFailure);
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
