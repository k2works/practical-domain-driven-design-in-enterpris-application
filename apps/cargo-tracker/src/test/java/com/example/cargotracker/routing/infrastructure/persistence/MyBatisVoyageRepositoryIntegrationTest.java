package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepositoryContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 航海のリポジトリの MyBatis 実装を PostgreSQL 18 で確かめる（Bolt 39e）。契約を継ぐ。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisVoyageRepositoryIntegrationTest extends VoyageRepositoryContract {

    @Autowired
    VoyageRepository voyages;

    @Override
    protected VoyageRepository voyages() {
        return voyages;
    }
}
