package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.infrastructure.persistence.MyBatisVoyageConcurrentUpdateIntegrationTest.Repositories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 同じ航海の DE-12 の同時（Bolt 39e の行 19・20）と読みと書きの同時（行 45）を H2 で確かめる（本体は PostgreSQL のテストと共有する。ADR-007）。
 * クラスに {@code @Transactional} を付けない理由は {@link MyBatisVoyageConcurrentUpdateIntegrationTest} のとおり。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2VoyageConcurrentUpdateTest {

    @Autowired
    VoyageRepository voyages;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private Repositories repositories() {
        return new Repositories(voyages, jdbc, transactionManager);
    }

    @Test
    void Bolt39e_行19_H2でも既存の行で新しいDE12が先にコミットすると古いDE12は適用されない() throws Exception {
        MyBatisVoyageConcurrentUpdateIntegrationTest.assertConcurrentAdoptions(repositories(), true, true);
    }

    @Test
    void Bolt39e_行19_H2でも既存の行で古いDE12が先にコミットすると読み直して新しいDE12が適用される() throws Exception {
        MyBatisVoyageConcurrentUpdateIntegrationTest.assertConcurrentAdoptions(repositories(), true, false);
    }

    @Test
    void Bolt39e_行20_H2でも初めての航海で新しいDE12が先にコミットすると負けた古いDE12は適用されない() throws Exception {
        MyBatisVoyageConcurrentUpdateIntegrationTest.assertConcurrentAdoptions(repositories(), false, true);
    }

    @Test
    void Bolt39e_行20_H2でも初めての航海で古いDE12が先にコミットすると負けた新しいDE12が適用される() throws Exception {
        MyBatisVoyageConcurrentUpdateIntegrationTest.assertConcurrentAdoptions(repositories(), false, false);
    }

    @Test
    void Bolt39e_行45_H2でも読みの途中で更新がコミットされても例外にならない() throws Exception {
        MyBatisVoyageConcurrentUpdateIntegrationTest.assertReadsDuringWrites(repositories());
    }
}
