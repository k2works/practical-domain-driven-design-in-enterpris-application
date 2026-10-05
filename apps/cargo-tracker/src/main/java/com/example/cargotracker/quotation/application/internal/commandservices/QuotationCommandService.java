package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 見積りのコマンドを受け付ける入力ポート（US-03、社内の営業担当者が使う）。トランザクションの境界になる。
 * 1 つのトランザクションでは見積りの集約だけを更新し、輸送要求の状態は DE-03 を受けて別のトランザクションで変える（Bolt 10 の H1）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationCommandService {

    private final TransportRequestRepository transportRequestRepository;
    private final QuotationRepository quotationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public QuotationCommandService(
            TransportRequestRepository transportRequestRepository,
            QuotationRepository quotationRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.transportRequestRepository = transportRequestRepository;
        this.quotationRepository = quotationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 見積りを作って算出する（作成中 → 承認待ち）。見積りを作れるのは、輸送要求が見積り作成中で、作成中・承認待ち・提示済みの
     * 見積りがないときだけ（Q-INV-18）。対象の版は輸送要求の現在の版。入力に違反があれば何も保存せずに違反を返す（AC3）。
     */
    @Transactional
    public CalculationOutcome calculate(CalculateQuotationCommand command) {
        Optional<TransportRequest> found = transportRequestRepository.findByNumberForStaff(command.number());
        if (found.isEmpty()) {
            return new CalculationOutcome.NotFound();
        }
        TransportRequest request = found.get();
        if (request.status() != TransportRequestStatus.QUOTING) {
            return new CalculationOutcome.Rejected(QuotationRejection.TRANSPORT_REQUEST_NOT_QUOTING);
        }
        List<Quotation> existing = quotationRepository.findByTransportRequestId(request.id());
        if (existing.stream().anyMatch(Quotation::isActive)) {
            return new CalculationOutcome.Rejected(QuotationRejection.ALREADY_QUOTED);
        }
        int quotationNo =
                existing.stream().mapToInt(Quotation::quotationNo).max().orElse(0) + 1;
        Quotation quotation = Quotation.create(
                new QuotationId(UUID.randomUUID()),
                request.id(),
                request.currentVersion().versionNo(),
                quotationNo);
        Optional<QuotationViolations> violations =
                quotation.calculate(command.input(), new UtcInstant(clock.instant()));
        if (violations.isPresent()) {
            return new CalculationOutcome.Invalid(violations.get());
        }
        try {
            quotationRepository.save(quotation);
        } catch (DuplicateQuotationException _) {
            // 同時の算出で、ほかの見積りが先に保存された（Q-INV-18。Bolt 9・10 レビュー R-02）
            return new CalculationOutcome.Rejected(QuotationRejection.ALREADY_QUOTED);
        }
        return new CalculationOutcome.Calculated(command.number(), quotationNo);
    }

    /** 見積りを社内承認して提示する（承認待ち → 提示済み）。提示時刻は Clock から得て、保存と同じトランザクションで DE-03 を発行する。 */
    @Transactional
    public PresentationOutcome present(PresentQuotationCommand command) {
        Optional<Quotation> found = transportRequestRepository
                .findByNumberForStaff(command.number())
                .flatMap(request ->
                        quotationRepository.findByTransportRequestIdAndNo(request.id(), command.quotationNo()));
        if (found.isEmpty()) {
            return new PresentationOutcome.NotFound();
        }
        Quotation quotation = found.get();
        Optional<QuotationRejection> rejection =
                quotation.presentInternally(command.approver(), new UtcInstant(clock.instant()));
        if (rejection.isPresent()) {
            return new PresentationOutcome.Rejected(rejection.get());
        }
        try {
            quotationRepository.update(quotation);
        } catch (ConcurrentQuotationUpdateException _) {
            return new PresentationOutcome.Conflict();
        }
        quotation.domainEvents().forEach(eventPublisher::publishEvent);
        quotation.clearDomainEvents();
        return new PresentationOutcome.Presented(command.number(), command.quotationNo());
    }
}
