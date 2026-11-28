package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 接続時間規則のリポジトリの MyBatis 実装（Bolt 17）。必要最小接続時間は分で持つ。
 */
@Repository
public class MyBatisConnectionRuleRepository implements ConnectionRuleRepository {

    private final RoutingReferenceMapper mapper;

    public MyBatisConnectionRuleRepository(RoutingReferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ConnectionRule> findAll() {
        return mapper.selectConnectionRules().stream()
                .map(row -> new ConnectionRule(
                        row.id(),
                        new Location(row.portUnlocode()),
                        Duration.ofMinutes(row.minConnectionMinutes()),
                        new UtcInstant(row.validFrom().toInstant()),
                        row.validTo() == null
                                ? null
                                : new UtcInstant(row.validTo().toInstant())))
                .toList();
    }
}
