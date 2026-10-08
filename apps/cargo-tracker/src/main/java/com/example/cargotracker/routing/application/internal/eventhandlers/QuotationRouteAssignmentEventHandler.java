package com.example.cargotracker.routing.application.internal.eventhandlers;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteAssignments;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-05 経路を確定した を経路設計の中で受け、見積りの公開 API で依頼元の見積りに経路版を割り当てる（R-INV-11、ADR-014。Bolt 20）。
 * 見積りは経路設計のイベントを購読しない（依存が循環する。Bolt 19 レビュー D-71）。確定した候補の区間は案件から読み、写しとして渡す。
 * 経路設計の確定のコミットの後に、非同期で、新しいトランザクションの中で処理する。割当ては冪等なので、再配信はそのまま呼び直す。
 * 見積りが業務の理由で割り当てなかった（置換済みなど）ときは、警告のログを残して終える（経路設計の確定は戻さない。結果整合）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class QuotationRouteAssignmentEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationRouteAssignmentEventHandler.class);

    private final RoutingCaseRepository repository;
    private final QuotationRouteAssignments routeAssignments;

    public QuotationRouteAssignmentEventHandler(
            RoutingCaseRepository repository, QuotationRouteAssignments routeAssignments) {
        this.repository = repository;
        this.routeAssignments = routeAssignments;
    }

    @ApplicationModuleListener
    public void on(RouteConfirmed event) {
        RoutingCaseNumber number = RoutingCaseNumber.parse(event.caseNumber());
        Optional<RouteCandidate> confirmed =
                repository.findByNumber(number).flatMap(routingCase -> confirmedCandidate(routingCase, event));
        if (confirmed.isEmpty()) {
            LOG.warn(
                    "DE-05 の確定した候補が見つからないため見積りに割り当てなかった: 案件 {}、経路版 {}、見積り {}",
                    event.caseNumber(),
                    event.routeVersionNo(),
                    event.quotationId());
            return;
        }
        routeAssignments
                .assign(
                        event.quotationId(),
                        number,
                        event.routeVersionNo(),
                        event.approvedAt(),
                        confirmed.get().legs())
                .ifPresent(reason -> LOG.warn(
                        "DE-05 の経路版を見積りに割り当てなかった: 案件 {}、経路版 {}、見積り {}、理由 {}",
                        event.caseNumber(),
                        event.routeVersionNo(),
                        event.quotationId(),
                        reason));
    }

    /** DE-05 の経路版の、確定の記録の候補。 */
    private static Optional<RouteCandidate> confirmedCandidate(RoutingCase routingCase, RouteConfirmed event) {
        return routingCase.routeVersions().stream()
                .filter(version -> version.routeVersionNo() == event.routeVersionNo())
                .findFirst()
                .flatMap(version -> version.confirmation()
                        .flatMap(confirmation -> version.candidates().stream()
                                .filter(candidate -> candidate.candidateNo() == confirmation.candidateNo())
                                .findFirst()));
    }
}
