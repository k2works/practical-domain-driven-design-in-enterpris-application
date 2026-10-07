package com.example.cargotracker.routing.acceptance;

import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import java.util.ArrayList;
import java.util.List;

/**
 * メモリ上の接続時間規則のリポジトリ。シナリオが規則を置く。
 */
public class InMemoryConnectionRuleRepository implements ConnectionRuleRepository {

    private final List<ConnectionRule> rules = new ArrayList<>();

    @Override
    public synchronized List<ConnectionRule> findAll() {
        return List.copyOf(rules);
    }

    public synchronized void add(ConnectionRule rule) {
        rules.add(rule);
    }

    public synchronized void clear() {
        rules.clear();
    }
}
