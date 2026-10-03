package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.RequiredDocumentStorage;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.rules.RequiredDocumentPolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
    private final RequiredDocumentPolicy documentPolicy;
    private final RequiredDocumentStorage documentStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TransportRequestCommandService(
            TransportRequestRepository repository,
            TransportRequestNumberIssuer numberIssuer,
            MvpAcceptancePolicy acceptancePolicy,
            RequiredDocumentPolicy documentPolicy,
            RequiredDocumentStorage documentStorage,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.numberIssuer = numberIssuer;
        this.acceptancePolicy = acceptancePolicy;
        this.documentPolicy = documentPolicy;
        this.documentStorage = documentStorage;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 輸送要求を提出する。輸送条件の入力と添付した書類（Q-INV-16）を検証し、不足や誤りがあれば何も保存せずに違反をまとめて返す。
     * 違反がなければ、書類の中身を保存し、提出時刻の年の業務番号を振り、保存と同じトランザクションで DE-01 を発行する。
     */
    @Transactional
    public SubmissionOutcome submit(SubmitTransportRequestCommand command) {
        UtcInstant submittedAt = new UtcInstant(clock.instant());
        return switch (validate(command.terms(), submittedAt, List.of(), command.attachments())) {
            case ShipmentTermsInput.Invalid(SubmissionViolations violations) ->
                new SubmissionOutcome.Rejected(violations);
            case ShipmentTermsInput.Valid(ShipmentTerms terms) -> submitValid(command, terms, submittedAt);
        };
    }

    /**
     * 差し戻された輸送要求を、直した輸送条件で再提出する（Q-INV-15）。自社（荷主企業）の輸送要求だけを対象にし、
     * 提出と同じ検証を通す。前の版の書類は引き継ぎ、足した書類と合わせて受付規則を判定する（Q-INV-16）。
     * 業務番号は変えず、新しい版を審査中にして DE-01 を発行する。下書きでなければ、書類を保存する前に拒否する。
     */
    @Transactional
    public ResubmissionOutcome resubmit(ResubmitTransportRequestCommand command) {
        Optional<TransportRequest> found = repository.findByNumber(command.number(), command.shipperCompanyId());
        if (found.isEmpty()) {
            return new ResubmissionOutcome.NotFound();
        }
        TransportRequest request = found.get();
        Optional<ResubmissionRejection> rejection = request.checkResubmittable();
        if (rejection.isPresent()) {
            return new ResubmissionOutcome.Rejected(rejection.get());
        }
        UtcInstant submittedAt = new UtcInstant(clock.instant());
        List<RequiredDocument> carried = request.currentVersion().terms().documents();
        return switch (validate(command.terms(), submittedAt, carried, command.attachments())) {
            case ShipmentTermsInput.Invalid(SubmissionViolations violations) ->
                new ResubmissionOutcome.Invalid(violations);
            case ShipmentTermsInput.Valid(ShipmentTerms terms) ->
                resubmitValid(
                        request,
                        terms.withDocuments(storeDocuments(request.id(), carried, command.attachments())),
                        command,
                        submittedAt);
        };
    }

    /** 輸送条件の入力の検証（Q-INV-01・02・12）に、書類の受付規則（Q-INV-16）の違反を足して、まとめて返す。 */
    private ShipmentTermsInput.Validation validate(
            ShipmentTermsInput input,
            UtcInstant submittedAt,
            List<RequiredDocument> carried,
            List<RequiredDocumentAttachment> added) {
        ShipmentTermsInput.Validation validation = input.validate(submittedAt, acceptancePolicy);
        List<Violation> documentViolations = documentPolicy.check(carried, added);
        if (documentViolations.isEmpty()) {
            return validation;
        }
        List<Violation> violations = new ArrayList<>();
        if (validation instanceof ShipmentTermsInput.Invalid(SubmissionViolations termsViolations)) {
            violations.addAll(termsViolations.violations());
        }
        violations.addAll(documentViolations);
        return new ShipmentTermsInput.Invalid(new SubmissionViolations(violations));
    }

    /**
     * 受付規則を通った書類の中身を保存し、引き継いだ書類の後ろに足す。書類番号は引き継いだ書類の続きから振る。
     * ファイルを保存した後に DB の保存が失敗すると、ファイルが残る（片付けは S3 の実装のときに決める。Bolt 7 のリスク）。
     */
    private List<RequiredDocument> storeDocuments(
            TransportRequestId id, List<RequiredDocument> carried, List<RequiredDocumentAttachment> added) {
        List<RequiredDocument> documents = new ArrayList<>(carried);
        int nextNo =
                carried.stream().mapToInt(RequiredDocument::documentNo).max().orElse(0) + 1;
        for (RequiredDocumentAttachment attachment : added) {
            DocumentMediaType mediaType = DocumentMediaType.detect(attachment.content())
                    .orElseThrow(() -> new IllegalStateException("受付規則を通っていない書類です: " + attachment));
            String objectKey = documentStorage.store(id, attachment.content());
            documents.add(RequiredDocument.of(nextNo++, attachment, mediaType, objectKey));
        }
        return documents;
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

    /** 検証を通った輸送条件で、書類を保存し、業務番号を振って提出し、DE-01 を発行する。 */
    private SubmissionOutcome submitValid(
            SubmitTransportRequestCommand command, ShipmentTerms terms, UtcInstant submittedAt) {
        TransportRequestId id = new TransportRequestId(UUID.randomUUID());
        ShipmentTerms withDocuments = terms.withDocuments(storeDocuments(id, List.of(), command.attachments()));
        TransportRequestNumber number = numberIssuer.next(TransportRequestNumber.yearOf(submittedAt));
        TransportRequest transportRequest = TransportRequest.submit(
                id, number, command.shipperCompanyId(), withDocuments, command.submittedBy(), submittedAt);
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
