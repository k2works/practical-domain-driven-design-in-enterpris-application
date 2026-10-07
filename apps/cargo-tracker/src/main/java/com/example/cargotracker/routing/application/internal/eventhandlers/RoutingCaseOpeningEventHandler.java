package com.example.cargotracker.routing.application.internal.eventhandlers;

import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteConditions;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.RoutingCaseConditions;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.Location;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-16 荷主が詳細経路設計を依頼した を購読し、経路設計案件を作る（R-INV-10、US-06。Bolt 17）。
 * 経路条件は見積りの公開 API から ACL 越しに得る。見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する。
 * 配信は少なくとも 1 回なので、同じ輸送要求版の案件があれば何もしない（冪等）。同時の再配信は一意制約が止め、トランザクションを
 * 戻して例外にする（案件番号も戻る。再起動の後に再配信され、そのときは案件があるので何もしない）。
 * 経路条件が得られない（輸送要求がないか、依頼の後に再提出された）ときは、結果整合が崩れた手がかりとして警告のログを残す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class RoutingCaseOpeningEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingCaseOpeningEventHandler.class);

    private final RoutingCaseRepository repository;
    private final RoutingCaseNumberIssuer numberIssuer;
    private final QuotationRouteConditions routeConditions;

    public RoutingCaseOpeningEventHandler(
            RoutingCaseRepository repository,
            RoutingCaseNumberIssuer numberIssuer,
            QuotationRouteConditions routeConditions) {
        this.repository = repository;
        this.numberIssuer = numberIssuer;
        this.routeConditions = routeConditions;
    }

    @ApplicationModuleListener
    public void on(RouteDesignRequested event) {
        if (repository.existsForTransportRequestVersion(
                event.transportRequestId(), event.transportRequestVersionNo())) {
            return;
        }
        Optional<RoutingCaseConditions> conditions =
                routeConditions.find(event.transportRequestId(), event.transportRequestVersionNo());
        if (conditions.isEmpty()) {
            LOG.warn(
                    "DE-16 の経路条件が得られないため経路設計案件を作らなかった: 輸送要求 {} の版 {}、見積り {}",
                    event.transportRequestId(),
                    event.transportRequestVersionNo(),
                    event.quotationId());
            return;
        }
        RoutingCaseNumber number = numberIssuer.next(RoutingCaseNumber.yearOf(event.requestedAt()));
        repository.save(RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                number,
                event.transportRequestId(),
                conditions.get().transportRequestNumber(),
                event.transportRequestVersionNo(),
                event.quotationId(),
                event.routeVia().stream().map(Location::new).toList(),
                conditions.get().specification(),
                event.requestedAt()));
    }
}
