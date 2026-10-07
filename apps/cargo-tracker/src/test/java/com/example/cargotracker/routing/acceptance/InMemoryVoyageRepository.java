package com.example.cargotracker.routing.acceptance;

import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import java.util.ArrayList;
import java.util.List;

/**
 * メモリ上の航海のリポジトリ。シナリオが航海を置く。
 */
public class InMemoryVoyageRepository implements VoyageRepository {

    private final List<Voyage> voyages = new ArrayList<>();

    @Override
    public synchronized List<Voyage> findAll() {
        return List.copyOf(voyages);
    }

    public synchronized void add(Voyage voyage) {
        voyages.add(voyage);
    }

    public synchronized void clear() {
        voyages.clear();
    }
}
