package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 接続時間規則のリポジトリの MyBatis 実装（Bolt 17）。骨組み（ステップ 3 の Red）。
 */
@Repository
public class MyBatisConnectionRuleRepository implements ConnectionRuleRepository {

    private final RoutingReferenceMapper mapper;

    public MyBatisConnectionRuleRepository(RoutingReferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ConnectionRule> findAll() {
        return List.of();
    }
}
