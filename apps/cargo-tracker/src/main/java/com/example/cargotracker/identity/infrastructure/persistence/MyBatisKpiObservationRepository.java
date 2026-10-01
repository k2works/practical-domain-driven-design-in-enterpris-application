package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * KPI 計測記録のリポジトリの MyBatis 実装。
 */
@Repository
public class MyBatisKpiObservationRepository implements KpiObservationRepository {

    private final KpiObservationMapper mapper;

    public MyBatisKpiObservationRepository(KpiObservationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(KpiObservation observation) {
        mapper.insertIfAbsent(new KpiObservationRow(observation.transportRequestId(), observation.shipperCompanyId().value(),
                observation.submittedAt().instant().atOffset(ZoneOffset.UTC)));
    }

    @Override
    public Optional<KpiObservation> findByTransportRequestId(UUID transportRequestId) {
        return mapper.selectByTransportRequestId(transportRequestId).map(MyBatisKpiObservationRepository::toAggregate);
    }

    @Override
    public List<KpiObservation> findAll() {
        return mapper.selectAll().stream().map(MyBatisKpiObservationRepository::toAggregate).toList();
    }

    private static KpiObservation toAggregate(KpiObservationRow row) {
        return KpiObservation.reconstitute(row.transportRequestId(), new CompanyId(row.shipperCompanyId()),
                new UtcInstant(row.submittedAt().toInstant()));
    }
}
