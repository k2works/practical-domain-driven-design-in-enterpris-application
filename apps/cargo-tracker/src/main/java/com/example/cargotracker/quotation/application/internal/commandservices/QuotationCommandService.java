package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequoteQuotationCommand;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 見積りのコマンドを受け付ける入力ポート（US-03、社内の営業担当者が使う）。トランザクションの境界になる。
 * 1 つのトランザクションでは見積りの集約だけを更新し、輸送要求の状態は DE-03 を受けて別のトランザクションで変える（Bolt 10 の H1）。
 * 再見積りだけは、旧版と新しい見積りの 2 つの見積りを 1 つのトランザクションで書く（Q-INV-18 が見積りをまたぐ規則のため。
 * ドメインモデル「見積りの失効と置換」。Bolt 11）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationCommandService {

    /** 再見積りできる見積依頼の状態（見積提示済みは DE-03 を受けた後）。 */
    private static final Set<TransportRequestStatus> REQUOTABLE =
            EnumSet.of(TransportRequestStatus.QUOTING, TransportRequestStatus.QUOTED);

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
        int quotationNo = nextQuotationNo(existing);
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

    /**
     * 見積りを再見積りする（US-03 AC5、Q-INV-07・18）。旧版を置換済み（有効期限を過ぎていれば失効）にし、次の見積り番号の
     * 新しい見積りを作って算出する（承認待ち）。見積依頼は見積り作成中か見積提示済みで、旧版の対象の版が現在の版のときだけ。
     * 旧版が置換済み・失効なら拒否し、入力に違反があれば何も保存せずに違反を返す。旧版の更新を先に書き、DB の部分一意インデックスに触れないようにする。
     */
    @Transactional
    public RequotationOutcome requote(RequoteQuotationCommand command) {
        Optional<TransportRequest> found = transportRequestRepository.findByNumberForStaff(command.number());
        if (found.isEmpty()) {
            return new RequotationOutcome.NotFound();
        }
        TransportRequest request = found.get();
        List<Quotation> existing = quotationRepository.findByTransportRequestId(request.id());
        Optional<Quotation> previous = existing.stream()
                .filter(quotation -> quotation.quotationNo() == command.quotationNo())
                .findFirst();
        if (previous.isEmpty()) {
            return new RequotationOutcome.NotFound();
        }
        Quotation old = previous.get();
        if (!REQUOTABLE.contains(request.status())) {
            return new RequotationOutcome.Rejected(QuotationRejection.TRANSPORT_REQUEST_NOT_QUOTING);
        }
        if (old.transportRequestVersionNo() != request.currentVersion().versionNo()) {
            return new RequotationOutcome.Rejected(QuotationRejection.OUTDATED_VERSION);
        }
        UtcInstant now = new UtcInstant(clock.instant());
        int quotationNo = nextQuotationNo(existing);
        Quotation replacement = Quotation.create(
                new QuotationId(UUID.randomUUID()),
                request.id(),
                request.currentVersion().versionNo(),
                quotationNo);
        // 置換済み・失効の旧版は、入力を見る前に拒否する。旧版は、入力に誤りがないと分かってから置き換える
        Optional<QuotationRejection> rejection = old.requoteRejection();
        if (rejection.isPresent()) {
            return new RequotationOutcome.Rejected(rejection.get());
        }
        Optional<QuotationViolations> violations = replacement.calculate(command.input(), now);
        if (violations.isPresent()) {
            return new RequotationOutcome.Invalid(violations.get());
        }
        old.replaceWith(replacement.id(), now);
        try {
            quotationRepository.update(old);
        } catch (ConcurrentQuotationUpdateException _) {
            return new RequotationOutcome.Conflict();
        }
        // 旧版を更新できた（行を押さえた）後は、同じ旧版の再見積りは楽観ロックで止まるため、新しい見積りの保存が
        // 一意制約に触れるのは壊れた前提として例外のまま返し、旧版の更新ごとロールバックする
        quotationRepository.save(replacement);
        return new RequotationOutcome.Calculated(command.number(), quotationNo, old.status());
    }

    /** 次の見積り番号（輸送要求の見積りの番号の最大 + 1。同時の作成は UK と部分一意インデックスで止める。R-02・R-08）。 */
    private static int nextQuotationNo(List<Quotation> existing) {
        return existing.stream().mapToInt(Quotation::quotationNo).max().orElse(0) + 1;
    }
}
