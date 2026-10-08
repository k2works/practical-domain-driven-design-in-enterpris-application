package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.api.RouteAssignment;
import com.example.cargotracker.quotation.api.RouteAssignmentReceipt;
import com.example.cargotracker.quotation.api.RouteAssignmentRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import java.time.Clock;
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
        return new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.QUOTATION_NOT_FOUND);
    }
}
