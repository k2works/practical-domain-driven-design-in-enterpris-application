package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 見積りのドメインイベントを購読して KPI 計測記録を残す。
 * 発行元のコミット後に、非同期で、新しいトランザクションの中で処理する（ADR-003）。
 * 配信は少なくとも 1 回なので、同じイベントが再び届いても記録を変えない（リポジトリの保存が冪等）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class KpiObservationEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(KpiObservationEventHandler.class);

    private final KpiObservationRepository repository;

    public KpiObservationEventHandler(KpiObservationRepository repository) {
        this.repository = repository;
    }

    /**
     * DE-01 輸送要求を提出した を受けて、KPI-01 の開始時刻を記録する。同じ輸送要求の 2 回目以降の DE-01 では記録を変えない（版 2 以降の扱いは KPI-01 の開始時刻の定義で確定する）。
     */
    @ApplicationModuleListener
    public void on(TransportRequestSubmitted event) {
        repository.save(KpiObservation.recordSubmission(
                event.transportRequestId(),
                event.transportRequestNumber(),
                event.shipperCompanyId(),
                event.submittedAt()));
    }

    /**
     * DE-03 見積りを提示した を受けて、KPI-01 の終点として最初の提示時刻を記録する（KPI-INV-01。Bolt 21）。
     * 提出の記録がない（DE-01 の処理が遅れた・失敗した）か、提示時刻が提出時刻より前なら、記録せずに警告のログを残して例外を投げる。
     * イベントの発行の記録が未完了のまま残り、起動のときの再配信で記録できる（ADR-014）。業務の拒否ではないため、捨てない。
     */
    @ApplicationModuleListener
    public void on(QuotationPresented event) {
        KpiObservation observation = repository
                .findByTransportRequestId(event.transportRequestId())
                .orElseThrow(() -> rejected("提出の記録がない", event));
        try {
            if (observation.recordPresentation(event.presentedAt())) {
                repository.saveFirstPresentation(observation);
            }
        } catch (IllegalArgumentException e) {
            throw rejected("提示時刻が提出時刻より前", event);
        }
    }

    private static IllegalStateException rejected(String reason, QuotationPresented event) {
        LOG.warn(
                "DE-03 の提示時刻を KPI 計測記録に記録しなかった（再配信を待つ）: 輸送要求 {}、見積り {}、提示時刻 {}、理由 {}",
                event.transportRequestId(),
                event.quotationId(),
                event.presentedAt(),
                reason);
        return new IllegalStateException("DE-03 を KPI 計測記録に記録できない: " + reason);
    }
}
