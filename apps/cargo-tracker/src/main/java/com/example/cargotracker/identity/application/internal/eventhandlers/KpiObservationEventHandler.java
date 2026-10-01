package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * 見積りのドメインイベントを購読して KPI 計測記録を残す。
 * 発行元のコミット後に、非同期で、新しいトランザクションの中で処理する（ADR-003）。
 */
public class KpiObservationEventHandler {

    private final KpiObservationRepository repository;

    public KpiObservationEventHandler(KpiObservationRepository repository) {
        this.repository = repository;
    }

    /**
     * DE-01 輸送要求を提出した を受けて、KPI-01 の開始時刻を記録する。
     */
    @ApplicationModuleListener
    public void on(TransportRequestSubmitted event) {
        repository.save(KpiObservation.recordSubmission(event.transportRequestId(), event.shipperCompanyId(),
                event.submittedAt()));
    }
}
