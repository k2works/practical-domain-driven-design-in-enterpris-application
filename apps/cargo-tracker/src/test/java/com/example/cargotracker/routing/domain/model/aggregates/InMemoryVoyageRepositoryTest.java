package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.acceptance.InMemoryVoyageRepository;

/** メモリの実装で航海のリポジトリの契約を確かめる（Bolt 39e）。 */
class InMemoryVoyageRepositoryTest extends VoyageRepositoryContract {

    private final InMemoryVoyageRepository voyages = new InMemoryVoyageRepository();

    @Override
    protected VoyageRepository voyages() {
        return voyages;
    }
}
