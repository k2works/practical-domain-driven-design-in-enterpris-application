package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepositoryContract;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    void 最初の提示時刻が提出時刻より前になる保存は表の制約で拒否される() {
        UUID transportRequestId = UUID.randomUUID();
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        repository.save(KpiObservation.recordSubmission(
                transportRequestId, "TR-2026-0001", shipper, new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"))));
        // 保存されているものより早い提出時刻を持つ写し。集約の規則は通るが、表の提出時刻の 1 秒前の提示時刻になる
        KpiObservation stale = KpiObservation.recordSubmission(
                transportRequestId, "TR-2026-0001", shipper, new UtcInstant(Instant.parse("2026-10-05T00:59:58Z")));
        stale.recordPresentation(new UtcInstant(Instant.parse("2026-10-05T00:59:59Z")));

        assertThatThrownBy(() -> repository.saveFirstPresentation(stale))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
