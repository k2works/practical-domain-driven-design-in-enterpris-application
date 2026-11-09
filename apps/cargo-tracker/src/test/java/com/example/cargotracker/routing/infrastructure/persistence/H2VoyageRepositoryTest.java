package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepositoryContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 航海のリポジトリの契約を、ローカル起動と同じ dev プロファイルの H2 で確かめる（Bolt 39e。ADR-007）。 */
@SpringBootTest
@ActiveProfiles("dev")
class H2VoyageRepositoryTest extends VoyageRepositoryContract {

    @Autowired
    VoyageRepository voyages;

    @Override
    protected VoyageRepository voyages() {
        return voyages;
    }
}
