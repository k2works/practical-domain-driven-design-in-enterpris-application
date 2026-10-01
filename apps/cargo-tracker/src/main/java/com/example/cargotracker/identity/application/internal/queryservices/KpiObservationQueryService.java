package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * KPI 計測記録を照会する入力ポート。
 */
public class KpiObservationQueryService {

    private final KpiObservationRepository repository;

    public KpiObservationQueryService(KpiObservationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<KpiObservation> findByTransportRequestId(UUID transportRequestId) {
        return repository.findByTransportRequestId(transportRequestId);
    }

    @Transactional(readOnly = true)
    public List<KpiObservation> findAll() {
        return repository.findAll();
    }
}
