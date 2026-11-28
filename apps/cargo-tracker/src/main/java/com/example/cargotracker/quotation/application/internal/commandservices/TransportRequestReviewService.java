package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestLookup;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestScope;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送要求の審査（確定・差戻し）を受け付ける入力ポート（US-02、社内の営業担当者が使う）。トランザクションの境界になる。
 * 営業担当者が審査できるのは担当企業の輸送要求だけで、最初に担当範囲を判定する（{@link StaffTransportRequestScope}。範囲を知らないまま
 * 書ける道を残さない。Bolt 38f の決定 (10)）。範囲の外なら何も書かずに {@link ReviewOutcome.OutOfScope} を返す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class TransportRequestReviewService {

    private final TransportRequestRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final StaffTransportRequestScope scope;

    public TransportRequestReviewService(
            TransportRequestRepository repository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            StaffTransportRequestScope scope) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.scope = scope;
    }

    /** 審査を確定する（US-02 AC1）。判断時刻は Clock から得る。 */
    @Transactional
    public ReviewOutcome approve(ApproveTransportRequestCommand command, AuthenticatedActor operator) {
        UtcInstant decidedAt = new UtcInstant(clock.instant());
        return review(
                command.number(),
                operator,
                ReviewDecision.APPROVED,
                request ->
                        request.approve(command.targetVersionNo(), command.reviewer(), command.rationale(), decidedAt));
    }

    /** 差し戻す（US-02 AC2）。判断時刻は Clock から得る。 */
    @Transactional
    public ReviewOutcome sendBack(SendBackTransportRequestCommand command, AuthenticatedActor operator) {
        UtcInstant decidedAt = new UtcInstant(clock.instant());
        return review(
                command.number(),
                operator,
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
            AuthenticatedActor operator,
            ReviewDecision decision,
            Function<TransportRequest, Optional<ReviewRejection>> operation) {
        TransportRequest request;
        switch (scope.find(number, operator)) {
            case StaffTransportRequestLookup.Found(TransportRequest found) -> request = found;
            case StaffTransportRequestLookup.NotFound _ -> {
                return new ReviewOutcome.NotFound();
            }
            case StaffTransportRequestLookup.OutOfScope _ -> {
                return new ReviewOutcome.OutOfScope();
            }
        }
        int reviewedVersionNo = request.currentVersion().versionNo();
        Optional<ReviewRejection> rejection = operation.apply(request);
        if (rejection.isPresent()) {
            return new ReviewOutcome.Rejected(rejection.get(), reviewedVersionNo);
        }
        try {
            repository.update(request);
        } catch (ConcurrentTransportRequestUpdateException _) {
            return new ReviewOutcome.Conflict();
        }
        publishEvents(request);
        return new ReviewOutcome.Reviewed(number, reviewedVersionNo, decision);
    }

    /** 保存した集約のイベントを、保存と同じトランザクションで発行し、集約から消す。 */
    private void publishEvents(TransportRequest request) {
        request.domainEvents().forEach(eventPublisher::publishEvent);
        request.clearDomainEvents();
    }
}
