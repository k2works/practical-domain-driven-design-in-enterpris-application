package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.api.RouteAssignment;
import com.example.cargotracker.quotation.api.RouteAssignmentReceipt;
import com.example.cargotracker.quotation.api.RouteAssignmentRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteAssignmentResult;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 見積りの公開 API の経路の割当ての実装（ADR-014、R-INV-11。Bolt 20）。経路設計の DE-05 の listener のトランザクションの中で、
 * 見積りの集約だけを更新し、DE-21 を発行する。輸送要求の荷主承認待ちへの変更は DE-21 を受けて別のトランザクションで行う。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class RouteAssignmentService implements RouteAssignment {

    private final QuotationRepository quotationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RouteAssignmentService(
            QuotationRepository quotationRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.quotationRepository = quotationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RouteAssignmentReceipt assign(RouteAssignmentRequest request) {
        Optional<Quotation> found = quotationRepository.findById(new QuotationId(request.quotationId()));
        if (found.isEmpty()) {
            return new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.QUOTATION_NOT_FOUND);
        }
        Quotation quotation = found.get();
        AssignedRoute route;
        try {
            route = toAssignedRoute(request);
        } catch (IllegalArgumentException _) {
            // 不正な依頼は再配信しても変わらないため、例外にして listener を戻さず、理由で返す（Bolt 20 レビュー）
            return new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.INVALID_REQUEST);
        }
        RouteAssignmentResult result = quotation.assignRoute(route, new UtcInstant(clock.instant()));
        return switch (result) {
            case ASSIGNED -> {
                // 競合（ConcurrentQuotationUpdateException）は listener のトランザクションごと戻し、DE-05 の再配信でやり直す
                quotationRepository.update(quotation);
                quotation.domainEvents().forEach(eventPublisher::publishEvent);
                quotation.clearDomainEvents();
                yield new RouteAssignmentReceipt.Assigned();
            }
            case ALREADY_ASSIGNED -> new RouteAssignmentReceipt.AlreadyAssigned();
            // 公開 API の理由は、ドメインの値の名前ではなく公開 API の定数で返す（Bolt 20 レビュー）
            case ANOTHER_ROUTE_VERSION_ASSIGNED ->
                new RouteAssignmentReceipt.NotAssigned(
                        RouteAssignmentReceipt.NotAssigned.ANOTHER_ROUTE_VERSION_ASSIGNED);
            case NOT_ROUTING_REQUESTED ->
                new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.NOT_ROUTING_REQUESTED);
            case RETIRED -> new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.RETIRED);
        };
    }

    private static AssignedRoute toAssignedRoute(RouteAssignmentRequest request) {
        return new AssignedRoute(
                request.routingCaseNumber(),
                request.routeVersionNo(),
                request.confirmedAt(),
                request.legs().stream()
                        .map(leg -> new AssignedRouteLeg(
                                leg.voyageNumber(), leg.load(), leg.discharge(), leg.departureAt(), leg.arrivalAt()))
                        .toList());
    }
}
