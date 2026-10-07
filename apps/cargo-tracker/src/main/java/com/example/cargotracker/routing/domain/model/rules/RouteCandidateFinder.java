package com.example.cargotracker.routing.domain.model.rules;

import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;

/**
 * 経路候補探索。航海の一覧から、出発地から目的地までの区間の列を列挙する（ドメインモデル）。
 * MVP は確定的な列挙とし、最適化はしない。R0.1 は直行と 1 回の積替え（区間 1〜2）まで（Bolt 17）。
 */
@DomainRule
public class RouteCandidateFinder {

    /** 区間の列を列挙する。骨組み（ステップ 2 の Red）。 */
    public List<List<Leg>> find(RouteSpecification specification, List<Voyage> voyages, UtcInstant judgedAt) {
        return List.of();
    }
}
