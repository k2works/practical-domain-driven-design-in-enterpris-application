package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepositoryContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * KPI 計測記録のリポジトリの契約を PostgreSQL 18 で確かめる（ADR-007）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisKpiObservationRepositoryIntegrationTest extends KpiObservationRepositoryContract {

    @Autowired
    KpiObservationRepository repository;

    @Override
    protected KpiObservationRepository repository() {
        return repository;
    }
}
