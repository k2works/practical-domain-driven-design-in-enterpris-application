package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequoteQuotationCommand;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestLookup;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestScope;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.rules.RequotePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
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
 * 再見積りだけは、旧版と新しい見積りの 2 つの見積りを 1 つのトランザクションで書く（Q-INV-18 が見積りをまたぐ規則のため。
 * ドメインモデル「見積りの失効と置換」。Bolt 11）。営業担当者が扱えるのは担当企業の見積依頼だけで、どのコマンドも最初に担当範囲を
 * 判定する（{@link StaffTransportRequestScope}。範囲の外なら何も書かずに範囲外の結果を返す。Bolt 38f の決定 (10)）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationCommandService {

    private final QuotationRepository quotationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final StaffTransportRequestScope scope;

    public QuotationCommandService(
            QuotationRepository quotationRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            StaffTransportRequestScope scope) {
        this.quotationRepository = quotationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.scope = scope;
    }

    /**
     * 見積りを作って算出する（作成中 → 承認待ち）。見積りを作れるのは、輸送要求が見積り作成中で、作成中・承認待ち・提示済みの
     * 見積りがないときだけ（Q-INV-18）。対象の版は輸送要求の現在の版。入力に違反があれば何も保存せずに違反を返す（AC3）。
     */
    @Transactional
    public CalculationOutcome calculate(CalculateQuotationCommand command, AuthenticatedActor operator) {
        TransportRequest request;
        switch (scope.find(command.number(), operator)) {
            case StaffTransportRequestLookup.Found(TransportRequest found) -> request = found;
            case StaffTransportRequestLookup.NotFound _ -> {
                return new CalculationOutcome.NotFound();
            }
            case StaffTransportRequestLookup.OutOfScope _ -> {
                return new CalculationOutcome.OutOfScope();
            }
        }
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
    public PresentationOutcome present(PresentQuotationCommand command, AuthenticatedActor operator) {
        TransportRequest request;
        switch (scope.find(command.number(), operator)) {
            case StaffTransportRequestLookup.Found(TransportRequest inScope) -> request = inScope;
            case StaffTransportRequestLookup.NotFound _ -> {
                return new PresentationOutcome.NotFound();
            }
            case StaffTransportRequestLookup.OutOfScope _ -> {
                return new PresentationOutcome.OutOfScope();
            }
        }
        Optional<Quotation> found =
                quotationRepository.findByTransportRequestIdAndNo(request.id(), command.quotationNo());
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
     * 新しい見積りを作って算出する（承認待ち）。見積依頼は見積り作成中・見積提示済み・経路設計中・荷主承認待ち・予約待ちのどれかで、
     * 旧版の対象の版が現在の版のときだけ。旧版が経路を持てば（荷主の承認の後に失効した見積りなど）、新しい見積りが経路・荷主の回答・
     * 経路方針を引き継ぐ（入力の経路方針は使わない。D-85。Bolt 35b）。
     * 旧版が置換済み・失効なら拒否し、入力に違反があれば何も保存せずに違反を返す。旧版の更新を先に書き、DB の部分一意インデックスに触れないようにする。
     */
    @Transactional
    public RequotationOutcome requote(RequoteQuotationCommand command, AuthenticatedActor operator) {
        TransportRequest request;
        switch (scope.find(command.number(), operator)) {
            case StaffTransportRequestLookup.Found(TransportRequest found) -> request = found;
            case StaffTransportRequestLookup.NotFound _ -> {
                return new RequotationOutcome.NotFound();
            }
            case StaffTransportRequestLookup.OutOfScope _ -> {
                return new RequotationOutcome.OutOfScope();
            }
        }
        List<Quotation> existing = quotationRepository.findByTransportRequestId(request.id());
        Optional<Quotation> previous = existing.stream()
                .filter(quotation -> quotation.quotationNo() == command.quotationNo())
                .findFirst();
        if (previous.isEmpty()) {
            return new RequotationOutcome.NotFound();
        }
        Quotation old = previous.get();
        UtcInstant now = new UtcInstant(clock.instant());
        // 輸送要求の状態・版と、置換済み・失効・期限内の承認済みの旧版は、入力を見る前に拒否する。判定は画面のボタン・GET と同じ照会を
        // 規則を使う（D-85。Bolt 35c）。旧版は、入力に誤りがないと分かってから置き換える
        Optional<QuotationRejection> rejection = RequotePolicy.rejection(request, old, now);
        if (rejection.isPresent()) {
            return new RequotationOutcome.Rejected(rejection.get());
        }
        int quotationNo = nextQuotationNo(existing);
        Quotation replacement = Quotation.create(
                new QuotationId(UUID.randomUUID()),
                request.id(),
                request.currentVersion().versionNo(),
                quotationNo);
        Optional<QuotationViolations> violations = replacement.calculate(command.input(), now);
        if (violations.isPresent()) {
            return new RequotationOutcome.Invalid(violations.get());
        }
        // 経路を持つ旧版（荷主の承認の後に失効した見積りなど）は、経路・荷主の回答・経路方針を新しい見積りへ引き継いで置き換わる
        // （D-85。Bolt 35b）。判定は上で通ったので拒まない
        old.requoteInto(replacement, now);
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
