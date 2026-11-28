package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseScope;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.infrastructure.persistence.MyBatisVoyageConcurrentUpdateIntegrationTest.Evaluations;
import com.example.cargotracker.routing.infrastructure.persistence.MyBatisVoyageConcurrentUpdateIntegrationTest.Repositories;
import java.time.Clock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
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

    @Autowired
    RoutingCaseRepository routingCases;

    @Autowired
    ConnectionRuleRepository connectionRules;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    Clock clock;

    @Autowired
    InfoFreshnessPolicy freshness;

    @Autowired
    RoutingCaseScope scope;

    private Repositories repositories() {
        return new Repositories(voyages, jdbc, transactionManager);
    }

    /** 確定した経路の評価の同時（Bolt 39f の行 25・26。本体は PostgreSQL のテストと共有する）。 */
    @Nested
    class Bolt39f_同時 {

        private Evaluations evaluations() {
            return new Evaluations(repositories(), routingCases, connectionRules, events, clock, freshness, scope);
        }

        @Test
        void 行25_H2でも別々の航海の2つのDE12のトランザクションが重なっても両方のコミットの後の評価で接続不足になる() throws Exception {
            MyBatisVoyageConcurrentUpdateIntegrationTest.assertBothVoyageUpdatesAreEvaluated(evaluations());
        }

        @Test
        void 行26_H2でも2つのDE30の評価が同じ案件で重なると遅れた方は読み直してDE06は1件() throws Exception {
            MyBatisVoyageConcurrentUpdateIntegrationTest.assertOverlappingEvaluationsRedesignOnce(evaluations());
        }
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

    @ParameterizedTest(name = "既存の行 {0}、先に書くのが新しい DE-12 {1}")
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    void Bolt39e_行19_行20_H2でも書きが重なっても待たされた側は読み直し公開した時刻の新しい方が残る(boolean existing, boolean winnerIsNewer)
            throws Exception {
        MyBatisVoyageConcurrentUpdateIntegrationTest.assertOverlappingWrites(repositories(), existing, winnerIsNewer);
    }

    @Test
    void Bolt39e_行45_H2でも読みの途中で更新がコミットされても例外にならない() throws Exception {
        MyBatisVoyageConcurrentUpdateIntegrationTest.assertReadsDuringWrites(repositories());
    }
}
