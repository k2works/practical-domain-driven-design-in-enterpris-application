package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 経路設計案件のリポジトリの MyBatis 実装（Bolt 17）。骨組み（ステップ 3 の Red）。
 */
@Repository
public class MyBatisRoutingCaseRepository implements RoutingCaseRepository {

    private final RoutingCaseMapper mapper;
    private final Clock clock;

    public MyBatisRoutingCaseRepository(RoutingCaseMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void save(RoutingCase routingCase) {
        // 骨組み
    }

    @Override
    public void update(RoutingCase routingCase) {
        // 骨組み
    }

    @Override
    public Optional<RoutingCase> findByNumber(RoutingCaseNumber number) {
        return Optional.empty();
    }

    @Override
    public boolean existsForTransportRequestVersion(UUID transportRequestId, int transportRequestVersionNo) {
        return false;
    }

    @Override
    public List<RoutingCaseSummary> findSummaries() {
        return List.of();
    }
}
