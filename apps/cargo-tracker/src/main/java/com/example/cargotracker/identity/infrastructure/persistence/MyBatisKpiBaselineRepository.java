package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaselineRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * KPI 基準値のリポジトリの MyBatis 実装（追記専用。W9 の Bolt 44-2）。
 */
@Repository
public class MyBatisKpiBaselineRepository implements KpiBaselineRepository {

    private final KpiBaselineMapper mapper;

    public MyBatisKpiBaselineRepository(KpiBaselineMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(KpiBaseline baseline) {
        mapper.insert(new KpiBaselineRow(
                baseline.id(),
                baseline.kpi().code(),
                baseline.periodFrom(),
                baseline.periodTo(),
                baseline.valueP50(),
                baseline.valueP90().orElse(null),
                baseline.method(),
                baseline.registeredBy().value(),
                baseline.registeredAt().instant().atOffset(ZoneOffset.UTC),
                baseline.supersedes().orElse(null)));
    }

    @Override
    public Optional<KpiBaseline> findLatest(KpiKind kpi) {
        return mapper.selectLatest(kpi.code()).map(MyBatisKpiBaselineRepository::toAggregate);
    }

    private static KpiBaseline toAggregate(KpiBaselineRow row) {
        return KpiBaseline.reconstitute(
                row.id(),
                KpiKind.ofCode(row.kpi()),
                row.periodFrom(),
                row.periodTo(),
                row.valueP50(),
                row.valueP90(),
                row.method(),
                new UserId(row.registeredBy()),
                new UtcInstant(row.registeredAt().toInstant()),
                row.supersedesId());
    }
}
