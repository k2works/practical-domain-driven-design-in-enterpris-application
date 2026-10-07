package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 航海のリポジトリの MyBatis 実装（Bolt 17）。骨組み（ステップ 3 の Red）。
 */
@Repository
public class MyBatisVoyageRepository implements VoyageRepository {

    private final RoutingReferenceMapper mapper;

    public MyBatisVoyageRepository(RoutingReferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Voyage> findAll() {
        return List.of();
    }
}
