package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestRejection;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送要求の審査（確定・差戻し）を受け付ける入力ポート（US-02、社内の営業担当者が使う）。トランザクションの境界になる。
 * 社内の画面は営業担当者がすべての荷主の輸送要求を扱うため、荷主企業で絞らない照会を使う。
 */
public class TransportRequestReviewService {

    private final TransportRequestRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TransportRequestReviewService(
            TransportRequestRepository repository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 審査を確定する（US-02 AC1）。判断時刻は Clock から得る。 */
    @Transactional
    public ReviewOutcome approve(ApproveTransportRequestCommand command) {
        UtcInstant decidedAt = new UtcInstant(clock.instant());
        return review(
                command.number(),
                ReviewDecision.APPROVED,
                request ->
                        request.approve(command.targetVersionNo(), command.reviewer(), command.rationale(), decidedAt));
    }

    /** 差し戻す（US-02 AC2）。判断時刻は Clock から得る。 */
    @Transactional
    public ReviewOutcome sendBack(SendBackTransportRequestCommand command) {
        UtcInstant decidedAt = new UtcInstant(clock.instant());
        return review(
                command.number(),
                ReviewDecision.SENT_BACK,
                request -> request.sendBack(
                        command.targetVersionNo(),
                        command.reviewer(),
                        command.reason(),
                        command.missingItems(),
                        decidedAt));
    }

    private ReviewOutcome review(
            TransportRequestNumber number,
            ReviewDecision decision,
            Function<TransportRequest, Optional<TransportRequestRejection>> operation) {
        Optional<TransportRequest> found = repository.findByNumberForStaff(number);
        if (found.isEmpty()) {
            return new ReviewOutcome.NotFound();
        }
        TransportRequest request = found.get();
        int reviewedVersionNo = request.currentVersion().versionNo();
        Optional<TransportRequestRejection> rejection = operation.apply(request);
        if (rejection.isPresent()) {
            return new ReviewOutcome.Rejected(rejection.get(), reviewedVersionNo);
        }
        try {
            repository.update(request);
        } catch (ConcurrentTransportRequestUpdateException _) {
            return new ReviewOutcome.Conflict();
        }
        request.domainEvents().forEach(eventPublisher::publishEvent);
        request.clearDomainEvents();
        return new ReviewOutcome.Reviewed(number, reviewedVersionNo, decision);
    }
}
