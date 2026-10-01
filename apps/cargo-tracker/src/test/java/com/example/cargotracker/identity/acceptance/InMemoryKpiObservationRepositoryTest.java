package com.example.cargotracker.identity.acceptance;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepositoryContract;

/**
 * メモリ上の KPI 計測記録リポジトリが、本物と同じ契約を守ることを確かめる。
 */
class InMemoryKpiObservationRepositoryTest extends KpiObservationRepositoryContract {

    private final InMemoryKpiObservationRepository repository = new InMemoryKpiObservationRepository();

    @Override
    protected KpiObservationRepository repository() {
        return repository;
    }
}
