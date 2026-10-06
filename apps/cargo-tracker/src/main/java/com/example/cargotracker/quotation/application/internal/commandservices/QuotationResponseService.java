package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷主の見積りへの回答を受け付ける入力ポート（US-24。荷主担当者が使う）。トランザクションの境界になる。
 * 社内の {@link QuotationCommandService} と分け、照会は必ず荷主企業で絞る（Q-INV-08。Bolt 4 R-02 と同じ考え方）。
 * 1 つのトランザクションでは見積りの集約だけを更新し、輸送要求の経路設計中への変更は DE-16 を受けて別のトランザクションで行う。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationResponseService {

    private final TransportRequestRepository transportRequestRepository;
    private final QuotationRepository quotationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public QuotationResponseService(
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
     * 詳細経路設計へ進むと回答する（提示済み → 詳細設計依頼済み。US-24 AC1、Q-INV-07・09）。回答時刻は Clock から得て、
     * 保存と同じトランザクションで DE-16 を発行する。荷主に提示していない見積り（承認待ち・提示前に置き換えた見積り）は
     * 荷主に見えないため、見つからない扱いにする。
     */
    @Transactional
    public RouteDesignRequestOutcome requestRouteDesign(RequestRouteDesignCommand command) {
        Optional<Quotation> found = transportRequestRepository
                .findByNumber(command.number(), command.shipperCompanyId())
                .flatMap(request ->
                        quotationRepository.findByTransportRequestIdAndNo(request.id(), command.quotationNo()))
                .filter(quotation -> quotation.presentedAt().isPresent());
        if (found.isEmpty()) {
            return new RouteDesignRequestOutcome.NotFound();
        }
        Quotation quotation = found.get();
        Optional<QuotationRejection> rejection =
                quotation.requestRouteDesign(command.respondent(), new UtcInstant(clock.instant()));
        if (rejection.isPresent()) {
            return new RouteDesignRequestOutcome.Rejected(rejection.get());
        }
        try {
            quotationRepository.update(quotation);
        } catch (ConcurrentQuotationUpdateException _) {
            return new RouteDesignRequestOutcome.Conflict();
        }
        quotation.domainEvents().forEach(eventPublisher::publishEvent);
        quotation.clearDomainEvents();
        return new RouteDesignRequestOutcome.Requested(command.number(), command.quotationNo());
    }
}
