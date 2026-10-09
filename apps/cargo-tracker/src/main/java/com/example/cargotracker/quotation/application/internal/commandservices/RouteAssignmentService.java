package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteAssignmentResult;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 経路の割当ての入力ポート。見積りの公開 API のアダプター（{@code interfaces.api.internal}）が、依頼を割り当てた経路に変えて委ねる
 * （2026-10-09 に公開 API を interfaces.api へ移した）。
 *
 * <p> * 見積りの公開 API の経路の割当ての実装（ADR-014、R-INV-11。Bolt 20）。経路設計の DE-05 の listener のトランザクションの中で、
 * 見積りの集約だけを更新し、DE-21 を発行する。輸送要求の荷主承認待ちへの変更は DE-21 を受けて別のトランザクションで行う。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class RouteAssignmentService {

    private final QuotationRepository quotationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RouteAssignmentService(
            QuotationRepository quotationRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.quotationRepository = quotationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 依頼元の見積りに経路版を割り当てる。
     *
     * @param quotationId 依頼元の見積り ID
     * @param route 割り当てる経路
     * @return 割当ての結果。見積りが見つからなければ空
     */
    @Transactional
    public Optional<RouteAssignmentResult> assign(UUID quotationId, AssignedRoute route) {
        Optional<Quotation> found = quotationRepository.findById(new QuotationId(quotationId));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Quotation quotation = found.get();
        RouteAssignmentResult result = quotation.assignRoute(route, new UtcInstant(clock.instant()));
        if (result == RouteAssignmentResult.ASSIGNED) {
            // 競合（ConcurrentQuotationUpdateException）は listener のトランザクションごと戻し、DE-05 の再配信でやり直す
            quotationRepository.update(quotation);
            quotation.domainEvents().forEach(eventPublisher::publishEvent);
            quotation.clearDomainEvents();
        }
        return Optional.of(result);
    }
}
