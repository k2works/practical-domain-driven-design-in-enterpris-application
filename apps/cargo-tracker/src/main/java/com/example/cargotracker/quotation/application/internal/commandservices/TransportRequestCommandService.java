package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.ReviseTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.RequiredDocumentStorage;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.rules.RequiredDocumentPolicy;
import com.example.cargotracker.quotation.domain.model.rules.RequiredDocumentPolicy.AcceptedAttachment;
import com.example.cargotracker.quotation.domain.model.rules.RequiredDocumentPolicy.DocumentCheck;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.RevisionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送要求のコマンドを受け付ける入力ポート。トランザクションの境界になる。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class TransportRequestCommandService {

    private final TransportRequestRepository repository;
    private final QuotationRepository quotationRepository;
    private final TransportRequestNumberIssuer numberIssuer;
    private final MvpAcceptancePolicy acceptancePolicy;
    private final RequiredDocumentPolicy documentPolicy;
    private final RequiredDocumentStorage documentStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TransportRequestCommandService(
            TransportRequestRepository repository,
            QuotationRepository quotationRepository,
            TransportRequestNumberIssuer numberIssuer,
            MvpAcceptancePolicy acceptancePolicy,
            RequiredDocumentPolicy documentPolicy,
            RequiredDocumentStorage documentStorage,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.quotationRepository = quotationRepository;
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
        DocumentCheck documents = documentPolicy.check(List.of(), command.attachments());
        return switch (command.terms().validate(submittedAt, acceptancePolicy).and(documents.violations())) {
            case ShipmentTermsInput.Invalid(SubmissionViolations violations) ->
                new SubmissionOutcome.Rejected(violations);
            case ShipmentTermsInput.Valid(ShipmentTerms terms) -> submitValid(command, terms, documents, submittedAt);
        };
    }

    /**
     * 差し戻された輸送要求を、直した輸送条件で再提出する（Q-INV-15）。自社（荷主企業）の輸送要求だけを対象にし、
     * 提出と同じ検証を通す。添付した種類の前の版の書類は差し替え、添付しなかった種類は引き継いで、受付規則を判定する
     * （Q-INV-16、D-25）。業務番号は変えず、新しい版を審査中にして DE-01 を発行する。下書きでなければ、書類を保存する前に拒否する。
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
        DocumentCheck documents =
                documentPolicy.check(request.currentVersion().terms().documents(), command.attachments());
        return switch (command.terms().validate(submittedAt, acceptancePolicy).and(documents.violations())) {
            case ShipmentTermsInput.Invalid(SubmissionViolations violations) ->
                new ResubmissionOutcome.Invalid(violations);
            case ShipmentTermsInput.Valid(ShipmentTerms terms) ->
                resubmitValid(
                        request, terms.withDocuments(storeDocuments(request.id(), documents)), command, submittedAt);
        };
    }

    /**
     * 本予約の前に、自社の輸送要求の条件を変更する（US-05 AC1）。承認を要さず、旧版を残した新しい版を審査中にして DE-01 を発行し、
     * 旧版の有効な見積りを同じトランザクションで無効にする。検証と書類の扱いは再提出と同じ。変更できない状態と古い版は、書類を保存する前に
     * 拒否する。
     *
     * <p>書き込みは見積り（有効な見積りは 1 件まで。Q-INV-18）が先。そこで競合したら何も書かずに結果で返す。その後の輸送要求の更新で
     * 競合したら {@link ConcurrentTransportRequestUpdateException} をそのまま投げ、見積りの無効化ごとロールバックする。
     */
    @Transactional
    public RevisionOutcome revise(ReviseTransportRequestCommand command) {
        Optional<TransportRequest> found = repository.findByNumber(command.number(), command.shipperCompanyId());
        if (found.isEmpty()) {
            return new RevisionOutcome.NotFound();
        }
        TransportRequest request = found.get();
        Optional<RevisionRejection> rejection = request.checkRevisable(command.baseVersionNo());
        if (rejection.isPresent()) {
            return new RevisionOutcome.Rejected(rejection.get());
        }
        UtcInstant submittedAt = new UtcInstant(clock.instant());
        DocumentCheck documents =
                documentPolicy.check(request.currentVersion().terms().documents(), command.attachments());
        return switch (command.terms().validate(submittedAt, acceptancePolicy).and(documents.violations())) {
            case ShipmentTermsInput.Invalid(SubmissionViolations violations) -> new RevisionOutcome.Invalid(violations);
            case ShipmentTermsInput.Valid(ShipmentTerms terms) ->
                reviseValid(
                        request, terms.withDocuments(storeDocuments(request.id(), documents)), command, submittedAt);
        };
    }

    private RevisionOutcome reviseValid(
            TransportRequest request,
            ShipmentTerms terms,
            ReviseTransportRequestCommand command,
            UtcInstant submittedAt) {
        Optional<RevisionRejection> rejection =
                request.revise(command.baseVersionNo(), terms, command.submittedBy(), submittedAt);
        if (rejection.isPresent()) {
            return new RevisionOutcome.Rejected(rejection.get());
        }
        try {
            voidActiveQuotations(request.id());
        } catch (ConcurrentQuotationUpdateException _) {
            return new RevisionOutcome.Conflict();
        }
        repository.update(request);
        publishEvents(request);
        return new RevisionOutcome.Revised(
                request.number(), request.currentVersion().versionNo());
    }

    private void voidActiveQuotations(TransportRequestId id) {
        for (Quotation quotation : quotationRepository.findByTransportRequestId(id)) {
            if (quotation.voidByTermsRevision()) {
                quotationRepository.update(quotation);
            }
        }
    }

    /**
     * 受付規則を通った書類から、新しい版の書類を作る。引き継ぐ書類の後ろに、受け付けた添付の中身を保存して足し、
     * 書類番号は新しい版の中で 1 から振り直す（D-25）。引き継ぐ書類のオブジェクトキーは変えない（ファイルを複製しない）。
     * ファイルを保存した後に DB の保存が失敗すると、ファイルが残る（片付けは ADR-010 のとおり運用準備（W10）で行う）。
     */
    private List<RequiredDocument> storeDocuments(TransportRequestId id, DocumentCheck documents) {
        List<RequiredDocument> stored = new ArrayList<>();
        for (RequiredDocument carried : documents.carried()) {
            stored.add(carried.withDocumentNo(stored.size() + 1));
        }
        for (AcceptedAttachment accepted : documents.accepted()) {
            String objectKey = documentStorage.store(id, accepted.attachment().content());
            stored.add(RequiredDocument.of(stored.size() + 1, accepted.attachment(), accepted.mediaType(), objectKey));
        }
        return stored;
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
            SubmitTransportRequestCommand command,
            ShipmentTerms terms,
            DocumentCheck documents,
            UtcInstant submittedAt) {
        TransportRequestId id = new TransportRequestId(UUID.randomUUID());
        ShipmentTerms withDocuments = terms.withDocuments(storeDocuments(id, documents));
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
