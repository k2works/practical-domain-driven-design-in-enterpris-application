package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 採用と採用の同時（Bolt 39d4 の行 10）を H2 で確かめる（本体は PostgreSQL のテストと共有する。ADR-007）。クラスに {@code @Transactional} を付けない理由は
 * {@link MyBatisConcurrentAdoptionIntegrationTest} のとおり。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2ConcurrentAdoptionTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    IngestionRepository ingestions;

    @Autowired
    ExternalRecordRepository externalRecords;

    @Autowired
    ReceivedRecordRepository receivedRecords;

    @Autowired
    ReceivedRecordDecisionRepository decisions;

    @Autowired
    AdoptedValueRepository adoptedValues;

    @Test
    void 行10_H2でも読んだ後に別の採用が先にコミットすると後の採用はトランザクションごと巻き戻り判断も採用値もイベントも残らず基準は先の側() {
        MyBatisConcurrentAdoptionIntegrationTest.assertLosingAdoptionRollsBack(
                new MyBatisConcurrentAdoptionIntegrationTest.Repositories(
                        jdbc,
                        transactionManager,
                        ingestions,
                        externalRecords,
                        receivedRecords,
                        decisions,
                        adoptedValues));
    }
}
