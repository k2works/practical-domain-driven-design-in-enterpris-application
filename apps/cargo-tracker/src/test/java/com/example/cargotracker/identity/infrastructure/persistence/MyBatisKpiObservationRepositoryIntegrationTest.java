package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * KPI 計測記録のリポジトリを PostgreSQL 18 で確かめる（ADR-007）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisKpiObservationRepositoryIntegrationTest {

    @Autowired
    KpiObservationRepository repository;

    @Test
    void 提出時刻の記録を保存して読み出せる() {
        UUID transportRequestId = UUID.randomUUID();
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        UtcInstant submittedAt = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));

        repository.save(KpiObservation.recordSubmission(transportRequestId, shipper, submittedAt));

        assertThat(repository.findByTransportRequestId(transportRequestId)).hasValueSatisfying(found -> {
            assertThat(found.shipperCompanyId()).isEqualTo(shipper);
            assertThat(found.submittedAt()).isEqualTo(submittedAt);
            assertThat(found.excluded()).isFalse();
        });
        assertThat(repository.findAll()).extracting(KpiObservation::transportRequestId).contains(transportRequestId);
    }
}
