package com.example.cargotracker.routing.acceptance;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import java.util.HashMap;
import java.util.Map;

/**
 * メモリ上の案件番号の採番。年ごとに 1 から数える。
 */
public class InMemoryRoutingCaseNumberIssuer implements RoutingCaseNumberIssuer {

    private final Map<Integer, Integer> lastNumbers = new HashMap<>();

    @Override
    public synchronized RoutingCaseNumber next(int year) {
        return new RoutingCaseNumber(year, lastNumbers.merge(year, 1, Integer::sum));
    }

    public synchronized void clear() {
        lastNumbers.clear();
    }
}
