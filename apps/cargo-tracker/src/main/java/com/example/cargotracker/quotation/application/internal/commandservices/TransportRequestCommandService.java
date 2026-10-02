package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
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
        return switch (command.terms().validate(submittedAt, acceptancePolicy)) {
            case ShipmentTermsInput.Invalid(SubmissionViolations violations) ->
                new SubmissionOutcome.Rejected(violations);
            case ShipmentTermsInput.Valid(ShipmentTerms terms) -> submitValid(command, terms, submittedAt);
        };
    }

    /**
     * 差し戻された輸送要求を、直した輸送条件で再提出する（Q-INV-15）。自社（荷主企業）の輸送要求だけを対象にし、
     * 提出と同じ検証を通す。業務番号は変えず、新しい版を審査中にして DE-01 を発行する。
     */
    @Transactional
    public ResubmissionOutcome resubmit(ResubmitTransportRequestCommand command) {
        Optional<TransportRequest> found = repository.findByNumber(command.number(), command.shipperCompanyId());
        if (found.isEmpty()) {
            return new ResubmissionOutcome.NotFound();
        }
        UtcInstant submittedAt = new UtcInstant(clock.instant());
        return switch (command.terms().validate(submittedAt, acceptancePolicy)) {
            case ShipmentTermsInput.Invalid(SubmissionViolations violations) ->
                new ResubmissionOutcome.Invalid(violations);
            case ShipmentTermsInput.Valid(ShipmentTerms terms) ->
                resubmitValid(found.get(), terms, command, submittedAt);
        };
    }

    private ResubmissionOutcome resubmitValid(
            TransportRequest request,
            ShipmentTerms terms,
            ResubmitTransportRequestCommand command,
            UtcInstant submittedAt) {
        Optional<ResubmissionRejection> rejection = request.resubmit(terms, command.submittedBy(), submittedAt);
        if (rejection.isPresent()) {
            return new ResubmissionOutcome.Rejected(rejection.get());
        }
        try {
            repository.update(request);
        } catch (ConcurrentTransportRequestUpdateException _) {
            return new ResubmissionOutcome.Conflict();
        }
        publishEvents(request);
        return new ResubmissionOutcome.Resubmitted(
                request.number(), request.currentVersion().versionNo());
    }

    /** 検証を通った輸送条件で、業務番号を振って提出し、DE-01 を発行する。 */
    private SubmissionOutcome submitValid(
            SubmitTransportRequestCommand command, ShipmentTerms terms, UtcInstant submittedAt) {
        TransportRequestNumber number = numberIssuer.next(TransportRequestNumber.yearOf(submittedAt));
        TransportRequest transportRequest = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                number,
                command.shipperCompanyId(),
                terms,
                command.submittedBy(),
                submittedAt);
        repository.save(transportRequest);
        publishEvents(transportRequest);
        return new SubmissionOutcome.Submitted(transportRequest.id(), number);
    }

    /** 保存した集約のイベントを、保存と同じトランザクションで発行し、集約から消す。 */
    private void publishEvents(TransportRequest transportRequest) {
        transportRequest.domainEvents().forEach(eventPublisher::publishEvent);
        transportRequest.clearDomainEvents();
    }
}
