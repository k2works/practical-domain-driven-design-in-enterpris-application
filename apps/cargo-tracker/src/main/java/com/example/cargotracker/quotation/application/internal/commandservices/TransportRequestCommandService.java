package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送要求のコマンドを受け付ける入力ポート。トランザクションの境界になる。
 */
public class TransportRequestCommandService {

    private final TransportRequestRepository repository;
    private final TransportRequestNumberIssuer numberIssuer;
    private final MvpAcceptancePolicy acceptancePolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TransportRequestCommandService(
            TransportRequestRepository repository,
            TransportRequestNumberIssuer numberIssuer,
            MvpAcceptancePolicy acceptancePolicy,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.numberIssuer = numberIssuer;
        this.acceptancePolicy = acceptancePolicy;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 輸送要求を提出する。輸送条件の入力を検証し、不足や誤りがあれば何も保存せずに違反を返す。
     * 違反がなければ、提出時刻の年の業務番号を振り、保存と同じトランザクションで DE-01 を発行する。
     */
    @Transactional
    public SubmissionOutcome submit(SubmitTransportRequestCommand command) {
        UtcInstant submittedAt = new UtcInstant(clock.instant());
        SubmissionViolations violations = command.terms().validate(submittedAt, acceptancePolicy);
        if (!violations.isEmpty()) {
            return new SubmissionOutcome.Rejected(violations);
        }
        TransportRequestNumber number = numberIssuer.next(TransportRequestNumber.yearOf(submittedAt));
        TransportRequest transportRequest = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                number,
                command.shipperCompanyId(),
                command.terms().toTerms(),
                command.submittedBy(),
                submittedAt);
        repository.save(transportRequest);
        transportRequest.domainEvents().forEach(eventPublisher::publishEvent);
        transportRequest.clearDomainEvents();
        return new SubmissionOutcome.Submitted(transportRequest.id(), number);
    }
}
