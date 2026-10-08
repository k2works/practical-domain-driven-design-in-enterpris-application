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
     * 提出の記録がない（DE-01 の購読が遅れた・失敗した）ときは、警告のログを残して例外を投げる。イベントの発行の記録が
     * 未完了のまま残り、起動のときの再配信で記録できる（ADR-014）。届く順の遅れで直る欠けなので、捨てない。
     * 提示時刻が提出時刻より前（KPI-INV-03）のときは、再配信しても直らないため、ERROR のログを残して捨てる（調査が要る。開発レビュー D-79）。
     */
    @ApplicationModuleListener
    public void on(QuotationPresented event) {
        KpiObservation observation = repository
                .findByTransportRequestId(event.transportRequestId())
                .orElseThrow(() -> notYetSubmitted(event));
        if (observation.precedesSubmission(event.presentedAt())) {
            LOG.error(
                    "DE-03 の提示時刻が提出時刻より前のため KPI 計測記録に記録せずに捨てた（再配信しても直らない。調査が要る）:" + " 輸送要求 {}、見積り {}、提出時刻 {}、提示時刻 {}",
                    event.transportRequestId(),
                    event.quotationId(),
                    observation.submittedAt().instant(),
                    event.presentedAt().instant());
            return;
        }
        if (observation.recordPresentation(event.presentedAt())) {
            repository.saveFirstPresentation(observation);
        }
    }

    private static IllegalStateException notYetSubmitted(QuotationPresented event) {
        LOG.warn(
                "DE-03 の提示時刻を KPI 計測記録に記録しなかった（提出の記録がない。DE-01 の購読の後の再配信を待つ）:" + " 輸送要求 {}、見積り {}、提示時刻 {}",
                event.transportRequestId(),
                event.quotationId(),
                event.presentedAt().instant());
        return new IllegalStateException("DE-03 を KPI 計測記録に記録できない: 提出の記録がない");
    }
}
