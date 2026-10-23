package com.example.cargotracker.routing.application.internal.eventhandlers;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteAssignments;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteRedesigns;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
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
 * 案件の行を押さえて読み（評価の保存と直列にする）、割り当てた経路版が再設計要の記録を持てば、同じトランザクションで続けて再設計要を
 * 伝える（DE-06 が DE-05 より先に届いても印のない経路を残さない。US-07 AC5。Bolt 37e2）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class QuotationRouteAssignmentEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationRouteAssignmentEventHandler.class);

    private final RoutingCaseRepository repository;
    private final QuotationRouteAssignments routeAssignments;
    private final QuotationRouteRedesigns routeRedesigns;

    public QuotationRouteAssignmentEventHandler(
            RoutingCaseRepository repository,
            QuotationRouteAssignments routeAssignments,
            QuotationRouteRedesigns routeRedesigns) {
        this.repository = repository;
        this.routeAssignments = routeAssignments;
        this.routeRedesigns = routeRedesigns;
    }

    @ApplicationModuleListener
    public void on(RouteConfirmed event) {
        RoutingCaseNumber number = RoutingCaseNumber.parse(event.caseNumber());
        Optional<RoutingCase> routingCase = repository.findByNumberForUpdate(number);
        Optional<RouteCandidate> confirmed =
                routingCase.flatMap(found -> found.confirmedCandidateOf(event.routeVersionNo()));
        if (confirmed.isEmpty()) {
            LOG.warn(
                    "DE-05 の確定した候補が見つからないため見積りに割り当てなかった: 案件 {}、経路版 {}、見積り {}",
                    event.caseNumber(),
                    event.routeVersionNo(),
                    event.quotationId());
            return;
        }
        Optional<String> notAssigned = routeAssignments.assign(
                event.quotationId(),
                number,
                event.routeVersionNo(),
                event.approvedAt(),
                confirmed.get().legs());
        if (notAssigned.isPresent()) {
            LOG.warn(
                    "DE-05 の経路版を見積りに割り当てなかった: 案件 {}、経路版 {}、見積り {}、理由 {}",
                    event.caseNumber(),
                    event.routeVersionNo(),
                    event.quotationId(),
                    notAssigned.get());
            return;
        }
        redesignOf(routingCase.orElseThrow(), event.routeVersionNo())
                .ifPresent(redesign -> notifyRedesignRequired(event, number, redesign));
    }

    /** 経路版の再設計要の記録（状態ではなく記録の有無で見る。旧版も記録を持てる。Bolt 37e2）。 */
    private static Optional<RedesignRecord> redesignOf(RoutingCase routingCase, int routeVersionNo) {
        return routingCase.routeVersions().stream()
                .filter(version -> version.routeVersionNo() == routeVersionNo)
                .findFirst()
                .flatMap(RouteVersion::redesign);
    }

    /**
     * 割り当てた経路版が再設計要なら、同じトランザクションで続けて再設計要を伝える（DE-06 が先に届いていた。届く順 B・C。Bolt 37e2）。
     * 時刻は再設計要の記録の時刻（DE-06 の時刻と同じ）。見積りの楽観ロックの競合は上げて、割当てごと戻す。
     */
    private void notifyRedesignRequired(RouteConfirmed event, RoutingCaseNumber number, RedesignRecord redesign) {
        routeRedesigns
                .notifyRedesignRequired(number, event.routeVersionNo(), redesign.requiredAt())
                .ifPresent(reason -> LOG.warn(
                        "DE-05 の経路版の再設計要を見積りに続けて伝えなかった: 案件 {}、経路版 {}、見積り {}、理由 {}",
                        event.caseNumber(),
                        event.routeVersionNo(),
                        event.quotationId(),
                        reason));
    }
}
